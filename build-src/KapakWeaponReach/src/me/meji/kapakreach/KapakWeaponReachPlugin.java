package me.meji.kapakreach;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class KapakWeaponReachPlugin extends JavaPlugin implements Listener {
    private static final double LONG_RANGE = 96.0D;
    private static final double LONG_RANGE_DAMAGE = 19.9D;
    private final Map<UUID, Long> lastRangeHit = new HashMap<>();
    private NamespacedKey kapakKey;
    private NamespacedKey rangeKey;

    @Override
    public void onEnable() {
        kapakKey = new NamespacedKey("kapakweapon", "kapak_weapon");
        rangeKey = new NamespacedKey(this, "divine_reach");
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            enhanceKapaks(player);
        }
        getLogger().info("KapakWeaponReach enabled: hunger refill, glint, and 96-block reach active.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> enhanceKapaks(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onGetKapak(PlayerCommandPreprocessEvent event) {
        if (!event.getMessage().trim().equalsIgnoreCase("/get kapak")) {
            return;
        }
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            enhanceKapaks(player);
            player.sendMessage(color("&6Kapakmu memancarkan aura enchant dan jangkauan dewa."));
        }, 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKapakDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!isKapak(player.getInventory().getItemInMainHand())) {
            return;
        }
        refillBody(player);
        enhanceItemInHand(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLongRangeSwing(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isKapak(hand)) {
            return;
        }
        enhanceItemInHand(player);

        long now = System.currentTimeMillis();
        long last = lastRangeHit.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 350L) {
            return;
        }

        LivingEntity target = findTarget(player);
        if (target == null) {
            return;
        }
        lastRangeHit.put(player.getUniqueId(), now);
        Location start = player.getEyeLocation();
        Location end = target.getLocation().add(0.0D, target.getHeight() * 0.55D, 0.0D);
        drawReachSlash(start, end);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.55f);
        player.getWorld().playSound(end, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 0.85f);
        target.damage(LONG_RANGE_DAMAGE, player);
    }

    private LivingEntity findTarget(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        LivingEntity best = null;
        double bestDistance = LONG_RANGE + 1.0D;

        for (Entity entity : player.getNearbyEntities(LONG_RANGE, LONG_RANGE, LONG_RANGE)) {
            if (!(entity instanceof LivingEntity living) || living == player || living.isDead()) {
                continue;
            }
            Location center = living.getLocation().add(0.0D, living.getHeight() * 0.55D, 0.0D);
            Vector toTarget = center.toVector().subtract(eye.toVector());
            double forwardDistance = toTarget.dot(direction);
            if (forwardDistance < 0.0D || forwardDistance > LONG_RANGE) {
                continue;
            }
            Vector closestPoint = eye.toVector().add(direction.clone().multiply(forwardDistance));
            double sideDistance = center.toVector().distance(closestPoint);
            double allowedWidth = Math.max(1.25D, living.getWidth() + 0.8D);
            if (sideDistance <= allowedWidth && forwardDistance < bestDistance && player.hasLineOfSight(living)) {
                best = living;
                bestDistance = forwardDistance;
            }
        }
        return best;
    }

    private void drawReachSlash(Location start, Location end) {
        Vector line = end.toVector().subtract(start.toVector());
        double length = line.length();
        if (length <= 0.01D) {
            return;
        }
        Vector step = line.normalize().multiply(0.45D);
        Location point = start.clone();
        for (double travelled = 0.0D; travelled < length; travelled += 0.45D) {
            point.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, point, 3, 0.08D, 0.08D, 0.08D, 0.02D);
            point.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, point, 2, 0.06D, 0.06D, 0.06D, 0.01D);
            point.add(step);
        }
    }

    private void enhanceKapaks(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isKapak(item)) {
                enhance(item);
            }
        }
    }

    private void enhanceItemInHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (isKapak(hand)) {
            enhance(hand);
            player.getInventory().setItemInMainHand(hand);
        }
    }

    private void enhance(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.removeAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE);
        meta.addAttributeModifier(
            Attribute.ENTITY_INTERACTION_RANGE,
            new AttributeModifier(rangeKey, 92.0D, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND)
        );
        item.setItemMeta(meta);
    }

    private void refillBody(Player player) {
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
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
