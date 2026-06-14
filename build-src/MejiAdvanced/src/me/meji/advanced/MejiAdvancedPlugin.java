package me.meji.advanced;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class MejiAdvancedPlugin extends JavaPlugin implements Listener {
    private static final String[] SIDEBAR_ENTRIES = {
        ChatColor.BLACK.toString(),
        ChatColor.DARK_BLUE.toString(),
        ChatColor.DARK_GREEN.toString(),
        ChatColor.DARK_AQUA.toString(),
        ChatColor.DARK_RED.toString(),
        ChatColor.DARK_PURPLE.toString(),
        ChatColor.GOLD.toString(),
        ChatColor.GRAY.toString(),
        ChatColor.DARK_GRAY.toString(),
        ChatColor.BLUE.toString(),
        ChatColor.GREEN.toString(),
        ChatColor.AQUA.toString()
    };
    private final Map<UUID, Long> sessionStarted = new HashMap<>();
    private int taskId = -1;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            setupPlayer(player);
        }
        taskId = Bukkit.getScheduler().runTaskTimer(this, this::refreshAll, 20L, 60L).getTaskId();
        getLogger().info("MejiAdvanced enabled: scoreboard, nametags, join/death/kill effects active.");
    }

    @Override
    public void onDisable() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            player.setPlayerListName(player.getName());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        setupPlayer(player);
        event.setJoinMessage(color("&8[&a+&8] &b" + player.getName() + " &7entered &dMeji &7with style. &8| &fSelamat datang, jangan jadi Steve."));
        player.sendTitle(color("&d&lMEJI"), color("&fWelcome, &b" + player.getName() + " &8/ &fSelamat bermain"), 10, 55, 15);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.35f);
        Bukkit.getScheduler().runTaskLater(this, this::refreshAll, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.setQuitMessage(color("&8[&c-&8] &7" + event.getPlayer().getName() + " left Meji. Progress saved, pride optional."));
        sessionStarted.remove(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTaskLater(this, this::refreshAll, 5L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        String reason = deathReason(victim);
        String keep = isKeepInventory(victim) ? " &8| &eKeepInventory on, still folded." : "";

        if (killer != null && killer != victim) {
            int damage = weaponDamage(killer.getInventory().getItemInMainHand());
            event.setDeathMessage(color("&8[&4MEJI&8] &c" + victim.getName() + " &7got humbled by &a" + killer.getName()
                + " &8(&c" + damage + " DMG&8)&7. &fDuel lesson delivered." + keep));
            killer.sendTitle(color("&a&lCLEAN HIT"), color("&7" + victim.getName() + " learned the hard way"), 5, 35, 10);
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.55f);
        } else {
            event.setDeathMessage(color("&8[&4MEJI&8] &c" + victim.getName() + " &7died: &f" + reason + keep));
        }

        String roast = randomDeathLine(victim, killer);
        event.deathScreenMessageOverride(net.kyori.adventure.text.Component.text(ChatColor.stripColor(color(roast))));
        victim.sendTitle(color("&4&lGAME OVER"), color(roast), 5, 60, 20);
        victim.playSound(victim.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.65f);
        Bukkit.getScheduler().runTaskLater(this, this::refreshAll, 5L);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String rank = armorPoints(player) >= 16 ? "&9Sentinel" : weaponDamage(player.getInventory().getItemInMainHand()) >= 8 ? "&cHunter" : "&aSurvivor";
        event.setFormat(color("&8[&dMeji&8] " + rank + " &8| &f%1$s &8» &7%2$s"));
    }

    private void setupPlayer(Player player) {
        sessionStarted.putIfAbsent(player.getUniqueId(), System.currentTimeMillis());
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        player.setScoreboard(board);
        refreshBoard(player);
    }

    private void refreshAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!sessionStarted.containsKey(viewer.getUniqueId())) {
                setupPlayer(viewer);
            }
            refreshBoard(viewer);
        }
    }

    private void refreshBoard(Player viewer) {
        Scoreboard board = viewer.getScoreboard();
        if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            viewer.setScoreboard(board);
        }
        Objective sidebar = board.getObjective("mejiSide");
        if (sidebar == null) {
            sidebar = board.registerNewObjective("mejiSide", "dummy", color("&d&lMEJI &8| &fSURVIVAL"));
            sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
            int score = SIDEBAR_ENTRIES.length;
            for (String entry : SIDEBAR_ENTRIES) {
                sidebar.getScore(entry).setScore(score--);
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("&8&m--------------");
        lines.add("&fName  &8» &b" + viewer.getName());
        lines.add("&fPing  &8» &a" + viewer.getPing() + "ms");
        lines.add("&fTPS   &8» " + tpsColor() + formatTps());
        lines.add("&fDeath &8» &c" + viewer.getStatistic(Statistic.DEATHS));
        lines.add("&fOnline&8» &e" + Bukkit.getOnlinePlayers().size() + "&7/&e" + Bukkit.getMaxPlayers());
        lines.add("&fDay   &8» &6" + worldDay(viewer));
        lines.add("&fPlayed&8» &d" + formatTicks(viewer.getStatistic(Statistic.PLAY_ONE_MINUTE)));
        lines.add("&fHere  &8» &b" + formatMillis(System.currentTimeMillis() - sessionStarted.getOrDefault(viewer.getUniqueId(), System.currentTimeMillis())));
        lines.add("&fHP    &8» &c" + health(viewer) + "&7/&c" + maxHealth(viewer) + " &6" + viewer.getFoodLevel());
        lines.add("&fArmor &8» &9" + armorPoints(viewer) + " &fDMG &8» &4" + weaponDamage(viewer.getInventory().getItemInMainHand()));
        lines.add("&8&m--------------&r");

        for (int i = 0; i < SIDEBAR_ENTRIES.length; i++) {
            Team team = board.getTeam("line" + i);
            if (team == null) {
                team = board.registerNewTeam("line" + i);
            }
            String entry = SIDEBAR_ENTRIES[i];
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
            team.setPrefix(color(lines.get(i)));
        }

        refreshViewerTeams(board);
        viewer.setPlayerListName(color("&dMeji &8| &f" + viewer.getName() + " &c" + health(viewer) + "HP &9" + armorPoints(viewer) + "AR"));
    }

    private void refreshViewerTeams(Scoreboard board) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            String name = "p" + target.getUniqueId().toString().replace("-", "").substring(0, 12);
            Team team = board.getTeam(name);
            if (team == null) {
                team = board.registerNewTeam(name);
            }
            if (!team.hasEntry(target.getName())) {
                team.addEntry(target.getName());
            }
            team.setPrefix(color("&7"));
            team.setSuffix(color(" &8[&c" + health(target) + "HP &9" + armorPoints(target) + "AR&8]"));
        }
    }

    private int health(Player player) {
        return Math.max(0, (int) Math.ceil(player.getHealth()));
    }

    private int maxHealth(Player player) {
        if (player.getAttribute(Attribute.MAX_HEALTH) == null) {
            return 20;
        }
        return (int) Math.ceil(player.getAttribute(Attribute.MAX_HEALTH).getValue());
    }

    private int armorPoints(Player player) {
        int armor = 0;
        PlayerInventory inv = player.getInventory();
        armor += armorValue(inv.getHelmet());
        armor += armorValue(inv.getChestplate());
        armor += armorValue(inv.getLeggings());
        armor += armorValue(inv.getBoots());
        return armor;
    }

    private int armorValue(ItemStack item) {
        if (item == null) return 0;
        String type = item.getType().name();
        int value;
        if (type.endsWith("_HELMET")) value = switchMaterial(type, 1, 2, 2, 2, 3, 3);
        else if (type.endsWith("_CHESTPLATE")) value = switchMaterial(type, 3, 5, 6, 6, 8, 8);
        else if (type.endsWith("_LEGGINGS")) value = switchMaterial(type, 2, 4, 5, 5, 6, 6);
        else if (type.endsWith("_BOOTS")) value = switchMaterial(type, 1, 1, 2, 2, 3, 3);
        else value = 0;
        int protection = item.getEnchantmentLevel(Enchantment.PROTECTION);
        return value + Math.min(4, protection);
    }

    private int switchMaterial(String type, int leather, int chain, int iron, int gold, int diamond, int netherite) {
        if (type.startsWith("LEATHER")) return leather;
        if (type.startsWith("CHAINMAIL")) return chain;
        if (type.startsWith("IRON")) return iron;
        if (type.startsWith("GOLDEN")) return gold;
        if (type.startsWith("DIAMOND")) return diamond;
        if (type.startsWith("NETHERITE")) return netherite;
        if (type.startsWith("TURTLE")) return 2;
        return 0;
    }

    private int weaponDamage(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 1;
        String type = item.getType().name();
        int damage = 1;
        if (type.endsWith("_SWORD")) damage = switchTool(type, 4, 5, 6, 4, 7, 8);
        else if (type.endsWith("_AXE")) damage = switchTool(type, 7, 9, 9, 7, 9, 10);
        else if (type.equals("TRIDENT")) damage = 9;
        else if (type.equals("MACE")) damage = 6;
        damage += item.getEnchantmentLevel(Enchantment.SHARPNESS);
        damage += item.getEnchantmentLevel(Enchantment.SMITE);
        damage += item.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS);
        return damage;
    }

    private int switchTool(String type, int wood, int stone, int iron, int gold, int diamond, int netherite) {
        if (type.startsWith("WOODEN")) return wood;
        if (type.startsWith("STONE")) return stone;
        if (type.startsWith("IRON")) return iron;
        if (type.startsWith("GOLDEN")) return gold;
        if (type.startsWith("DIAMOND")) return diamond;
        if (type.startsWith("NETHERITE")) return netherite;
        return 1;
    }

    private boolean isKeepInventory(Player player) {
        Boolean value = player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY);
        return value != null && value;
    }

    private String deathReason(Player player) {
        EntityDamageSnapshot snapshot = EntityDamageSnapshot.from(player);
        return snapshot.message();
    }

    private String randomDeathLine(Player victim, Player killer) {
        int deaths = victim.getStatistic(Statistic.DEATHS) + 1;
        if (killer != null && killer != victim) {
            return color("&c" + victim.getName() + "&7, that was not a fight. Itu training dummy moment.");
        }
        String[] lines = {
            "&eKeep inventory is on and you still lost the argument.",
            "&cYour game is not over, your confidence is. Bisa main apa engga?",
            "&6Meji recorded that death in 4K. Jangan diulang ya.",
            "&bSmall mistake, huge comedy. Respawn and behave.",
            "&dDeath #" + deaths + " unlocked: professional embarrassment."
        };
        return lines[Math.floorMod(victim.getName().hashCode() + deaths, lines.length)];
    }

    private int worldDay(Player player) {
        return (int) (player.getWorld().getFullTime() / 24000L);
    }

    private String formatTicks(int ticks) {
        return formatMillis(ticks * 50L);
    }

    private String formatMillis(long millis) {
        long totalSeconds = Math.max(0, millis / 1000L);
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private String formatTps() {
        double tps = Bukkit.getServer().getTPS()[0];
        return String.format(Locale.US, "%.1f", Math.min(20.0, tps));
    }

    private String tpsColor() {
        double tps = Bukkit.getServer().getTPS()[0];
        if (tps >= 19.0) return "&a";
        if (tps >= 17.0) return "&e";
        return "&c";
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private record EntityDamageSnapshot(String message) {
        static EntityDamageSnapshot from(Player player) {
            if (player.getLastDamageCause() == null) {
                return new EntityDamageSnapshot("unknown skill issue / penyebab misterius");
            }
            return switch (player.getLastDamageCause().getCause()) {
                case FALL -> new EntityDamageSnapshot("gravity won, again / gravitasi menang lagi");
                case LAVA -> new EntityDamageSnapshot("swam in spicy water / berenang di lava");
                case FIRE, FIRE_TICK -> new EntityDamageSnapshot("became toast / jadi roti panggang");
                case DROWNING -> new EntityDamageSnapshot("forgot lungs exist / lupa cara napas");
                case VOID -> new EntityDamageSnapshot("fell out of the assignment / keluar dari map kehidupan");
                case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> new EntityDamageSnapshot("lost close combat / kalah jarak dekat");
                case PROJECTILE -> new EntityDamageSnapshot("got aim-checked / kena tes aim");
                case STARVATION -> new EntityDamageSnapshot("ignored food like a genius / lupa makan");
                case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> new EntityDamageSnapshot("experienced rapid unscheduled mining / ledakan edukatif");
                default -> new EntityDamageSnapshot("skill issue detected / kemampuan dipertanyakan");
            };
        }
    }
}
