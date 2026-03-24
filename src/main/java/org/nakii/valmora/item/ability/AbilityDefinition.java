package org.nakii.valmora.item.ability;

import java.util.ArrayList;
import java.util.List;

public class AbilityDefinition {
    private final String id;
    private final String name;
    private final AbilityTrigger trigger;
    private final double targetRange;
    private final double cooldown;
    private final double manaCost;
    private final List<String> description;
    private final List<ConfiguredMechanic> mechanics;

    private AbilityDefinition(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.trigger = builder.trigger;
        this.targetRange = builder.targetRange;
        this.cooldown = builder.cooldown;
        this.manaCost = builder.manaCost;
        this.description = builder.description;
        this.mechanics = builder.mechanics;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public AbilityTrigger getTrigger() { return trigger; }
    public double getTargetRange() { return targetRange; }
    public double getCooldown() { return cooldown; }
    public double getManaCost() { return manaCost; }
    public List<String> getDescription() { return description; }
    public List<ConfiguredMechanic> getMechanics() { return mechanics; }

    public static class Builder {
        private final String id;
        private String name;
        private AbilityTrigger trigger;
        private double targetRange = 0.0;
        private double cooldown = 0.0;
        private double manaCost = 0.0;
        private List<String> description = new ArrayList<>();
        private List<ConfiguredMechanic> mechanics = new ArrayList<>();

        public Builder(String id) {
            this.id = id;
        }

        public Builder name(String name) { this.name = name; return this; }
        public Builder trigger(AbilityTrigger trigger) { this.trigger = trigger; return this; }
        public Builder targetRange(double targetRange) { this.targetRange = targetRange; return this; }
        public Builder cooldown(double cooldown) { this.cooldown = cooldown; return this; }
        public Builder manaCost(double manaCost) { this.manaCost = manaCost; return this; }
        public Builder description(List<String> description) { this.description = description; return this; }
        public Builder addMechanic(ConfiguredMechanic mechanic) { this.mechanics.add(mechanic); return this; }

        public AbilityDefinition build() {
            return new AbilityDefinition(this);
        }
    }
}