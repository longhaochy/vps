package me.meji.kapakwarning;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class KapakWeaponWarningPlugin extends JavaPlugin implements Listener {
    private static final double WARNING_RADIUS = 18.0D;
    private static final long COOLDOWN_MS = 5000L;
    private final Map<UUID, Long> lastWarning = new HashMap<>();
    private NamespacedKey kapakKey;

    @Override
    public void onEnable() {
        kapakKey = new NamespacedKey("kapakweapon", "kapak_weapon");
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("KapakWeaponWarning enabled: nearby warning active.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> checkHeldKapak(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> checkHeldKapak(player));
    }

    private void checkHeldKapak(Player holder) {
        if (!holder.isOnline() || !isKapak(holder.getInventory().getItemInMainHand())) {
            return;
        }

        long now = System.currentTimeMillis();
        long last = lastWarning.getOrDefault(holder.getUniqueId(), 0L);
        if (now - last < COOLDOWN_MS) {
            return;
        }
        lastWarning.put(holder.getUniqueId(), now);

        String warning = color("&4&lJANGAN COBA-COBA LAWAN! &cJika kapak itu sudah dipegang oleh &6"
            + holder.getName() + " &ckamu bisa mati dengan sangat mengenaskan.");

        holder.getWorld().playSound(holder.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.2f, 0.55f);
        holder.getWorld().playSound(holder.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.1f, 0.65f);
        holder.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, holder.getLocation().add(0, 1.0, 0), 80, 1.0, 1.1, 1.0, 0.08);
        holder.getWorld().spawnParticle(Particle.LARGE_SMOKE, holder.getLocation().add(0, 1.0, 0), 80, 1.2, 1.1, 1.2, 0.08);

        for (Player nearby : holder.getWorld().getPlayers()) {
            if (nearby.equals(holder)) {
                continue;
            }
            if (nearby.getLocation().distanceSquared(holder.getLocation()) > WARNING_RADIUS * WARNING_RADIUS) {
                continue;
            }
            nearby.sendMessage(warning);
            nearby.sendTitle(color("&4&lBAHAYA"), color("&c" + holder.getName() + " memegang Kapak Dewa"), 5, 55, 15);
            nearby.playSound(nearby.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.45f);
            nearby.playSound(nearby.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.55f);
        }
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
