package me.meji.kapak;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class KapakWeaponPlugin extends JavaPlugin implements Listener {
    private static final double DAMAGE = 19.9D;
    private NamespacedKey kapakKey;
    private NamespacedKey damageKey;

    @Override
    public void onEnable() {
        kapakKey = new NamespacedKey(this, "kapak_weapon");
        damageKey = new NamespacedKey(this, "kapak_damage");
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("KapakWeapon enabled. Use /get kapak.");
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
        if (args.length != 1 || !args[0].toLowerCase(Locale.ROOT).equals("kapak")) {
            player.sendMessage(color("&cPakai: /get kapak"));
            return true;
        }

        ItemStack item = createKapak();
        var leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        player.sendMessage(color("&aKamu mendapat &6Kapak Kayu Meji &7(Damage 19.9)."));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        return true;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (isKapak(hand)) {
            event.setDamage(DAMAGE);
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
            new AttributeModifier(new NamespacedKey(this, "kapak_attack_damage"), 18.9D, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND)
        );
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(kapakKey, PersistentDataType.BYTE, (byte) 1);
        data.set(damageKey, PersistentDataType.DOUBLE, DAMAGE);
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

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
