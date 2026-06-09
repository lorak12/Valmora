package org.nakii.valmora.module.mob;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.item.ConfiguredMechanic;
import org.nakii.valmora.module.mob.ability.MobAbility;
import org.nakii.valmora.module.mob.ability.MobAbilityTrigger;
import org.nakii.valmora.util.Formatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime driver for boss mobs. Only mobs that {@link MobDefinition#isBoss() are bosses}
 * (have abilities and/or a boss bar) are tracked, so normal mobs cost nothing.
 *
 * <p>A single repeating task drives {@code ON_TIMER} and {@code ON_HEALTH} abilities and
 * refreshes boss bars. {@code ON_ATTACK}/{@code ON_DAMAGED}/{@code ON_SPAWN}/{@code ON_DEATH}
 * are fired from the combat/spawn/death hooks.
 */
public class BossController {

    /** How often (ticks) the controller ticks. Timer-ability granularity is this period. */
    private static final long TICK_PERIOD = 10L;
    /** Radius (blocks) used to broadcast ability announcements. */
    private static final double ANNOUNCE_RADIUS = 40.0;

    private final Valmora plugin;
    private final Map<UUID, BossInstance> instances = new HashMap<>();
    private BukkitTask task;

    public BossController(Valmora plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) return;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (BossInstance instance : instances.values()) {
            hideBar(instance);
        }
        instances.clear();
    }

    /** Registers a freshly spawned boss and fires its ON_SPAWN abilities. */
    public void register(LivingEntity entity, MobDefinition definition) {
        if (!definition.isBoss()) return;

        BossInstance instance = new BossInstance(entity, definition);
        BossBarConfig barConfig = definition.getBossBar();
        if (barConfig != null && barConfig.isEnabled()) {
            instance.bossBar = BossBar.bossBar(
                    Formatter.format(Formatter.capitalize(definition.getName() == null ? definition.getId() : definition.getName())),
                    1.0f,
                    barConfig.getColor(),
                    barConfig.getOverlay()
            );
        }
        instances.put(entity.getUniqueId(), instance);

        for (MobAbility ability : definition.getAbilities()) {
            if (ability.getTrigger() == MobAbilityTrigger.ON_SPAWN) {
                fire(instance, ability, null);
            }
        }
    }

    public void unregister(UUID entityId) {
        BossInstance instance = instances.remove(entityId);
        if (instance != null) {
            hideBar(instance);
        }
    }

    public boolean isTracked(UUID entityId) {
        return instances.containsKey(entityId);
    }

    /** Fires ON_ATTACK abilities when the boss damages {@code target}. */
    public void onAttack(LivingEntity bossEntity, LivingEntity target) {
        fireEventAbilities(bossEntity, MobAbilityTrigger.ON_ATTACK, target);
    }

    /** Fires ON_DAMAGED abilities when the boss is hit by {@code attacker}. */
    public void onDamaged(LivingEntity bossEntity, LivingEntity attacker) {
        fireEventAbilities(bossEntity, MobAbilityTrigger.ON_DAMAGED, attacker);
    }

    /** Fires ON_DEATH abilities, then unregisters the boss. */
    public void onDeath(LivingEntity bossEntity) {
        fireEventAbilities(bossEntity, MobAbilityTrigger.ON_DEATH, bossEntity.getKiller());
        unregister(bossEntity.getUniqueId());
    }

    private void fireEventAbilities(LivingEntity bossEntity, MobAbilityTrigger trigger, LivingEntity target) {
        BossInstance instance = instances.get(bossEntity.getUniqueId());
        if (instance == null) return;
        for (MobAbility ability : instance.definition.getAbilities()) {
            if (ability.getTrigger() == trigger) {
                fire(instance, ability, target);
            }
        }
    }

    private void tick() {
        Iterator<Map.Entry<UUID, BossInstance>> it = instances.entrySet().iterator();
        while (it.hasNext()) {
            BossInstance instance = it.next().getValue();
            LivingEntity entity = instance.entity;

            if (entity == null || entity.isDead() || !entity.isValid()) {
                hideBar(instance);
                it.remove();
                continue;
            }

            instance.ticksAlive += TICK_PERIOD;

            for (MobAbility ability : instance.definition.getAbilities()) {
                switch (ability.getTrigger()) {
                    case ON_TIMER -> {
                        long last = instance.lastFiredTick.getOrDefault(ability.getId(), Long.MIN_VALUE);
                        if (instance.ticksAlive - last >= ability.getIntervalTicks()
                                && Math.random() < ability.getChance()) {
                            if (fire(instance, ability, null)) {
                                instance.lastFiredTick.put(ability.getId(), instance.ticksAlive);
                            }
                        }
                    }
                    case ON_HEALTH -> {
                        if (!instance.firedHealthAbilities.contains(ability.getId())) {
                            double percent = healthPercent(entity);
                            if (percent <= ability.getHealthPercent()) {
                                fire(instance, ability, null);
                                instance.firedHealthAbilities.add(ability.getId());
                            }
                        }
                    }
                    default -> { /* event-driven triggers handled elsewhere */ }
                }
            }

            updateBar(instance);
        }
    }

    /**
     * Executes an ability: respects the per-ability cooldown, resolves a target, announces, and
     * runs each mechanic with the boss as caster. Returns false if it was on cooldown.
     */
    private boolean fire(BossInstance instance, MobAbility ability, LivingEntity providedTarget) {
        long now = System.currentTimeMillis();
        Long expiry = instance.cooldownExpiry.get(ability.getId());
        if (expiry != null && now < expiry) {
            return false;
        }
        if (ability.getCooldownSeconds() > 0) {
            instance.cooldownExpiry.put(ability.getId(), now + (long) (ability.getCooldownSeconds() * 1000));
        }

        LivingEntity target = providedTarget;
        if (target == null && ability.getTargetRange() > 0) {
            target = findNearestPlayer(instance.entity, ability.getTargetRange());
        }

        if (ability.getAnnounce() != null && !ability.getAnnounce().isEmpty()) {
            for (Player p : nearbyPlayers(instance.entity, ANNOUNCE_RADIUS)) {
                p.sendMessage(Formatter.format(ability.getAnnounce()));
            }
        }

        for (ConfiguredMechanic mechanic : ability.getMechanics()) {
            mechanic.execute(instance.entity, target);
        }
        return true;
    }

    private void updateBar(BossInstance instance) {
        if (instance.bossBar == null) return;
        BossBar bar = instance.bossBar;
        double percent = healthPercent(instance.entity);
        bar.progress((float) Math.max(0.0, Math.min(1.0, percent / 100.0)));

        double range = instance.definition.getBossBar().getRange();
        Set<UUID> shouldSee = new HashSet<>();
        for (Player p : nearbyPlayers(instance.entity, range)) {
            shouldSee.add(p.getUniqueId());
            if (instance.barViewers.add(p.getUniqueId())) {
                p.showBossBar(bar);
            }
        }
        instance.barViewers.removeIf(uuid -> {
            if (!shouldSee.contains(uuid)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.hideBossBar(bar);
                return true;
            }
            return false;
        });
    }

    private void hideBar(BossInstance instance) {
        if (instance.bossBar == null) return;
        for (UUID uuid : instance.barViewers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.hideBossBar(instance.bossBar);
        }
        instance.barViewers.clear();
    }

    private double healthPercent(LivingEntity entity) {
        double max = maxHealth(entity);
        if (max <= 0) return 100.0;
        return (entity.getHealth() / max) * 100.0;
    }

    private double maxHealth(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : entity.getHealth();
    }

    private Player findNearestPlayer(LivingEntity entity, double range) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        double rangeSq = range * range;
        for (Player p : entity.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            double distSq = p.getLocation().distanceSquared(entity.getLocation());
            if (distSq <= rangeSq && distSq < best) {
                best = distSq;
                nearest = p;
            }
        }
        return nearest;
    }

    private List<Player> nearbyPlayers(LivingEntity entity, double range) {
        List<Player> result = new ArrayList<>();
        double rangeSq = range * range;
        for (Player p : entity.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(entity.getLocation()) <= rangeSq) {
                result.add(p);
            }
        }
        return result;
    }

    /** Per-entity runtime state for a tracked boss. */
    private static class BossInstance {
        final LivingEntity entity;
        final MobDefinition definition;
        BossBar bossBar;
        long ticksAlive = 0;
        final Map<String, Long> lastFiredTick = new HashMap<>();
        final Set<String> firedHealthAbilities = new HashSet<>();
        final Map<String, Long> cooldownExpiry = new HashMap<>();
        final Set<UUID> barViewers = new HashSet<>();

        BossInstance(LivingEntity entity, MobDefinition definition) {
            this.entity = entity;
            this.definition = definition;
        }
    }
}
