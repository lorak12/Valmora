package org.nakii.valmora.module.item;

import org.nakii.valmora.module.script.condition.ConditionGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AbilityDefinition {

    /**
     * How this ability's lore is rendered by {@code ItemFactory.updateLore}.
     * <ul>
     *   <li>{@link #FULL} — the default "banner" format: {@code Ability: <name> <TRIGGER>} header,
     *       description lines, then Mana Cost / Cooldown footer lines.</li>
     *   <li>{@link #SIMPLE} — just the (MiniMessage-formatted) {@code description} lines, no header
     *       and no cost/cooldown footer. For abilities whose author wants a single clean stat-style
     *       line (e.g. "Deals +50% more damage to undead mobs.") rather than the full ability block.</li>
     * </ul>
     */
    public enum DisplayMode { FULL, SIMPLE }

    private final String id;
    private final String name;
    private final AbilityTrigger trigger;
    private final double targetRange;
    private final double cooldown;
    private final double manaCost;
    private final List<String> description;
    private final DisplayMode displayMode;
    private final List<ConfiguredMechanic> mechanics;
    // Phase 5 (docs/REFACTOR/PROGRESS.md Task 20): pre-compiled once at load time by
    // ItemDefinitionParser via ConditionParser.parseList(...) — AbilityExecutor.conditionsPass()
    // (an ON_HIT-triggered combat hot path) used to re-parse these as raw expression strings on
    // every single hit instead of evaluating an already-compiled AST.
    private final ConditionGroup conditions;

    private AbilityDefinition(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.trigger = builder.trigger;
        this.targetRange = builder.targetRange;
        this.cooldown = builder.cooldown;
        this.manaCost = builder.manaCost;
        this.description = builder.description;
        this.displayMode = builder.displayMode;
        this.mechanics = builder.mechanics;
        this.conditions = builder.conditions;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public AbilityTrigger getTrigger() { return trigger; }
    public double getTargetRange() { return targetRange; }
    public double getCooldown() { return cooldown; }
    public double getManaCost() { return manaCost; }
    public List<String> getDescription() { return description; }
    public DisplayMode getDisplayMode() { return displayMode; }
    public List<ConfiguredMechanic> getMechanics() { return mechanics; }
    public ConditionGroup getConditions() { return conditions; }

    public static class Builder {
        private final String id;
        private String name;
        private AbilityTrigger trigger;
        private double targetRange = 0.0;
        private double cooldown = 0.0;
        private double manaCost = 0.0;
        private List<String> description = new ArrayList<>();
        private DisplayMode displayMode = DisplayMode.FULL;
        private List<ConfiguredMechanic> mechanics = new ArrayList<>();
        private ConditionGroup conditions = new ConditionGroup(Collections.emptyList());

        public Builder(String id) {
            this.id = id;
        }

        public Builder name(String name) { this.name = name; return this; }
        public Builder trigger(AbilityTrigger trigger) { this.trigger = trigger; return this; }
        public Builder targetRange(double targetRange) { this.targetRange = targetRange; return this; }
        public Builder cooldown(double cooldown) { this.cooldown = cooldown; return this; }
        public Builder manaCost(double manaCost) { this.manaCost = manaCost; return this; }
        public Builder description(List<String> description) { this.description = description; return this; }
        public Builder displayMode(DisplayMode displayMode) { this.displayMode = displayMode; return this; }
        public Builder addMechanic(ConfiguredMechanic mechanic) { this.mechanics.add(mechanic); return this; }
        public Builder conditions(ConditionGroup conditions) { this.conditions = conditions; return this; }

        public AbilityDefinition build() {
            return new AbilityDefinition(this);
        }
    }
}
