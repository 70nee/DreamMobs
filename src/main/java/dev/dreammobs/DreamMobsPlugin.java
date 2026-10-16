package dev.dreammobs;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.entity.LivingEntity;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class DreamMobsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private NamespacedKey dreamKey;
    private boolean warnedDisguise = false;
    private final Map<UUID, Long> rushCooldown = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        dreamKey = new NamespacedKey(this, "dream");
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("dreammobs")).setExecutor(this);
        Objects.requireNonNull(getCommand("dreammobs")).setTabCompleter(this);
        // Disguises are packet-based and may need reapplication on restart/chunk load.
        Bukkit.getScheduler().runTaskTimer(this, this::refreshNearbyDisguises, 100L, 200L);
        Bukkit.getScheduler().runTaskTimer(this, this::tickNightmareRush, 60L, 10L);
        getLogger().info("DreamMobs enabled; natural zombie replacement chance = "
                + getConfig().getDouble("zombie-replacement-chance", .5));
    }

    private boolean isDream(Entity entity) {
        return entity instanceof Zombie && entity.getPersistentDataContainer()
                .has(dreamKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNaturalSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        if (event.getEntityType() != EntityType.ZOMBIE) return;
        if (!(event.getEntity() instanceof Zombie zombie)) return;
        double chance = Math.max(0, Math.min(1, getConfig().getDouble("zombie-replacement-chance", .5)));
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        // Schedule next tick: avoid modifying while other plugins are handling spawn event.
        Bukkit.getScheduler().runTask(this, () -> {
            if (!zombie.isValid() || zombie.isDead() || isDream(zombie)) return;
            configureDream(zombie);
        });
    }

    private void configureDream(Zombie zombie) {
        zombie.setBaby(false);
        zombie.setCustomName("Dream");
        zombie.setCustomNameVisible(true);
        zombie.getPersistentDataContainer().set(dreamKey, PersistentDataType.BYTE, (byte)1);
        zombie.setRemoveWhenFarAway(true); // Regular hostile-mob despawning, not a permanent boss.
        setAttribute(zombie, Attribute.MAX_HEALTH, getConfig().getDouble("health", 150));
        setAttribute(zombie, Attribute.ATTACK_DAMAGE, getConfig().getDouble("attack-damage", 30));
        setAttribute(zombie, Attribute.ARMOR, getConfig().getDouble("armor", 12));
        setAttribute(zombie, Attribute.MOVEMENT_SPEED, getConfig().getDouble("movement-speed", .30));
        setAttribute(zombie, Attribute.FOLLOW_RANGE, getConfig().getDouble("follow-range", 40));
        AttributeInstance hp = zombie.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) zombie.setHealth(hp.getValue());
        applyDisguise(zombie);
    }

    private void setAttribute(Zombie zombie, Attribute attribute, double amount) {
        AttributeInstance value = zombie.getAttribute(attribute);
        if (value != null) value.setBaseValue(amount);
    }

    @EventHandler(ignoreCancelled = true)
    public void protectFromSun(EntityCombustEvent event) {
        if (isDream(event.getEntity())) event.setCancelled(true);
    }

    /** LibsDisguises is optional at compile time. Reflection prevents classloader failures
     * when the dependency isn't installed. The mob still exists but looks like a zombie. */
    private void applyDisguise(Zombie zombie) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) {
            if (!warnedDisguise) {
                warnedDisguise = true;
                getLogger().warning("LibsDisguises missing: Dream works but appears as a zombie. Install LibsDisguises + PacketEvents.");
            }
            return;
        }
        try {
            Class<?> api = Class.forName("me.libraryaddict.disguise.DisguiseAPI");
            if ((boolean) api.getMethod("isDisguised", Entity.class).invoke(null, zombie)) return;
            Class<?> disguise = Class.forName("me.libraryaddict.disguise.disguisetypes.Disguise");
            Class<?> playerDisguise = Class.forName("me.libraryaddict.disguise.disguisetypes.PlayerDisguise");
            Constructor<?> ctor = playerDisguise.getConstructor(String.class);
            Object playerSkin = ctor.newInstance("Dream");
            String skinName = getConfig().getString("skin-name", "DreamSkin");
            playerDisguise.getMethod("setSkin", String.class).invoke(playerSkin, skinName);
            Method show = api.getMethod("disguiseToAll", Entity.class, disguise);
            show.invoke(null, zombie, playerSkin);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (!warnedDisguise) {
                warnedDisguise = true;
                getLogger().warning("Dream disguise failed; check LibsDisguises version and imported skin: " + ex);
            }
        }
    }

    private void refreshNearbyDisguises() {
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) return;
        // Limited to entities near players to avoid iterating the entire loaded world.
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Entity entity : player.getNearbyEntities(64, 48, 64)) {
                if (isDream(entity) && entity instanceof Zombie zombie) applyDisguise(zombie);
            }
        }
    }

    /** Custom server-side hostile animation: purple charging spiral, sudden lunge,
     * followed by a sweeping, damaging impact. No resource pack or client mod needed.
     * Uses the underlying zombie location so gameplay is server authoritative. */
    private void tickNightmareRush() {
        if (!getConfig().getBoolean("nightmare-rush.enabled", true)) return;
        long now = System.currentTimeMillis();
        int cooldownSeconds = Math.max(3, getConfig().getInt("nightmare-rush.cooldown-seconds", 12));
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Entity e : viewer.getNearbyEntities(32, 20, 32)) {
                if (!(e instanceof Zombie dream) || !isDream(dream) || dream.isDead()) continue;
                LivingEntity target = dream.getTarget();
                if (!(target instanceof Player) || target.isDead() || !target.getWorld().equals(dream.getWorld())) continue;
                double distanceSquared = dream.getLocation().distanceSquared(target.getLocation());
                if (distanceSquared < 9 || distanceSquared > 64) continue;
                Long last = rushCooldown.get(dream.getUniqueId());
                if (last != null && now - last < cooldownSeconds * 1000L) continue;
                rushCooldown.put(dream.getUniqueId(), now);
                nightmareRush(dream, target);
            }
        }
        // prevent stale entities from accumulating in the cooldown map
        if (rushCooldown.size() > 5000) rushCooldown.entrySet().removeIf(entry -> now - entry.getValue() > 120000L);
    }

    private void nightmareRush(Zombie dream, LivingEntity target) {
        Location origin = dream.getLocation().add(0, 1.0, 0);
        dream.getWorld().playSound(origin, Sound.ENTITY_ENDERMAN_STARE, 0.9f, 0.6f);
        // 12 tick charge-up, purple spiral progressing upward
        for (int tick = 0; tick < 12; tick += 2) {
            final int t = tick;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!dream.isValid() || dream.isDead()) return;
                Location c = dream.getLocation().add(0, .3 + t * .10, 0);
                for (int i = 0; i < 9; i++) {
                    double angle = i * Math.PI * 2 / 9 + t * .42;
                    dream.getWorld().spawnParticle(Particle.PORTAL, c.clone().add(Math.cos(angle) * .85, 0, Math.sin(angle) * .85), 2, .04, .08, .04, .1);
                }
                dream.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, c, 6, .4, .4, .4, .01);
            }, t);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!dream.isValid() || dream.isDead() || !target.isValid() || target.isDead()) return;
            if (!dream.getWorld().equals(target.getWorld())) return;
            Vector toward = target.getLocation().toVector().subtract(dream.getLocation().toVector());
            if (toward.lengthSquared() > .01) dream.setVelocity(toward.normalize().multiply(1.25).setY(.32));
            dream.getWorld().playSound(dream.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, .6f);
            dream.getWorld().spawnParticle(Particle.SWEEP_ATTACK, dream.getLocation().add(0, 1, 0), 12, .7, .45, .7, .02);
            dream.getWorld().spawnParticle(Particle.PORTAL, dream.getLocation().add(0, 1, 0), 50, .8, .8, .8, .3);
        }, 12L);
        // Impact is dodgeable and respects normal damage events/protection plugins.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!dream.isValid() || dream.isDead() || !target.isValid() || target.isDead()) return;
            if (dream.getWorld().equals(target.getWorld()) && dream.getLocation().distanceSquared(target.getLocation()) <= 9) {
                target.damage(Math.max(0, getConfig().getDouble("nightmare-rush.bonus-damage", 8.0)), dream);
                dream.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 22, .6, .6, .6, .15);
            }
        }, 18L);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /dreammobs <spawn|reload|status>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Spawn must be run by an in-game player.");
                    return true;
                }
                Zombie zombie = (Zombie) player.getWorld().spawnEntity(player.getLocation().add(2, 0, 2), EntityType.ZOMBIE);
                configureDream(zombie);
                sender.sendMessage("Spawned Dream nearby.");
            }
            case "reload" -> {
                reloadConfig();
                sender.sendMessage("DreamMobs configuration reloaded; new stats apply to future Dream mobs.");
            }
            case "status" -> sender.sendMessage("DreamMobs: chance=" + getConfig().getDouble("zombie-replacement-chance")
                    + ", disguise plugin=" + Bukkit.getPluginManager().isPluginEnabled("LibsDisguises"));
            default -> sender.sendMessage("Usage: /dreammobs <spawn|reload|status>");
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("spawn", "reload", "status") : List.of();
    }
}
