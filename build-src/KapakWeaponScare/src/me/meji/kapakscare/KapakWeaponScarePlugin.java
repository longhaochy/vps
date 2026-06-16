package me.meji.kapakscare;

import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class KapakWeaponScarePlugin extends JavaPlugin implements Listener {
    private NamespacedKey kapakKey;

    @Override
    public void onEnable() {
        kapakKey = new NamespacedKey("kapakweapon", "kapak_weapon");
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("KapakWeaponScare enabled: horror effects active.");
    }

    @EventHandler
    public void onGetKapak(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().trim().toLowerCase(Locale.ROOT);
        if (!command.equals("/get kapak")) {
            return;
        }
        Player player = event.getPlayer();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (hasKapak(player)) {
                playSummonRitual(player);
            }
        }, 3L);
    }

    @EventHandler
    public void onKapakHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (!isKapak(player.getInventory().getItemInMainHand())) {
            return;
        }
        playHitNightmare(player, target);
    }

    private void playSummonRitual(Player player) {
        Location center = player.getLocation().add(0.0, 1.0, 0.0);
        World world = player.getWorld();
        world.playSound(center, Sound.AMBIENT_CAVE, 1.3f, 0.55f);
        world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 1.6f, 0.45f);
        world.playSound(center, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.0f, 0.7f);
        world.spawnParticle(Particle.ELDER_GUARDIAN, center, 1, 0.0, 0.0, 0.0, 0.0);

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline() || tick > 22) {
                    cancel();
                    return;
                }
                Location base = player.getLocation();
                double radius = 0.7 + (tick * 0.035);
                for (int i = 0; i < 18; i++) {
                    double angle = (tick * 0.45) + (i * Math.PI / 9.0);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location point = base.clone().add(x, 0.15 + (i * 0.075), z);
                    world.spawnParticle(Particle.SOUL, point, 1, 0.01, 0.01, 0.01, 0.0);
                    world.spawnParticle(Particle.SCULK_SOUL, point, 1, 0.01, 0.01, 0.01, 0.0);
                }
                if (tick % 6 == 0) {
                    world.playSound(base, Sound.ENTITY_WARDEN_TENDRIL_CLICKS, 1.2f, 0.6f);
                }
                tick++;
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void playHitNightmare(Player player, LivingEntity target) {
        Location center = target.getLocation().add(0.0, target.getHeight() * 0.55, 0.0);
        World world = target.getWorld();
        world.playSound(center, Sound.ENTITY_WARDEN_ROAR, 1.5f, 0.75f);
        world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 0.85f, 1.55f);
        world.playSound(center, Sound.ENTITY_ENDERMAN_SCREAM, 1.1f, 0.65f);
        world.spawnParticle(Particle.SONIC_BOOM, center, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 45, 0.65, 0.55, 0.65, 0.04);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 32, 0.55, 0.45, 0.55, 0.04);

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline() || target.isDead() || tick > 12) {
                    cancel();
                    return;
                }
                Location base = target.getLocation().add(0.0, 0.2, 0.0);
                double radius = 0.45 + tick * 0.08;
                for (int i = 0; i < 24; i++) {
                    double angle = (i * Math.PI / 12.0) + tick * 0.35;
                    Location point = base.clone().add(Math.cos(angle) * radius, tick * 0.11, Math.sin(angle) * radius);
                    world.spawnParticle(Particle.REVERSE_PORTAL, point, 1, 0.02, 0.02, 0.02, 0.0);
                    world.spawnParticle(Particle.WITCH, point, 1, 0.02, 0.02, 0.02, 0.0);
                }
                tick++;
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private boolean hasKapak(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isKapak(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isKapak(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(kapakKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }
}
