package me.meji.topbar;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class MejiTopBarPlugin extends JavaPlugin implements Listener {
    private static final String SERVER = "play.meji.my.id";
    private static final String[] SLOGANS = {
        "SURVIVE",
        "BUILD",
        "RAID",
        "EXPLORE",
        "NO MERCY",
        "STAY ALIVE"
    };
    private static final BarColor[] COLORS = {
        BarColor.PURPLE,
        BarColor.BLUE,
        BarColor.GREEN,
        BarColor.YELLOW,
        BarColor.RED,
        BarColor.WHITE
    };

    private final Map<UUID, BossBar> bars = new HashMap<>();
    private int taskId = -1;
    private int frame;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            addBar(player);
        }
        taskId = Bukkit.getScheduler().runTaskTimer(this, this::tickBars, 0L, 5L).getTaskId();
        getLogger().info("MejiTopBar enabled: animated top bossbar active.");
    }

    @Override
    public void onDisable() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        addBar(event.getPlayer());
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.45f, 1.4f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BossBar bar = bars.remove(event.getPlayer().getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    private void addBar(Player player) {
        BossBar old = bars.remove(player.getUniqueId());
        if (old != null) {
            old.removeAll();
        }
        BossBar bar = Bukkit.createBossBar(titleFor(player), BarColor.PURPLE, BarStyle.SEGMENTED_6);
        bar.setProgress(1.0D);
        bar.addPlayer(player);
        bars.put(player.getUniqueId(), bar);
    }

    private void tickBars() {
        frame++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            BossBar bar = bars.get(player.getUniqueId());
            if (bar == null) {
                addBar(player);
                continue;
            }
            bar.setTitle(titleFor(player));
            bar.setColor(COLORS[Math.floorMod(frame / 16, COLORS.length)]);
            bar.setProgress(progressForFrame());
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        }
    }

    private String titleFor(Player player) {
        String slogan = SLOGANS[Math.floorMod(frame / 24, SLOGANS.length)];
        return glitchIcon() + " " + gradient("MEJI", "#ff2bd6", "#28d7ff")
            + color(" &8• ") + gradient(SERVER, "#ffffff", "#58ff9a")
            + color(" &8• ") + gradient(slogan, "#ffd84d", "#ff4d6d");
    }

    private String glitchIcon() {
        String[] frames = {
            color("&d&l◆"),
            color("&b&l◇"),
            color("&f&l✦"),
            color("&c&l◆"),
            color("&d&l◇"),
            color("&b&l✦")
        };
        return frames[Math.floorMod(frame / 4, frames.length)];
    }

    private double progressForFrame() {
        double wave = (Math.sin(frame / 8.0D) + 1.0D) / 2.0D;
        return 0.35D + (wave * 0.65D);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private String gradient(String text, String startHex, String endHex) {
        int[] start = rgb(startHex);
        int[] end = rgb(endHex);
        StringBuilder out = new StringBuilder();
        int denominator = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            double mix = i / (double) denominator;
            int red = (int) Math.round(start[0] + ((end[0] - start[0]) * mix));
            int green = (int) Math.round(start[1] + ((end[1] - start[1]) * mix));
            int blue = (int) Math.round(start[2] + ((end[2] - start[2]) * mix));
            out.append(hexColor(red, green, blue)).append(ChatColor.BOLD).append(text.charAt(i));
        }
        return out.toString();
    }

    private int[] rgb(String hex) {
        return new int[] {
            Integer.parseInt(hex.substring(1, 3), 16),
            Integer.parseInt(hex.substring(3, 5), 16),
            Integer.parseInt(hex.substring(5, 7), 16)
        };
    }

    private String hexColor(int red, int green, int blue) {
        String hex = String.format("%02x%02x%02x", red, green, blue);
        StringBuilder out = new StringBuilder("§x");
        for (int i = 0; i < hex.length(); i++) {
            out.append('§').append(hex.charAt(i));
        }
        return out.toString();
    }
}
