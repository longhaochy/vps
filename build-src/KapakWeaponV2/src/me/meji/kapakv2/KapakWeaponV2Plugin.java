package me.meji.kapakv2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public final class KapakWeaponV2Plugin extends JavaPlugin implements Listener {
    private static final double KAPAK_DAMAGE = 19.9D;
    private static final double PEDANG_NORMAL_DAMAGE = 20.9D;
    private static final double PEDANG_EXECUTE_DAMAGE = 10.9D;
    private static final int EXECUTE_HITS = 15;
    private static final double PEDANG_REACH = 38.0D;
    private static final long NORMAL_COOLDOWN_MS = 450L;
    private static final long EXECUTE_COOLDOWN_MS = 4800L;
    private final Map<UUID, Long> lastNormalHit = new HashMap<>();
    private final Map<UUID, Long> lastExecuteHit = new HashMap<>();
    private final Set<UUID> scriptedHitTargets = new HashSet<>();
    private NamespacedKey kapakKey;
    private NamespacedKey kapakDamageKey;
    private NamespacedKey pedangKey;

    @Override
    public void onEnable() {
        kapakKey = new NamespacedKey("kapakweapon", "kapak_weapon");
        kapakDamageKey = new NamespacedKey("kapakweapon", "kapak_damage");
        pedangKey = new NamespacedKey("mejipedang", "pedang_weapon");
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("KapakWeaponV2 enabled. Use /get kapak or /get pedang.");
    }

    @Override
    public void onDisable() {
        lastNormalHit.clear();
        lastExecuteHit.clear();
        scriptedHitTargets.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("get")) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command ini hanya untuk player.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(color("&cPakai: /get kapak &7atau &c/get pedang"));
            return true;
        }

        String itemName = args[0].toLowerCase(Locale.ROOT);
        if (itemName.equals("kapak")) {
            give(player, createKapak());
            player.sendMessage(color("&aKamu mendapat &6Kapak Kayu Meji &7(Damage 19.9)."));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
            return true;
        }
        if (itemName.equals("pedang")) {
            give(player, createPedang());
            player.sendMessage(color("&aKamu mendapat &bPedang Eksekusi Meji&7. Jongkok + hit untuk eksekusi 15x."));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.45f);
            return true;
        }

        player.sendMessage(color("&cPakai: /get kapak &7atau &c/get pedang"));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        UUID targetId = target.getUniqueId();
        if (scriptedHitTargets.contains(targetId)) {
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (isKapak(hand)) {
            event.setDamage(KAPAK_DAMAGE);
            return;
        }
        if (!isPedang(hand)) {
            return;
        }

        healAndFeed(player);
        applyTargetDebuffs(target);
        if (player.isSneaking()) {
            event.setDamage(0.0D);
            launchTarget(player, target, 3.45D, 0.92D);
            startExecution(player, target);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastNormalHit.getOrDefault(player.getUniqueId(), 0L) < NORMAL_COOLDOWN_MS) {
            return;
        }
        lastNormalHit.put(player.getUniqueId(), now);
        event.setDamage(PEDANG_NORMAL_DAMAGE);
        launchTarget(player, target, 4.15D, 1.05D);
        playPedangImpact(player, target, false);
        pulseSky(player, target, 50L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLongReachSwing(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!isPedang(player.getInventory().getItemInMainHand())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (player.isSneaking()) {
            if (now - lastExecuteHit.getOrDefault(player.getUniqueId(), 0L) < EXECUTE_COOLDOWN_MS) {
                return;
            }
        } else if (now - lastNormalHit.getOrDefault(player.getUniqueId(), 0L) < NORMAL_COOLDOWN_MS) {
            return;
        }
        LivingEntity target = findReachTarget(player);
        if (target == null) {
            return;
        }
        healAndFeed(player);
        applyTargetDebuffs(target);
        drawReachLine(player.getEyeLocation(), target.getLocation().add(0.0D, target.getHeight() * 0.55D, 0.0D));
        if (player.isSneaking()) {
            launchTarget(player, target, 3.45D, 0.92D);
            startExecution(player, target);
        } else {
            lastNormalHit.put(player.getUniqueId(), now);
            scriptedHitTargets.add(target.getUniqueId());
            try {
                target.damage(PEDANG_NORMAL_DAMAGE, player);
            } finally {
                scriptedHitTargets.remove(target.getUniqueId());
            }
            launchTarget(player, target, 4.15D, 1.05D);
            playPedangImpact(player, target, false);
            pulseSky(player, target, 50L);
        }
    }

    private void startExecution(Player player, LivingEntity target) {
        long now = System.currentTimeMillis();
        UUID attackerId = player.getUniqueId();
        if (now - lastExecuteHit.getOrDefault(attackerId, 0L) < EXECUTE_COOLDOWN_MS) {
            return;
        }
        lastExecuteHit.put(attackerId, now);
        Location returnLocation = player.getLocation().clone();
        playPedangImpact(player, target, true);
        pulseSky(player, target, 130L);

        new BukkitRunnable() {
            private int hit;

            @Override
            public void run() {
                if (!player.isOnline() || target.isDead() || hit >= EXECUTE_HITS) {
                    finishExecution(player, returnLocation);
                    cancel();
                    return;
                }
                chaseTarget(player, target);
                healAndFeed(player);
                applyTargetDebuffs(target);
                animateSky(player, target, hit);
                scriptedHitTargets.add(target.getUniqueId());
                try {
                    target.damage(PEDANG_EXECUTE_DAMAGE, player);
                } finally {
                    scriptedHitTargets.remove(target.getUniqueId());
                }
                playExecutionTick(player, target, hit);
                hit++;
            }
        }.runTaskTimer(this, 5L, 7L);
    }

    private void finishExecution(Player player, Location returnLocation) {
        if (!player.isOnline()) {
            return;
        }
        if (returnLocation.getWorld() != null && player.getWorld().equals(returnLocation.getWorld())) {
            player.teleport(returnLocation);
            player.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.55f, 1.25f);
        }
        resetSky(player);
    }

    private void chaseTarget(Player player, LivingEntity target) {
        Location targetLocation = target.getLocation();
        Vector back = targetLocation.getDirection().setY(0.0D);
        if (back.lengthSquared() < 0.01D) {
            back = player.getLocation().toVector().subtract(targetLocation.toVector()).setY(0.0D);
        }
        if (back.lengthSquared() < 0.01D) {
            back = new Vector(1.0D, 0.0D, 0.0D);
        }
        back.normalize().multiply(-1.55D);
        Location destination = targetLocation.clone().add(back);
        destination.setY(targetLocation.getY());
        face(destination, targetLocation);
        player.teleport(destination);
        player.setVelocity(targetLocation.toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.35D).setY(0.08D));
    }

    private void face(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        from.setYaw((float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ())));
        from.setPitch((float) Math.toDegrees(-Math.atan2(direction.getY(), Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ()))));
    }

    private void launchTarget(Player player, LivingEntity target, double power, double up) {
        Vector direction = target.getLocation().toVector().subtract(player.getLocation().toVector());
        if (direction.lengthSquared() < 0.01D) {
            direction = player.getLocation().getDirection();
        }
        direction.setY(0.0D).normalize().multiply(power).setY(up);
        target.setVelocity(direction);
    }

    private LivingEntity findReachTarget(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        LivingEntity best = null;
        double bestDistance = PEDANG_REACH + 1.0D;

        for (Entity entity : player.getNearbyEntities(PEDANG_REACH, PEDANG_REACH, PEDANG_REACH)) {
            if (!(entity instanceof LivingEntity living) || living == player || living.isDead()) {
                continue;
            }
            Location center = living.getLocation().add(0.0D, living.getHeight() * 0.55D, 0.0D);
            Vector toTarget = center.toVector().subtract(eye.toVector());
            double forwardDistance = toTarget.dot(direction);
            if (forwardDistance < 0.0D || forwardDistance > PEDANG_REACH) {
                continue;
            }
            Vector closestPoint = eye.toVector().add(direction.clone().multiply(forwardDistance));
            double sideDistance = center.toVector().distance(closestPoint);
            double allowedWidth = Math.max(1.75D, living.getWidth() + 1.25D);
            if (sideDistance <= allowedWidth && forwardDistance < bestDistance && player.hasLineOfSight(living)) {
                best = living;
                bestDistance = forwardDistance;
            }
        }
        return best;
    }

    private void drawReachLine(Location start, Location end) {
        Vector line = end.toVector().subtract(start.toVector());
        double length = line.length();
        if (length <= 0.01D) {
            return;
        }
        Vector step = line.normalize().multiply(0.85D);
        Location point = start.clone();
        for (double travelled = 0.0D; travelled < length; travelled += 0.85D) {
            point.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.04D, 0.04D, 0.04D, 0.01D);
            point.getWorld().spawnParticle(Particle.CRIT, point, 1, 0.04D, 0.04D, 0.04D, 0.01D);
            point.add(step);
        }
    }

    private void applyTargetDebuffs(LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 5, true, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 80, 4, true, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 4, true, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, true, true, true));
    }

    private void healAndFeed(Player player) {
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        } else {
            player.setHealth(20.0D);
        }
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
    }

    private void playPedangImpact(Player player, LivingEntity target, boolean execute) {
        Location center = target.getLocation().add(0.0D, Math.max(0.7D, target.getHeight() * 0.55D), 0.0D);
        World world = target.getWorld();
        world.spawnParticle(Particle.SWEEP_ATTACK, center, execute ? 3 : 1, 0.35D, 0.25D, 0.35D, 0.0D);
        world.spawnParticle(Particle.CRIT, center, execute ? 28 : 16, 0.35D, 0.35D, 0.35D, 0.12D);
        world.spawnParticle(Particle.ELECTRIC_SPARK, center, execute ? 18 : 10, 0.4D, 0.3D, 0.4D, 0.06D);
        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, execute ? 0.75f : 1.05f);
        world.playSound(center, Sound.ENTITY_BREEZE_WIND_BURST, 0.75f, 1.3f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.35f, 1.65f);
    }

    private void playExecutionTick(Player player, LivingEntity target, int hit) {
        Location center = target.getLocation().add(0.0D, Math.max(0.6D, target.getHeight() * 0.55D), 0.0D);
        World world = target.getWorld();
        world.spawnParticle(Particle.DAMAGE_INDICATOR, center, 8, 0.35D, 0.35D, 0.35D, 0.03D);
        world.spawnParticle(Particle.CRIT, center, 12, 0.32D, 0.32D, 0.32D, 0.08D);
        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.85f, 0.8f + hit * 0.08f);
    }

    private void pulseSky(Player player, LivingEntity target, long durationTicks) {
        animateSky(player, target, 0);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            resetSky(player);
            if (target instanceof Player targetPlayer) {
                resetSky(targetPlayer);
            }
        }, durationTicks);
    }

    private void animateSky(Player player, LivingEntity target, int step) {
        long[] times = {0L, 6000L, 12500L, 16000L, 18000L};
        long time = times[Math.floorMod(step, times.length)];
        applySky(player, time, step % 2 == 0);
        if (target instanceof Player targetPlayer) {
            applySky(targetPlayer, times[Math.floorMod(step + 2, times.length)], step % 2 != 0);
        }
    }

    private void applySky(Player player, long time, boolean storm) {
        player.setPlayerTime(time, false);
        if (storm) {
            player.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL);
        } else {
            player.setPlayerWeather(org.bukkit.WeatherType.CLEAR);
        }
    }

    private void resetSky(Player player) {
        if (!player.isOnline()) {
            return;
        }
        player.resetPlayerTime();
        player.resetPlayerWeather();
    }

    private void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack stack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
    }

    private ItemStack createKapak() {
        ItemStack item = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Kapak Kayu Meji", NamedTextColor.GOLD));
        meta.lore(java.util.List.of(
            Component.text("Damage: 19.9", NamedTextColor.RED),
            Component.text("Tidak mudah hancur", NamedTextColor.GRAY)
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.addAttributeModifier(
            Attribute.ATTACK_DAMAGE,
            new AttributeModifier(new NamespacedKey("kapakweapon", "kapak_attack_damage"), 18.9D, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND)
        );
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(kapakKey, PersistentDataType.BYTE, (byte) 1);
        data.set(kapakDamageKey, PersistentDataType.DOUBLE, KAPAK_DAMAGE);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPedang() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Pedang Eksekusi Meji", NamedTextColor.AQUA));
        meta.lore(java.util.List.of(
            Component.text("Hit biasa: knockback + 20.9 damage", NamedTextColor.RED),
            Component.text("Reach jauh: tebas target dari jarak besar", NamedTextColor.AQUA),
            Component.text("Jongkok + hit: kejar target dan eksekusi 15x", NamedTextColor.GRAY),
            Component.text("Selesai eksekusi balik ke posisi awal", NamedTextColor.GRAY),
            Component.text("Efek lebih ringan dari kapak", NamedTextColor.DARK_GRAY)
        ));
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addEnchant(Enchantment.KNOCKBACK, 2, true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.addAttributeModifier(
            Attribute.ATTACK_DAMAGE,
            new AttributeModifier(new NamespacedKey("mejipedang", "pedang_attack_damage"), 10.0D, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND)
        );
        meta.getPersistentDataContainer().set(pedangKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isKapak(ItemStack item) {
        if (item == null || item.getType() != Material.WOODEN_AXE || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(kapakKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private boolean isPedang(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_SWORD || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(pedangKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
