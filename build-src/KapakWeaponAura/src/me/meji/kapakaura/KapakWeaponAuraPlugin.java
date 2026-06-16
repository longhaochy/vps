package me.meji.kapakaura;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class KapakWeaponAuraPlugin extends JavaPlugin implements Listener {
    private static final double AURA_RADIUS = 24.0D;
    private static final long RED_SKY_TIME = 12500L;
    private static final long AURA_DURATION_MS = 1000L;
    private final Set<UUID> affectedViewers = new HashSet<>();
    private final Map<UUID, Long> activeUntil = new HashMap<>();
    private final Random random = new Random();
    private NamespacedKey kapakKey;
    private int taskId = -1;

    @Override
    public void onEnable() {
        kapakKey = new NamespacedKey("kapakweapon", "kapak_weapon");
        Bukkit.getPluginManager().registerEvents(this, this);
        taskId = Bukkit.getScheduler().runTaskTimer(this, this::tickAura, 0L, 5L).getTaskId();
        getLogger().info("KapakWeaponAuraPulse enabled: red sky, harmless lightning, and blue fire pulse for 1 second.");
    }

    @Override
    public void onDisable() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetViewer(player);
        }
        affectedViewers.clear();
        activeUntil.clear();
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> {
            if (isKapak(player.getInventory().getItemInMainHand())) {
                activeUntil.put(player.getUniqueId(), System.currentTimeMillis() + AURA_DURATION_MS);
            } else {
                activeUntil.remove(player.getUniqueId());
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activeUntil.remove(event.getPlayer().getUniqueId());
        affectedViewers.remove(event.getPlayer().getUniqueId());
    }

    private void tickAura() {
        Set<UUID> shouldAffect = new HashSet<>();
        long now = System.currentTimeMillis();

        for (Player holder : Bukkit.getOnlinePlayers()) {
            long until = activeUntil.getOrDefault(holder.getUniqueId(), 0L);
            if (until <= now || !isKapak(holder.getInventory().getItemInMainHand())) {
                activeUntil.remove(holder.getUniqueId());
                continue;
            }
            playHolderAura(holder);
            for (Player viewer : holder.getWorld().getPlayers()) {
                if (viewer.getLocation().distanceSquared(holder.getLocation()) <= AURA_RADIUS * AURA_RADIUS) {
                    shouldAffect.add(viewer.getUniqueId());
                    applyRedSky(viewer);
                }
            }
        }

        for (UUID uuid : new HashSet<>(affectedViewers)) {
            if (!shouldAffect.contains(uuid)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    resetViewer(player);
                }
                affectedViewers.remove(uuid);
            }
        }
        affectedViewers.addAll(shouldAffect);
    }

    private void applyRedSky(Player viewer) {
        viewer.setPlayerTime(RED_SKY_TIME, false);
        viewer.setPlayerWeather(WeatherType.DOWNFALL);
    }

    private void resetViewer(Player viewer) {
        viewer.resetPlayerTime();
        viewer.resetPlayerWeather();
    }

    private void playHolderAura(Player holder) {
        Location base = holder.getLocation();

        holder.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, base.clone().add(0.0D, 0.15D, 0.0D), 95, 2.2D, 0.35D, 2.2D, 0.04D);
        holder.getWorld().spawnParticle(Particle.SCULK_SOUL, base.clone().add(0.0D, 0.9D, 0.0D), 65, 1.7D, 0.9D, 1.7D, 0.03D);
        holder.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, base.clone().add(0.0D, 1.0D, 0.0D), 90, 2.3D, 1.3D, 2.3D, 0.08D);
        holder.getWorld().spawnParticle(Particle.LARGE_SMOKE, base.clone().add(0.0D, 0.8D, 0.0D), 45, 1.7D, 0.8D, 1.7D, 0.06D);

        for (int i = 0; i < 5; i++) {
            Location bolt = base.clone().add(randomOffset(7.0D), 0.0D, randomOffset(7.0D));
            bolt.setY(holder.getWorld().getHighestBlockYAt(bolt) + 1.0D);
            holder.getWorld().strikeLightningEffect(bolt);
        }

        holder.getWorld().playSound(base, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.7f);
        holder.getWorld().playSound(base, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.9f, 0.55f);
        holder.getWorld().playSound(base, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.5f);
    }

    private double randomOffset(double radius) {
        return (random.nextDouble() * radius * 2.0D) - radius;
    }

    private boolean isKapak(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(kapakKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }
}
