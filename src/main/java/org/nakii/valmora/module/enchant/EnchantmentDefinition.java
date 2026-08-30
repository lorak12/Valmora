package org.nakii.valmora.module.enchant;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.module.item.ItemType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable enchant content definition. {@code combat}/{@code triggers} are compiled at load time
 * (Phase 2 of the enchant overhaul) into {@link EnchantCombatHook.CompiledCombatModifiers}/{@link
 * EnchantTriggerBlock}; {@code state}/{@code stats} are still parsed as raw {@link
 * ConfigurationSection}s — inert placeholders carried forward until Phase 3/4 wire them up, so the
 * YAML schema stays stable.
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
    private final EnchantCombatHook.CompiledCombatModifiers modifyAttack;
    private final EnchantCombatHook.CompiledCombatModifiers modifyDefend;
    private final Map<EnchantTrigger, EnchantTriggerBlock> triggers;
    private final ConfigurationSection stateSection;
    private final ConfigurationSection statsSection;

    public EnchantmentDefinition(String id, String name, List<String> description, int etableMaxLevel,
                              int absoluteMaxLevel, List<ItemType> targets, List<String> conflicts,
                              EnchantmentLogic logic) {
        this(id, name, description, etableMaxLevel, absoluteMaxLevel, targets, conflicts, logic,
                Map.of(), null, null, Map.of(), null, null);
    }

    private EnchantmentDefinition(String id, String name, List<String> description, int etableMaxLevel,
                              int absoluteMaxLevel, List<ItemType> targets, List<String> conflicts,
                              EnchantmentLogic logic, Map<String, String> variables,
                              EnchantCombatHook.CompiledCombatModifiers modifyAttack,
                              EnchantCombatHook.CompiledCombatModifiers modifyDefend,
                              Map<EnchantTrigger, EnchantTriggerBlock> triggers,
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
        this.modifyAttack = modifyAttack;
        this.modifyDefend = modifyDefend;
        this.triggers = triggers;
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

    /** Raw {@code $level$}-scoped formula strings from the YAML {@code variables:} block, evaluated
     *  once per dispatch and attached as {@code $calc.<name>$} (see {@link EnchantDispatcher}). */
    public Map<String, String> getVariables() {
        return variables;
    }

    /** Compiled {@code combat.modify-attack:} block, or {@code null} if not declared. */
    public EnchantCombatHook.CompiledCombatModifiers getModifyAttack() {
        return modifyAttack;
    }

    /** Compiled {@code combat.modify-defend:} block, or {@code null} if not declared. */
    public EnchantCombatHook.CompiledCombatModifiers getModifyDefend() {
        return modifyDefend;
    }

    /** Compiled {@code triggers.<TRIGGER>:} blocks, keyed by trigger — empty if none declared. */
    public Map<EnchantTrigger, EnchantTriggerBlock> getTriggers() {
        return triggers;
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
        private EnchantCombatHook.CompiledCombatModifiers modifyAttack;
        private EnchantCombatHook.CompiledCombatModifiers modifyDefend;
        private Map<EnchantTrigger, EnchantTriggerBlock> triggers = new EnumMap<>(EnchantTrigger.class);
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
        public Builder modifyAttack(EnchantCombatHook.CompiledCombatModifiers block) { this.modifyAttack = block; return this; }
        public Builder modifyDefend(EnchantCombatHook.CompiledCombatModifiers block) { this.modifyDefend = block; return this; }
        public Builder triggers(Map<EnchantTrigger, EnchantTriggerBlock> triggers) { this.triggers = triggers; return this; }
        public Builder trigger(EnchantTrigger trigger, EnchantTriggerBlock block) { this.triggers.put(trigger, block); return this; }
        public Builder stateSection(ConfigurationSection section) { this.stateSection = section; return this; }
        public Builder statsSection(ConfigurationSection section) { this.statsSection = section; return this; }

        public EnchantmentDefinition build() {
            return new EnchantmentDefinition(id, name, description, etableMaxLevel, absoluteMaxLevel,
                    targets, conflicts, logic, Map.copyOf(variables), modifyAttack, modifyDefend,
                    Map.copyOf(triggers), stateSection, statsSection);
        }
    }
}
