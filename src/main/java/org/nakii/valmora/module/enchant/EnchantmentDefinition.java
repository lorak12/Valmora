package org.nakii.valmora.module.enchant;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.module.item.ItemType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable enchant content definition. {@code combat}/{@code triggers}/{@code state}/{@code stats}
 * are parsed as raw {@link ConfigurationSection}s in this phase — inert placeholders carried
 * forward so the YAML schema is stable from here on; the script/state engine phases replace these
 * accessors' callers with real compiled objects without another schema change.
 */
public class EnchantmentDefinition {

    private final String id;
    private final String name;
    private final List<String> description;
    private final int etableMaxLevel;
    private final int absoluteMaxLevel;
    private final List<ItemType> targets;
    private final List<String> conflicts;
    private final EnchantmentLogic logic;
    private final Map<String, String> variables;
    private final ConfigurationSection combatSection;
    private final ConfigurationSection triggersSection;
    private final ConfigurationSection stateSection;
    private final ConfigurationSection statsSection;

    public EnchantmentDefinition(String id, String name, List<String> description, int etableMaxLevel,
                              int absoluteMaxLevel, List<ItemType> targets, List<String> conflicts,
                              EnchantmentLogic logic) {
        this(id, name, description, etableMaxLevel, absoluteMaxLevel, targets, conflicts, logic,
                Map.of(), null, null, null, null);
    }

    private EnchantmentDefinition(String id, String name, List<String> description, int etableMaxLevel,
                              int absoluteMaxLevel, List<ItemType> targets, List<String> conflicts,
                              EnchantmentLogic logic, Map<String, String> variables,
                              ConfigurationSection combatSection, ConfigurationSection triggersSection,
                              ConfigurationSection stateSection, ConfigurationSection statsSection) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.etableMaxLevel = etableMaxLevel;
        this.absoluteMaxLevel = absoluteMaxLevel;
        this.targets = targets;
        this.conflicts = conflicts;
        this.logic = logic;
        this.variables = variables;
        this.combatSection = combatSection;
        this.triggersSection = triggersSection;
        this.stateSection = stateSection;
        this.statsSection = statsSection;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getDescription() {
        return description;
    }

    public int getEtableMaxLevel() {
        return etableMaxLevel;
    }

    public int getAbsoluteMaxLevel() {
        return absoluteMaxLevel;
    }

    public List<ItemType> getTargets() {
        return targets;
    }

    public List<String> getConflicts() {
        return conflicts;
    }

    public EnchantmentLogic getLogic() {
        return logic;
    }

    /** Raw {@code $level$}-scoped formula strings from the YAML {@code variables:} block, backing
     *  {@code $calc.<name>$} once the script bridge (Phase 2) evaluates and attaches them. */
    public Map<String, String> getVariables() {
        return variables;
    }

    /** Raw {@code combat:} section (modify-attack/modify-defend) — inert until Phase 2 compiles it. */
    public ConfigurationSection getCombatSection() {
        return combatSection;
    }

    /** Raw {@code triggers:} section — inert until Phase 2 compiles it into dispatchable blocks. */
    public ConfigurationSection getTriggersSection() {
        return triggersSection;
    }

    /** Raw {@code state:} section (transient/persistent) — inert until Phase 3's state engine. */
    public ConfigurationSection getStateSection() {
        return stateSection;
    }

    /** Raw {@code stats:} section — inert until wired into {@code StatManager.recalculateStats}. */
    public ConfigurationSection getStatsSection() {
        return statsSection;
    }

    public boolean canApplyTo(ItemType type) {
        return targets.contains(type);
    }

    public boolean conflictsWith(String otherId) {
        return conflicts.contains(otherId.toLowerCase());
    }

    /** Fluent Java construction, mirroring {@code ModifierDefinition.builder}, for a plugin
     *  registering a custom enchant without YAML. */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private String name;
        private List<String> description = new ArrayList<>();
        private int etableMaxLevel = 5;
        private int absoluteMaxLevel = 10;
        private List<ItemType> targets = new ArrayList<>();
        private List<String> conflicts = new ArrayList<>();
        private EnchantmentLogic logic;
        private Map<String, String> variables = new LinkedHashMap<>();
        private ConfigurationSection combatSection;
        private ConfigurationSection triggersSection;
        private ConfigurationSection stateSection;
        private ConfigurationSection statsSection;

        private Builder(String id) {
            this.id = id;
            this.name = id;
        }

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(List<String> description) { this.description = description; return this; }
        public Builder etableMaxLevel(int level) { this.etableMaxLevel = level; return this; }
        public Builder absoluteMaxLevel(int level) { this.absoluteMaxLevel = level; return this; }
        public Builder targets(List<ItemType> targets) { this.targets = targets; return this; }
        public Builder target(ItemType target) { this.targets.add(target); return this; }
        public Builder conflicts(List<String> conflicts) { this.conflicts = conflicts; return this; }
        public Builder conflict(String enchantId) { this.conflicts.add(enchantId); return this; }
        public Builder logic(EnchantmentLogic logic) { this.logic = logic; return this; }
        public Builder variables(Map<String, String> variables) { this.variables = variables; return this; }
        public Builder variable(String name, String formula) { this.variables.put(name, formula); return this; }
        public Builder combatSection(ConfigurationSection section) { this.combatSection = section; return this; }
        public Builder triggersSection(ConfigurationSection section) { this.triggersSection = section; return this; }
        public Builder stateSection(ConfigurationSection section) { this.stateSection = section; return this; }
        public Builder statsSection(ConfigurationSection section) { this.statsSection = section; return this; }

        public EnchantmentDefinition build() {
            return new EnchantmentDefinition(id, name, description, etableMaxLevel, absoluteMaxLevel,
                    targets, conflicts, logic, Map.copyOf(variables), combatSection, triggersSection,
                    stateSection, statsSection);
        }
    }
}
