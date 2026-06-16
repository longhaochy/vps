package me.meji.camera;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class MejiCameraPlugin extends JavaPlugin implements Listener, TabExecutor {
    private final Map<UUID, CameraState> cameraStates = new HashMap<>();

    @Override
    public void onEnable() {
        if (getCommand("camera") != null) {
            getCommand("camera").setExecutor(this);
            getCommand("camera").setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("MejiCamera enabled: /camera on and /camera off are ready.");
    }

    @Override
    public void onDisable() {
        for (UUID uuid : new ArrayList<>(cameraStates.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                disableCamera(player, false);
            }
        }
        cameraStates.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command ini hanya bisa dipakai player.");
            return true;
        }
        if (!player.hasPermission("mejicamera.use")) {
            player.sendMessage(ChatColor.RED + "Kamu tidak punya izin memakai /camera.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(ChatColor.YELLOW + "Pakai: /camera on atau /camera off");
            return true;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        if ("on".equals(mode)) {
            enableCamera(player);
            return true;
        }
        if ("off".equals(mode)) {
            disableCamera(player, true);
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Pakai: /camera on atau /camera off");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : List.of("on", "off")) {
            if (option.startsWith(prefix)) {
                matches.add(option);
            }
        }
        return matches;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cameraStates.remove(event.getPlayer().getUniqueId());
    }

    private void enableCamera(Player player) {
        UUID uuid = player.getUniqueId();
        if (cameraStates.containsKey(uuid)) {
            player.sendMessage(ChatColor.AQUA + "Camera sudah aktif. Pakai /camera off untuk kembali.");
            return;
        }

        cameraStates.put(uuid, new CameraState(
            player.getLocation().clone(),
            player.getGameMode(),
            player.getAllowFlight(),
            player.isFlying(),
            player.getFlySpeed()
        ));

        player.setSpectatorTarget(null);
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.sendMessage(ChatColor.AQUA + "Camera aktif. Terbang bebas untuk melihat jauh, lalu /camera off untuk kembali.");
    }

    private void disableCamera(Player player, boolean showMessage) {
        CameraState state = cameraStates.remove(player.getUniqueId());
        if (state == null) {
            if (showMessage) {
                player.sendMessage(ChatColor.YELLOW + "Camera belum aktif.");
            }
            return;
        }

        player.setSpectatorTarget(null);
        player.teleport(state.location());
        player.setGameMode(state.gameMode());

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.setAllowFlight(state.allowFlight());
            player.setFlying(state.flying() && state.allowFlight());
            player.setFlySpeed(state.flySpeed());
            if (showMessage) {
                player.sendMessage(ChatColor.GREEN + "Camera nonaktif. Kamu sudah kembali ke posisi sebelumnya.");
            }
        }, 1L);
    }

    private record CameraState(Location location, GameMode gameMode, boolean allowFlight, boolean flying, float flySpeed) {
    }
}
