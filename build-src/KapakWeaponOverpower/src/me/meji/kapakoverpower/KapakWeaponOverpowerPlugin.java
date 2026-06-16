package me.meji.kapakoverpower;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public final class KapakWeaponOverpowerPlugin extends JavaPlugin implements Listener {
    private static final double LAUNCH_POWER = 4.8D;
    private static final double LAUNCH_UP = 1.75D;
    private NamespacedKey kapakKey;

    @Override
    public void onEnable() {
        kapakKey = new NamespacedKey("kapakweapon", "kapak_weapon");
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("KapakWeaponOverpower enabled: extreme knockback, lightning, heal, speed, and kill notices active.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKapakImpact(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (!isKapak(player.getInventory().getItemInMainHand())) {
            return;
        }

        healFull(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2, true, true, true));
        playImpact(player, target);
        throwTarget(player, target);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKapakKill(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        if (killer == null || !isKapak(killer.getInventory().getItemInMainHand())) {
            return;
        }

        event.setDeathMessage(color("&4&l☠ &c" + victim.getName()
            + " &7dihancurkan oleh &6&lKAPAK DEWA JESUS MAHA KUASA &7di tangan &c"
            + killer.getName() + "&4&l ☠"));
        killer.sendTitle(color("&4&lKAPAK DEWA"), color("&6Jesus Maha Kuasa &8| &c" + victim.getName() + " tumbang"), 5, 45, 15);
        victim.sendTitle(color("&4&lTERBANTING"), color("&7Dihakimi oleh &6Kapak Dewa Jesus Maha Kuasa"), 5, 55, 20);
        Bukkit.broadcastMessage(color("&8[&4MEJI&8] &cLangit terbelah. &6Kapak Dewa Jesus Maha Kuasa &cmenghabisi &f"
            + victim.getName() + " &coleh &f" + killer.getName() + "&c."));
    }

    private void throwTarget(Player player, LivingEntity target) {
        Vector direction = target.getLocation().toVector().subtract(player.getLocation().toVector());
        if (direction.lengthSquared() < 0.01D) {
            direction = player.getLocation().getDirection();
        }
        direction.setY(0.0D).normalize().multiply(LAUNCH_POWER).setY(LAUNCH_UP);
        Vector finalDirection = direction;
        Bukkit.getScheduler().runTask(this, () -> {
            if (!target.isDead()) {
                target.setVelocity(finalDirection);
            }
        });
    }

    private void playImpact(Player player, LivingEntity target) {
        Location center = target.getLocation().add(0.0D, Math.max(0.8D, target.getHeight() * 0.55D), 0.0D);
        World world = target.getWorld();

        world.strikeLightningEffect(center);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 4, 0.9D, 0.7D, 0.9D, 0.0D);
        world.spawnParticle(Particle.EXPLOSION, center, 38, 2.4D, 1.5D, 2.4D, 0.08D);
        world.spawnParticle(Particle.SONIC_BOOM, center, 4, 0.8D, 0.6D, 0.8D, 0.0D);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 180, 2.5D, 1.8D, 2.5D, 0.12D);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 130, 2.1D, 1.4D, 2.1D, 0.12D);
        world.spawnParticle(Particle.SCULK_SOUL, center, 95, 1.8D, 1.2D, 1.8D, 0.08D);
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 150, 2.2D, 1.5D, 2.2D, 0.16D);
        world.spawnParticle(Particle.WITCH, center, 80, 1.8D, 1.3D, 1.8D, 0.1D);

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.55f);
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.65f);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.8f, 0.55f);
        world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.3f, 1.25f);
        world.playSound(center, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.8f, 0.55f);
        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.4f, 0.45f);

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (target.isDead() || tick > 18) {
                    cancel();
                    return;
                }
                Location base = target.getLocation().add(0.0D, 0.4D, 0.0D);
                double radius = 1.3D + tick * 0.18D;
                for (int i = 0; i < 36; i++) {
                    double angle = (i * Math.PI / 18.0D) + tick * 0.45D;
                    Location point = base.clone().add(Math.cos(angle) * radius, tick * 0.13D, Math.sin(angle) * radius);
                    world.spawnParticle(Particle.SOUL, point, 1, 0.03D, 0.03D, 0.03D, 0.0D);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.05D, 0.05D, 0.05D, 0.03D);
                    world.spawnParticle(Particle.REVERSE_PORTAL, point, 1, 0.04D, 0.04D, 0.04D, 0.01D);
                }
                if (tick % 4 == 0) {
                    world.playSound(base, Sound.ENTITY_WARDEN_TENDRIL_CLICKS, 1.1f, 0.5f);
                }
                tick++;
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void healFull(Player player) {
        if (player.getAttribute(Attribute.MAX_HEALTH) == null) {
            player.setHealth(20.0D);
            return;
        }
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
    }

    private boolean isKapak(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(kapakKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
