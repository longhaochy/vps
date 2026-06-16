package me.meji.claims;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class MejiClaimsPlugin extends JavaPlugin implements Listener, TabExecutor {
    private static final Set<String> GLOBAL_TRUSTED = Set.of("tanpoint", ".electedrun19", "electedrun19", ".zakk9857", "zakk9857");
    private static final Set<Material> PROTECTED_INTERACTS = Set.of(
        Material.CHEST,
        Material.TRAPPED_CHEST,
        Material.BARREL,
        Material.FURNACE,
        Material.BLAST_FURNACE,
        Material.SMOKER,
        Material.HOPPER,
        Material.DISPENSER,
        Material.DROPPER,
        Material.BREWING_STAND,
        Material.SHULKER_BOX,
        Material.WHITE_SHULKER_BOX,
        Material.ORANGE_SHULKER_BOX,
        Material.MAGENTA_SHULKER_BOX,
        Material.LIGHT_BLUE_SHULKER_BOX,
        Material.YELLOW_SHULKER_BOX,
        Material.LIME_SHULKER_BOX,
        Material.PINK_SHULKER_BOX,
        Material.GRAY_SHULKER_BOX,
        Material.LIGHT_GRAY_SHULKER_BOX,
        Material.CYAN_SHULKER_BOX,
        Material.PURPLE_SHULKER_BOX,
        Material.BLUE_SHULKER_BOX,
        Material.BROWN_SHULKER_BOX,
        Material.GREEN_SHULKER_BOX,
        Material.RED_SHULKER_BOX,
        Material.BLACK_SHULKER_BOX,
        Material.IRON_DOOR,
        Material.OAK_DOOR,
        Material.SPRUCE_DOOR,
        Material.BIRCH_DOOR,
        Material.JUNGLE_DOOR,
        Material.ACACIA_DOOR,
        Material.DARK_OAK_DOOR,
        Material.MANGROVE_DOOR,
        Material.CHERRY_DOOR,
        Material.BAMBOO_DOOR,
        Material.CRIMSON_DOOR,
        Material.WARPED_DOOR,
        Material.IRON_TRAPDOOR,
        Material.OAK_TRAPDOOR,
        Material.SPRUCE_TRAPDOOR,
        Material.BIRCH_TRAPDOOR,
        Material.JUNGLE_TRAPDOOR,
        Material.ACACIA_TRAPDOOR,
        Material.DARK_OAK_TRAPDOOR,
        Material.MANGROVE_TRAPDOOR,
        Material.CHERRY_TRAPDOOR,
        Material.BAMBOO_TRAPDOOR,
        Material.CRIMSON_TRAPDOOR,
        Material.WARPED_TRAPDOOR,
        Material.LEVER,
        Material.STONE_BUTTON,
        Material.OAK_BUTTON,
        Material.SPRUCE_BUTTON,
        Material.BIRCH_BUTTON,
        Material.JUNGLE_BUTTON,
        Material.ACACIA_BUTTON,
        Material.DARK_OAK_BUTTON,
        Material.MANGROVE_BUTTON,
        Material.CHERRY_BUTTON,
        Material.BAMBOO_BUTTON,
        Material.CRIMSON_BUTTON,
        Material.WARPED_BUTTON
    );

    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, Long> outlineUntil = new HashMap<>();
    private final List<Claim> claims = new ArrayList<>();
    private NamespacedKey claimToolKey;
    private File claimsFile;
    private int outlineTaskId = -1;

    @Override
    public void onEnable() {
        claimToolKey = new NamespacedKey(this, "claim_tool");
        claimsFile = new File("plugins/MejiClaims/claims.yml");
        loadClaims();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("kit") != null) {
            getCommand("kit").setExecutor(this);
            getCommand("kit").setTabCompleter(this);
        }
        outlineTaskId = Bukkit.getScheduler().runTaskTimer(this, this::tickOutlines, 20L, 20L).getTaskId();
        getLogger().info("MejiClaims enabled: golden shovel claim protection active.");
    }

    @Override
    public void onDisable() {
        if (outlineTaskId != -1) {
            Bukkit.getScheduler().cancelTask(outlineTaskId);
        }
        saveClaims();
        selections.clear();
        outlineUntil.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("kit")) {
            return false;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("claim")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use /kit claim.");
                return true;
            }
            giveClaimTool(player);
            player.sendMessage(color("&8[&aClaim&8] &fGolden shovel diterima. Klik 2 sudut area untuk claim."));
            return true;
        }
        sender.sendMessage(color("&8[&aClaim&8] &7Use: &f/kit claim"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("kit") && args.length == 1) {
            return List.of("claim");
        }
        return List.of();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSelect(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null || !isClaimTool(event.getItem())) {
            return;
        }
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selection.first = block.getLocation();
            player.sendMessage(color("&8[&aClaim&8] &7Sudut 1: &f" + formatBlock(block)));
        } else {
            selection.second = block.getLocation();
            player.sendMessage(color("&8[&aClaim&8] &7Sudut 2: &f" + formatBlock(block)));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.5f);
        if (selection.ready()) {
            createClaim(player, selection);
            selections.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            deny(event.getPlayer(), "Area ini sudah di-claim.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            deny(event.getPlayer(), "Tidak bisa build di claim orang.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (isClaimTool(event.getItem())) {
            return;
        }
        Material type = event.getClickedBlock().getType();
        if (PROTECTED_INTERACTS.contains(type) && !canBuild(event.getPlayer(), event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
            deny(event.getPlayer(), "Container/pintu ini dilindungi claim.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> claimAt(block.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> claimAt(block.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player) && claimAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Claim claim = claimAt(event.getEntity().getLocation());
        if (claim == null || event.getEntity() instanceof Player) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player player && claim.isTrusted(player.getName())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        Claim claim = claimAt(event.getEntity().getLocation());
        if (claim == null) {
            return;
        }
        if (event instanceof HangingBreakByEntityEvent byEntity && byEntity.getRemover() instanceof Player player && claim.isTrusted(player.getName())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (!canBuild(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
            deny(event.getPlayer(), "Armor stand ini dilindungi claim.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Claim claim = claimAt(event.getVehicle().getLocation());
        if (claim == null) {
            return;
        }
        if (event.getAttacker() instanceof Player player && claim.isTrusted(player.getName())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Claim claim = claimAt(event.getVehicle().getLocation());
        if (claim == null) {
            return;
        }
        if (event.getAttacker() instanceof Player player && claim.isTrusted(player.getName())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (claimAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (claimAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() == null && claimAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        Claim from = claimAt(event.getBlock().getLocation());
        Claim to = claimAt(event.getToBlock().getLocation());
        if (to != null && from != to) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> claimAt(block.getLocation()) != null || claimAt(block.getRelative(event.getDirection()).getLocation()) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> claimAt(block.getLocation()) != null || claimAt(block.getRelative(event.getDirection()).getLocation()) != null)) {
            event.setCancelled(true);
        }
    }

    private void giveClaimTool(Player player) {
        ItemStack tool = new ItemStack(Material.GOLDEN_SHOVEL);
        ItemMeta meta = tool.getItemMeta();
        meta.setDisplayName(color("&6&lClaim Shovel &8| &aMeji"));
        meta.setLore(List.of(
            color("&7Klik kiri: sudut pertama"),
            color("&7Klik kanan: sudut kedua"),
            color("&fArea di tengah akan terlindungi otomatis.")
        ));
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(claimToolKey, PersistentDataType.BYTE, (byte) 1);
        tool.setItemMeta(meta);
        player.getInventory().addItem(tool);
    }

    private boolean isClaimTool(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_SHOVEL || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(claimToolKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void createClaim(Player player, Selection selection) {
        if (!selection.first.getWorld().equals(selection.second.getWorld())) {
            player.sendMessage(color("&8[&cClaim&8] &fDua sudut harus di world yang sama."));
            return;
        }
        Claim claim = Claim.from(player.getName(), selection.first, selection.second);
        if (claim.area() < 9) {
            player.sendMessage(color("&8[&cClaim&8] &fArea terlalu kecil. Minimal 3x3."));
            return;
        }
        for (Claim existing : claims) {
            if (existing.overlaps(claim)) {
                player.sendMessage(color("&8[&cClaim&8] &fArea tabrakan dengan claim lain."));
                return;
            }
        }
        claims.add(claim);
        saveClaims();
        outlineUntil.put(player.getUniqueId(), System.currentTimeMillis() + 12000L);
        showClaimOutline(player, claim, true);
        player.sendMessage(color("&8[&aClaim&8] &fClaim dibuat: &a" + claim.width() + "x" + claim.depth()
            + " &7blok. Trusted: &ftanpoint, .ElectedRun19, .Zakk9857"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.25f);
    }

    private void tickOutlines() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean holdingTool = isClaimTool(player.getInventory().getItemInMainHand());
            boolean recentlyClaimed = outlineUntil.getOrDefault(player.getUniqueId(), 0L) > now;
            if (!holdingTool && !recentlyClaimed) {
                continue;
            }
            for (Claim claim : claims) {
                World world = Bukkit.getWorld(claim.world());
                if (world == null || !world.equals(player.getWorld())) {
                    continue;
                }
                Location center = claim.center(world);
                if (center.distanceSquared(player.getLocation()) <= 96.0D * 96.0D) {
                    showClaimOutline(player, claim, false);
                }
            }
        }
        outlineUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private void showClaimOutline(Player player, Claim claim, boolean burst) {
        World world = Bukkit.getWorld(claim.world());
        if (world == null || !world.equals(player.getWorld())) {
            return;
        }
        int y = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 2, player.getLocation().getBlockY() + 1));
        double minX = claim.minX() + 0.06D;
        double maxX = claim.maxX() + 0.94D;
        double minZ = claim.minZ() + 0.06D;
        double maxZ = claim.maxZ() + 0.94D;
        double step = burst ? 0.75D : 1.25D;

        for (double x = minX; x <= maxX + 0.01D; x += step) {
            sendClaimParticle(player, x, y, minZ);
            sendClaimParticle(player, x, y, maxZ);
        }
        for (double z = minZ; z <= maxZ + 0.01D; z += step) {
            sendClaimParticle(player, minX, y, z);
            sendClaimParticle(player, maxX, y, z);
        }
        sendCornerPillar(player, minX, y, minZ);
        sendCornerPillar(player, minX, y, maxZ);
        sendCornerPillar(player, maxX, y, minZ);
        sendCornerPillar(player, maxX, y, maxZ);
    }

    private void sendCornerPillar(Player player, double x, int y, double z) {
        for (int dy = 0; dy <= 3; dy++) {
            player.spawnParticle(Particle.END_ROD, x, y + dy, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void sendClaimParticle(Player player, double x, int y, double z) {
        player.spawnParticle(Particle.HAPPY_VILLAGER, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private boolean canBuild(Player player, Location location) {
        Claim claim = claimAt(location);
        return claim == null || claim.isTrusted(player.getName());
    }

    private Claim claimAt(Location location) {
        for (Claim claim : claims) {
            if (claim.contains(location)) {
                return claim;
            }
        }
        return null;
    }

    private void deny(Player player, String message) {
        player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.stripColor(color("&c" + message))));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.6f);
    }

    private void loadClaims() {
        claims.clear();
        if (!claimsFile.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(claimsFile);
        ConfigurationSection section = config.getConfigurationSection("claims");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item == null) {
                continue;
            }
            Claim claim = Claim.fromConfig(item);
            if (claim != null) {
                claims.add(claim);
            }
        }
    }

    private void saveClaims() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        YamlConfiguration config = new YamlConfiguration();
        for (int i = 0; i < claims.size(); i++) {
            claims.get(i).save(config.createSection("claims." + i));
        }
        try {
            config.save(claimsFile);
        } catch (IOException error) {
            getLogger().warning("Failed to save claims: " + error.getMessage());
        }
    }

    private String formatBlock(Block block) {
        return block.getWorld().getName() + " " + block.getX() + " " + block.getY() + " " + block.getZ();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static String norm(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static final class Selection {
        private Location first;
        private Location second;

        private boolean ready() {
            return first != null && second != null;
        }
    }

    private record Claim(String owner, String world, int minX, int maxX, int minZ, int maxZ, Set<String> trusted) {
        private static Claim from(String owner, Location first, Location second) {
            Set<String> trust = new HashSet<>(GLOBAL_TRUSTED);
            trust.add(norm(owner));
            return new Claim(owner, first.getWorld().getName(),
                Math.min(first.getBlockX(), second.getBlockX()),
                Math.max(first.getBlockX(), second.getBlockX()),
                Math.min(first.getBlockZ(), second.getBlockZ()),
                Math.max(first.getBlockZ(), second.getBlockZ()),
                trust);
        }

        private static Claim fromConfig(ConfigurationSection section) {
            String owner = section.getString("owner", "");
            String world = section.getString("world", "");
            if (owner.isBlank() || world.isBlank()) {
                return null;
            }
            Set<String> trusted = new HashSet<>(GLOBAL_TRUSTED);
            for (String name : section.getStringList("trusted")) {
                trusted.add(norm(name));
            }
            trusted.add(norm(owner));
            return new Claim(owner, world,
                section.getInt("min-x"),
                section.getInt("max-x"),
                section.getInt("min-z"),
                section.getInt("max-z"),
                trusted);
        }

        private boolean contains(Location location) {
            World locationWorld = location.getWorld();
            return locationWorld != null
                && world.equals(locationWorld.getName())
                && location.getBlockX() >= minX
                && location.getBlockX() <= maxX
                && location.getBlockZ() >= minZ
                && location.getBlockZ() <= maxZ;
        }

        private boolean overlaps(Claim other) {
            return world.equals(other.world)
                && minX <= other.maxX
                && maxX >= other.minX
                && minZ <= other.maxZ
                && maxZ >= other.minZ;
        }

        private boolean isTrusted(String playerName) {
            return trusted.contains(norm(playerName));
        }

        private int width() {
            return maxX - minX + 1;
        }

        private int depth() {
            return maxZ - minZ + 1;
        }

        private int area() {
            return width() * depth();
        }

        private Location center(World loadedWorld) {
            return new Location(loadedWorld, (minX + maxX + 1) / 2.0D, loadedWorld.getHighestBlockYAt((minX + maxX) / 2, (minZ + maxZ) / 2), (minZ + maxZ + 1) / 2.0D);
        }

        private void save(ConfigurationSection section) {
            section.set("owner", owner);
            section.set("world", world);
            section.set("min-x", minX);
            section.set("max-x", maxX);
            section.set("min-z", minZ);
            section.set("max-z", maxZ);
            section.set("trusted", new ArrayList<>(trusted));
        }
    }
}
