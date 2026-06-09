package org.nakii.valmora.module.mob.ability;

import org.nakii.valmora.module.item.ConfiguredMechanic;

import java.util.ArrayList;
import java.util.List;

/**
 * A single boss ability. Reuses the item ability mechanic system ({@link ConfiguredMechanic} +
 * the shared {@code MechanicRegistry}); only the trigger layer is mob-specific.
 */
public class MobAbility {
    private final String id;
    private final String name;
    private final MobAbilityTrigger trigger;
    private final int intervalTicks;     // ON_TIMER: how often the timer ability is eligible to fire
    private final double chance;         // ON_TIMER: roll chance per eligible tick (0..1)
    private final double healthPercent;  // ON_HEALTH: fire once when health drops below this percent (0..100)
    private final double targetRange;    // how far to look for a player target (0 = no target)
    private final double cooldownSeconds;
    private final String announce;       // optional MiniMessage broadcast to nearby players when fired
    private final List<ConfiguredMechanic> mechanics;

    private MobAbility(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.trigger = builder.trigger;
        this.intervalTicks = builder.intervalTicks;
        this.chance = builder.chance;
        this.healthPercent = builder.healthPercent;
        this.targetRange = builder.targetRange;
        this.cooldownSeconds = builder.cooldownSeconds;
        this.announce = builder.announce;
        this.mechanics = builder.mechanics;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public MobAbilityTrigger getTrigger() { return trigger; }
    public int getIntervalTicks() { return intervalTicks; }
    public double getChance() { return chance; }
    public double getHealthPercent() { return healthPercent; }
    public double getTargetRange() { return targetRange; }
    public double getCooldownSeconds() { return cooldownSeconds; }
    public String getAnnounce() { return announce; }
    public List<ConfiguredMechanic> getMechanics() { return mechanics; }

    public static class Builder {
        private final String id;
        private String name;
        private MobAbilityTrigger trigger = MobAbilityTrigger.ON_TIMER;
        private int intervalTicks = 100;
        private double chance = 1.0;
        private double healthPercent = 50.0;
        private double targetRange = 0.0;
        private double cooldownSeconds = 0.0;
        private String announce = null;
        private List<ConfiguredMechanic> mechanics = new ArrayList<>();

        public Builder(String id) {
            this.id = id;
            this.name = id;
        }

        public Builder name(String name) { this.name = name; return this; }
        public Builder trigger(MobAbilityTrigger trigger) { this.trigger = trigger; return this; }
        public Builder intervalTicks(int intervalTicks) { this.intervalTicks = intervalTicks; return this; }
        public Builder chance(double chance) { this.chance = chance; return this; }
        public Builder healthPercent(double healthPercent) { this.healthPercent = healthPercent; return this; }
        public Builder targetRange(double targetRange) { this.targetRange = targetRange; return this; }
        public Builder cooldownSeconds(double cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; return this; }
        public Builder announce(String announce) { this.announce = announce; return this; }
        public Builder addMechanic(ConfiguredMechanic mechanic) { this.mechanics.add(mechanic); return this; }

        public MobAbility build() {
            return new MobAbility(this);
        }
    }
}
