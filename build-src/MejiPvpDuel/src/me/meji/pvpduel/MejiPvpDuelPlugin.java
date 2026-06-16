package me.meji.pvpduel;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class MejiPvpDuelPlugin extends JavaPlugin implements Listener, TabExecutor {
    private static final String ARENA_WORLD = "flat";
    private static final long REQUEST_TTL_MILLIS = 60_000L;
    private static final int ARENA_MIN_DISTANCE = 450;
    private static final int ARENA_MAX_DISTANCE = 3_500;
    private final Map<UUID, DuelRequest> requestsByTarget = new HashMap<>();
    private final Set<UUID> pvpDisabled = new HashSet<>();
    private File stateFile;

    @Override
    public void onEnable() {
        stateFile = new File(getDataFolder(), "pvp.yml");
        loadState();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("pvp") != null) {
            getCommand("pvp").setExecutor(this);
            getCommand("pvp").setTabCompleter(this);
        }
        if (getCommand("pvpaccept") != null) {
            getCommand("pvpaccept").setExecutor(this);
            getCommand("pvpaccept").setTabCompleter(this);
        }
        ensureArenaWorld();
        Bukkit.getScheduler().runTaskTimer(this, this::cleanExpiredRequests, 20L * 15L, 20L * 15L);
        getLogger().info("MejiPvpDuel enabled: /pvp <player> and /pvpaccept active.");
    }

    @Override
    public void onDisable() {
        saveState();
        requestsByTarget.clear();
        pvpDisabled.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("pvp")) {
            return handlePvp(sender, args);
        }
        if (command.getName().equalsIgnoreCase("pvpaccept")) {
            return handleAccept(sender);
        }
        return false;
    }

    private boolean handlePvp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player challenger)) {
            sender.sendMessage("Only players can use /pvp.");
            return true;
        }
        if (args.length != 1) {
            challenger.sendMessage(color("&8[&cPvP&8] &7Use: &f/pvp <player>&7, &f/pvp on&7, &f/pvp off"));
            return true;
        }
        if (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off")) {
            return handleToggle(challenger, args[0].equalsIgnoreCase("on"));
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            target = findPlayerLoose(args[0]);
        }
        if (target == null || !target.isOnline()) {
            challenger.sendMessage(color("&8[&cPvP&8] &fPlayer tidak online."));
            return true;
        }
        if (target.getUniqueId().equals(challenger.getUniqueId())) {
            challenger.sendMessage(color("&8[&cPvP&8] &fTidak bisa duel diri sendiri."));
            return true;
        }
        long expiresAt = System.currentTimeMillis() + REQUEST_TTL_MILLIS;
        requestsByTarget.put(target.getUniqueId(), new DuelRequest(challenger.getUniqueId(), target.getUniqueId(), expiresAt));
        challenger.sendMessage(color("&8[&aPvP&8] &fRequest duel dikirim ke &a" + target.getName() + "&f. Berlaku 60 detik."));
        target.sendMessage(color("&8[&cPvP&8] &a" + challenger.getName() + " &fmenantang kamu duel."));
        target.sendMessage(color("&8[&cPvP&8] &fKetik &a/pvpaccept &funtuk teleport ke arena flat."));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.25f);
        return true;
    }

    private boolean handleToggle(Player player, boolean enabled) {
        if (enabled) {
            pvpDisabled.remove(player.getUniqueId());
            player.sendMessage(color("&8[&cPvP&8] &aPvP ON &7- kamu bisa hit dan bisa di-hit."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.45f);
        } else {
            pvpDisabled.add(player.getUniqueId());
            player.sendMessage(color("&8[&cPvP&8] &cPvP OFF &7- kamu tidak bisa hit dan tidak bisa di-hit."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.75f);
        }
        saveState();
        return true;
    }

    private boolean handleAccept(CommandSender sender) {
        if (!(sender instanceof Player target)) {
            sender.sendMessage("Only players can use /pvpaccept.");
            return true;
        }
        DuelRequest request = requestsByTarget.remove(target.getUniqueId());
        if (request == null || request.isExpired()) {
            target.sendMessage(color("&8[&cPvP&8] &fTidak ada request duel aktif."));
            return true;
        }
        Player challenger = Bukkit.getPlayer(request.challengerId());
        if (challenger == null || !challenger.isOnline()) {
            target.sendMessage(color("&8[&cPvP&8] &fPlayer yang menantang sudah offline."));
            return true;
        }
        startDuel(challenger, target);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = damagingPlayer(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (isPvpOff(victim) || isPvpOff(attacker)) {
            event.setCancelled(true);
            if (isPvpOff(attacker)) {
                attacker.sendActionBar(net.kyori.adventure.text.Component.text(color("&cPvP kamu OFF. Ketik /pvp on.")));
            } else {
                attacker.sendActionBar(net.kyori.adventure.text.Component.text(color("&c" + victim.getName() + " sedang PvP OFF.")));
            }
        }
    }

    private void startDuel(Player first, Player second) {
        World world = ensureArenaWorld();
        if (world == null) {
            first.sendMessage(color("&8[&cPvP&8] &fWorld flat gagal dibuat/dibuka."));
            second.sendMessage(color("&8[&cPvP&8] &fWorld flat gagal dibuat/dibuka."));
            return;
        }
        ArenaSpawn spawn = randomArenaSpawn(world);
        prepareSpawnArea(world, spawn.centerX(), spawn.centerZ());

        Location firstSpawn = safeLocation(world, spawn.centerX() - 5, spawn.centerZ());
        Location secondSpawn = safeLocation(world, spawn.centerX() + 5, spawn.centerZ());
        secondSpawn.setYaw(90.0f);
        firstSpawn.setYaw(-90.0f);

        equipDuelKit(first);
        equipDuelKit(second);
        first.teleport(firstSpawn);
        second.teleport(secondSpawn);
        first.setGameMode(GameMode.SURVIVAL);
        second.setGameMode(GameMode.SURVIVAL);
        first.setHealth(Math.min(maxHealth(first), 20.0D));
        second.setHealth(Math.min(maxHealth(second), 20.0D));
        first.setFoodLevel(20);
        second.setFoodLevel(20);
        first.sendTitle(color("&c&lPVP"), color("&fLawan: &a" + second.getName()), 5, 40, 10);
        second.sendTitle(color("&c&lPVP"), color("&fLawan: &a" + first.getName()), 5, 40, 10);
        Bukkit.broadcastMessage(color("&8[&cPvP&8] &a" + first.getName() + " &fvs &a" + second.getName() + " &7mulai di world flat."));
    }

    private Player damagingPlayer(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean isPvpOff(Player player) {
        return pvpDisabled.contains(player.getUniqueId());
    }

    private World ensureArenaWorld() {
        World world = Bukkit.getWorld(ARENA_WORLD);
        if (world != null) {
            return world;
        }
        WorldCreator creator = new WorldCreator(ARENA_WORLD);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.FLAT);
        creator.generatorSettings("{\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:dirt\",\"height\":2},{\"block\":\"minecraft:grass_block\",\"height\":1}],\"biome\":\"minecraft:plains\",\"structures\":{}}");
        creator.generateStructures(false);
        World created = creator.createWorld();
        if (created != null) {
            created.setPVP(true);
            created.setStorm(false);
            created.setThundering(false);
            created.setTime(1_000L);
        }
        return created;
    }

    private ArenaSpawn randomArenaSpawn(World world) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int distance = random.nextInt(ARENA_MIN_DISTANCE, ARENA_MAX_DISTANCE + 1);
        int x = random.nextBoolean() ? distance : -distance;
        int z = random.nextInt(ARENA_MIN_DISTANCE, ARENA_MAX_DISTANCE + 1) * (random.nextBoolean() ? 1 : -1);
        x = (x / 32) * 32;
        z = (z / 32) * 32;
        world.getChunkAt(x >> 4, z >> 4).load(true);
        return new ArenaSpawn(x, z);
    }

    private Location safeLocation(World world, int x, int z) {
        int y = Math.max(world.getHighestBlockYAt(x, z) + 1, 5);
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    private void prepareSpawnArea(World world, int centerX, int centerZ) {
        for (int x = centerX - 8; x <= centerX + 8; x++) {
            for (int z = centerZ - 8; z <= centerZ + 8; z++) {
                int y = Math.max(world.getHighestBlockYAt(x, z), 4);
                Block floor = world.getBlockAt(x, y, z);
                if (floor.getType().isAir()) {
                    floor.setType(Material.GRASS_BLOCK, false);
                }
                world.getBlockAt(x, y + 1, z).setType(Material.AIR, false);
                world.getBlockAt(x, y + 2, z).setType(Material.AIR, false);
            }
        }
    }

    private void equipDuelKit(Player player) {
        PlayerInventory inventory = player.getInventory();
        addOrDrop(player, new ItemStack(Material.RED_BED, 1));
        addOrDrop(player, new ItemStack(Material.COBBLESTONE, 64));
        addOrDrop(player, new ItemStack(Material.COBBLESTONE, 64));
        addOrDrop(player, new ItemStack(Material.WATER_BUCKET, 1));
        inventory.setHeldItemSlot(0);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.1f);
    }

    private double maxHealth(Player player) {
        org.bukkit.attribute.AttributeInstance attribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return attribute == null ? 20.0D : attribute.getValue();
    }

    private void addOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack stack : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
    }

    private Player findPlayerLoose(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).equals(normalized)
                || ChatColor.stripColor(player.getName()).toLowerCase(Locale.ROOT).equals(normalized)
                || player.getName().toLowerCase(Locale.ROOT).contains(normalized)) {
                return player;
            }
        }
        return null;
    }

    private void cleanExpiredRequests() {
        requestsByTarget.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private void loadState() {
        pvpDisabled.clear();
        if (stateFile == null || !stateFile.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(stateFile);
        for (String value : config.getStringList("pvp-off")) {
            try {
                pvpDisabled.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveState() {
        if (stateFile == null) {
            return;
        }
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        YamlConfiguration config = new YamlConfiguration();
        config.set("pvp-off", pvpDisabled.stream().map(UUID::toString).sorted().toList());
        try {
            config.save(stateFile);
        } catch (IOException error) {
            getLogger().warning("Failed to save PvP toggle state: " + error.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("pvp") && args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
            List<String> options = new java.util.ArrayList<>();
            for (String option : List.of("on", "off")) {
                if (option.startsWith(prefix)) {
                    options.add(option);
                }
            }
            options.addAll(playerNames);
            return options;
        }
        return List.of();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private record DuelRequest(UUID challengerId, UUID targetId, long expiresAt) {
        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private record ArenaSpawn(int centerX, int centerZ) {
    }
}
