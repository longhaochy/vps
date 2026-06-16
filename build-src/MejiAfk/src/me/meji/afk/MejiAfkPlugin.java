package me.meji.afk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class MejiAfkPlugin extends JavaPlugin implements Listener, TabExecutor {
    private static final int DISPLAY_COUNT = 18;
    private static final double RADIUS = 2.25D;
    private static final String[] MANTRAS = {
        "天", "玄", "护", "灵", "阵", "守", "命", "息", "星", "月", "雷", "风"
    };
    private static final TextColor[] COLORS = {
        TextColor.color(0x66E3FF),
        TextColor.color(0xB47CFF),
        TextColor.color(0xFFD166),
        TextColor.color(0x7CFFB2),
        TextColor.color(0xFF7AB6)
    };

    private final Map<UUID, AfkState> afkStates = new HashMap<>();
    private int animationTaskId = -1;
    private double spin = 0.0D;

    @Override
    public void onEnable() {
        if (getCommand("afk") != null) {
            getCommand("afk").setExecutor(this);
            getCommand("afk").setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        animationTaskId = Bukkit.getScheduler().runTaskTimer(this, this::tickAfkDisplays, 1L, 2L).getTaskId();
        getLogger().info("MejiAfk enabled: /afk on and /afk off are ready.");
    }

    @Override
    public void onDisable() {
        if (animationTaskId != -1) {
            Bukkit.getScheduler().cancelTask(animationTaskId);
        }
        for (UUID uuid : new ArrayList<>(afkStates.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                disableAfk(player, false);
            } else {
                removeDisplays(afkStates.get(uuid));
            }
        }
        afkStates.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command ini hanya bisa dipakai player.");
            return true;
        }
        if (!player.hasPermission("mejiafk.use")) {
            player.sendMessage(ChatColor.RED + "Kamu tidak punya izin memakai /afk.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(ChatColor.YELLOW + "Pakai: /afk on atau /afk off");
            return true;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        if ("on".equals(mode)) {
            enableAfk(player);
            return true;
        }
        if ("off".equals(mode)) {
            disableAfk(player, true);
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Pakai: /afk on atau /afk off");
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && afkStates.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            player.setFireTicks(0);
            player.setFreezeTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        LivingEntity target = event.getTarget();
        if (target instanceof Player player && afkStates.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        AfkState state = afkStates.get(event.getPlayer().getUniqueId());
        if (state == null || event.getTo() == null) {
            return;
        }

        Location lock = state.lockLocation();
        Location to = event.getTo();
        if (!lock.getWorld().equals(to.getWorld())
            || lock.distanceSquared(to) > 0.003D) {
            Location corrected = lock.clone();
            corrected.setYaw(to.getYaw());
            corrected.setPitch(to.getPitch());
            event.setTo(corrected);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        disableAfk(event.getPlayer(), false);
    }

    private void enableAfk(Player player) {
        UUID uuid = player.getUniqueId();
        if (afkStates.containsKey(uuid)) {
            player.sendMessage(ChatColor.AQUA + "AFK sudah aktif. Pakai /afk off untuk lanjut main.");
            return;
        }

        AfkState state = new AfkState(
            player.getLocation().clone(),
            player.isInvulnerable(),
            player.isCollidable(),
            player.getNoDamageTicks(),
            new ArrayList<>()
        );
        afkStates.put(uuid, state);

        player.setInvulnerable(true);
        player.setCollidable(false);
        player.setNoDamageTicks(Integer.MAX_VALUE);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        spawnDisplays(player, state);

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8F, 1.35F);
        player.sendMessage(ChatColor.AQUA + "AFK aktif. Mantra pelindung menyala sampai /afk off.");
    }

    private void disableAfk(Player player, boolean showMessage) {
        AfkState state = afkStates.remove(player.getUniqueId());
        if (state == null) {
            if (showMessage) {
                player.sendMessage(ChatColor.YELLOW + "AFK belum aktif.");
            }
            return;
        }

        removeDisplays(state);
        player.setInvulnerable(state.invulnerable());
        player.setCollidable(state.collidable());
        player.setNoDamageTicks(Math.max(0, state.noDamageTicks()));
        player.setFireTicks(0);
        player.setFreezeTicks(0);

        if (showMessage) {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5F, 1.6F);
            player.sendMessage(ChatColor.GREEN + "Oke, lanjut main.");
        }
    }

    private void spawnDisplays(Player player, AfkState state) {
        World world = player.getWorld();
        Location base = state.lockLocation();
        for (int index = 0; index < DISPLAY_COUNT; index++) {
            Location location = displayLocation(base, index, 0.0D);
            TextDisplay display = world.spawn(location, TextDisplay.class, entity -> {
                entity.setPersistent(false);
                entity.setInvulnerable(true);
                entity.setGravity(false);
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setSeeThrough(true);
                entity.setShadowed(false);
                entity.setDefaultBackground(false);
                entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                entity.setTextOpacity((byte) 255);
                entity.setViewRange(32.0F);
                entity.setLineWidth(80);
            });
            state.displays().add(display);
        }
    }

    private void tickAfkDisplays() {
        if (afkStates.isEmpty()) {
            return;
        }

        spin += 0.16D;
        int colorOffset = (int) Math.floor(spin * 3.0D);
        for (Map.Entry<UUID, AfkState> entry : new ArrayList<>(afkStates.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                removeDisplays(entry.getValue());
                afkStates.remove(entry.getKey());
                continue;
            }

            AfkState state = entry.getValue();
            Location lock = state.lockLocation();
            player.setVelocity(player.getVelocity().zero());
            player.setFireTicks(0);
            player.setFreezeTicks(0);

            for (int index = 0; index < state.displays().size(); index++) {
                TextDisplay display = state.displays().get(index);
                if (!display.isValid()) {
                    continue;
                }
                display.teleport(displayLocation(lock, index, spin));
                display.text(Component.text(MANTRAS[(index + colorOffset) % MANTRAS.length], COLORS[(index + colorOffset) % COLORS.length]));
            }

            if (((int) (spin * 10.0D)) % 6 == 0) {
                player.getWorld().spawnParticle(
                    Particle.ENCHANT,
                    lock.clone().add(0.0D, 1.0D, 0.0D),
                    8,
                    1.4D,
                    0.8D,
                    1.4D,
                    0.0D
                );
            }
        }
    }

    private Location displayLocation(Location base, int index, double angleOffset) {
        double fraction = (double) index / (double) DISPLAY_COUNT;
        double angle = fraction * Math.PI * 2.0D + angleOffset;
        double y = 0.65D + Math.sin(angleOffset + index * 0.7D) * 0.45D;
        return base.clone().add(Math.cos(angle) * RADIUS, y, Math.sin(angle) * RADIUS);
    }

    private void removeDisplays(AfkState state) {
        if (state == null) {
            return;
        }
        for (Entity display : state.displays()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        state.displays().clear();
    }

    private record AfkState(
        Location lockLocation,
        boolean invulnerable,
        boolean collidable,
        int noDamageTicks,
        List<TextDisplay> displays
    ) {
    }
}
