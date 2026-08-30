package org.nakii.valmora.module.enchant;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.module.enchant.event.EnchantStateEventFactory;
import org.nakii.valmora.module.enchant.state.EnchantStateEngine;
import org.nakii.valmora.module.enchant.state.EnchantStateType;
import org.nakii.valmora.module.enchant.state.PersistentStateDefinition;
import org.nakii.valmora.module.enchant.state.TransientStateDefinition;
import org.nakii.valmora.module.enchant.state.TransientStateTracker;
import org.nakii.valmora.module.enchant.logic.StatBonusLogic;
import org.nakii.valmora.module.enchant.logic.DamageMultiplierLogic;
import org.nakii.valmora.module.enchant.logic.DefenseReductionLogic;
import org.nakii.valmora.module.enchant.logic.ExecuteLogic;
import org.nakii.valmora.module.enchant.logic.FirstStrikeLogic;
import org.nakii.valmora.module.enchant.logic.LethalityLogic;
import org.nakii.valmora.module.enchant.logic.LifeStealLogic;
import org.nakii.valmora.module.enchant.logic.RespiteLogic;
import org.nakii.valmora.module.enchant.logic.ThornsLogic;
import org.nakii.valmora.module.item.ItemType;
import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.module.script.condition.ConditionParser;
import org.nakii.valmora.module.script.event.EventParser;
import org.nakii.valmora.module.script.expression.ExpressionParser;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public class EnchantModule implements ReloadableModule {

    private final Valmora plugin;
    private final EnchantmentRegistry registry;
    private final Map<String, EnchantmentLogic> logicMap;
    private final Map<String, Function<ConfigurationSection, EnchantmentLogic>> logicFactories;
    private EnchantKillListener killListener;
    private TransientStateTracker stateTracker;
    private EnchantStateEngine stateEngine;
    private BukkitTask stateCleanupTask;

    public EnchantModule(Valmora plugin) {
        this.plugin = plugin;
        this.registry = new EnchantmentRegistry();
        this.logicMap = new ConcurrentHashMap<>();
        this.logicFactories = new ConcurrentHashMap<>();
    }

    @Override
    public void onEnable() {
        registerBuiltinLogics();

        // Script bridge (Phase 2 of the enchant overhaul) — enchant's own variable namespaces,
        // reusing the shared script module/HookBus infra rather than a parallel DSL.
        var scriptModule = plugin.getScriptModule();
        scriptModule.registerProvider(new EnchantLevelVariableProvider());
        scriptModule.registerProvider(new EnchantVariableProvider());
        scriptModule.registerProvider(new EnchantCalcVariableProvider());
        scriptModule.registerProvider(new HitVariableProvider());

        // State engine (Phase 3 of the enchant overhaul) — in-memory transient counters (combo
        // hits, stacking debuffs) plus the persistent (PDC) tier's mutation path. Recreated fresh
        // every onEnable (not just on first load): transient combat state is inherently session
        // bookkeeping, not meant to survive a reload.
        stateTracker = new TransientStateTracker();
        stateEngine = new EnchantStateEngine(stateTracker);
        scriptModule.registerEvent(new EnchantStateEventFactory());
        // Sweeps stale transient entries every 5 minutes — the fix for the pre-overhaul logic
        // classes' unbounded per-victim map growth (see TransientStateTracker's class doc).
        stateCleanupTask = plugin.getServer().getScheduler().runTaskTimer(plugin, stateTracker::cleanup, 6000L, 6000L);

        loadEnchants();

        killListener = new EnchantKillListener();
        plugin.getServer().getPluginManager().registerEvents(killListener, plugin);
    }

    private void registerBuiltinLogics() {
        // Phase 4.5 follow-up (docs/REFACTOR/PROGRESS.md): "sharpness" used to be a separate
        // hardcoded class (SharpnessLogic) applying a fixed 5%/level MELEE multiplier — an exact
        // special case of the generic valmora:damage_multiplier logic below. Registering it as a
        // factory with matching defaults means existing enchants/*.yml with `logic:
        // valmora:sharpness` and no logic-params behave identically, while a new enchant can now
        // override the damage type or percent-per-level via YAML.
        logicFactories.put("valmora:sharpness", params ->
            new DamageMultiplierLogic(params.getString("type", "MELEE"), params.getDouble("percent-per-level", 5.0)));

        // Phase 4.5 (docs/REFACTOR/PROGRESS.md): "growth", "fortune", and "efficiency" used to be
        // separate hardcoded Java classes (GrowthLogic, FortuneLogic, EfficiencyLogic), each just
        // a fixed per-level bonus to one stat — i.e. already a special case of the generic
        // valmora:stat_bonus logic below. Registering them as factories means any existing
        // enchants/*.yml with `logic: valmora:fortune` and no logic-params behaves identically
        // (defaults match the old hardcoded values), while a new enchant can override the bonus
        // via YAML without a code change. Defaults resolve through StatRoleRegistry (via
        // SystemStats) rather than a literal "health"/"mining_fortune" string, so a server that
        // renamed those roles keeps getting the right stat.
        logicFactories.put("valmora:growth", params ->
            new StatBonusLogic(params.getString("stat", ValmoraAPI.getInstance().getSystemStats().getHealth()),
                    params.getDouble("per-level", 10.0)));
        logicFactories.put("valmora:fortune", params ->
            new StatBonusLogic(params.getString("stat", ValmoraAPI.getInstance().getSystemStats().getMiningFortune()),
                    params.getDouble("per-level", 10.0)));
        logicFactories.put("valmora:efficiency", params ->
            new StatBonusLogic(params.getString("stat", ValmoraAPI.getInstance().getSystemStats().getMiningSpeed()),
                    params.getDouble("per-level", 50.0)));

        // Parameterized generic logics
        logicFactories.put("valmora:stat_bonus", params ->
            new StatBonusLogic(params.getString("stat", "strength"), params.getDouble("per-level", 1.0)));
        logicFactories.put("valmora:damage_multiplier", params ->
            new DamageMultiplierLogic(params.getString("type", "MELEE"), params.getDouble("percent-per-level", 5.0)));
        logicFactories.put("valmora:defense_reduction", params ->
            new DefenseReductionLogic(params.getDouble("percent-per-level", 3.0)));

        // The 7 enchants shipped in enchants/example_enchantments.yml with a `logic:` id that
        // nothing registered (added 2026-08-07 — docs/IMPLEMENTATION_BACKLOG.md, Enchant module).
        // "protection" is a plain per-level defense stat bonus — an exact fit for the existing
        // generic valmora:stat_bonus shape, same as growth/fortune/efficiency above; the other 6
        // needed real per-hit/conditional logic and got their own classes (module/enchant/logic/).
        logicFactories.put("valmora:protection", params ->
            new StatBonusLogic(params.getString("stat", ValmoraAPI.getInstance().getSystemStats().getDefense()),
                    params.getDouble("per-level", 4.0)));
        logicFactories.put("valmora:execute", params ->
            new ExecuteLogic(params.getDouble("percent-per-missing-percent", 0.2)));
        logicFactories.put("valmora:first_strike", params ->
            new FirstStrikeLogic(params.getDouble("percent-per-level", 25.0)));
        logicFactories.put("valmora:life_steal", params ->
            new LifeStealLogic(params.getDouble("percent-per-level", 0.5)));
        logicFactories.put("valmora:lethality", params ->
            new LethalityLogic(params.getDouble("percent-per-level-per-stack", 0.2)));
        logicFactories.put("valmora:respite", params ->
            new RespiteLogic(params.getDouble("per-level", 0.5)));
        logicFactories.put("valmora:thorns", params ->
            new ThornsLogic(params.getDouble("chance-percent", 15.0), params.getDouble("reflect-damage", 1.0)));
    }

    @Override
    public void onDisable() {
        if (killListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(killListener);
            killListener = null;
        }
        if (stateCleanupTask != null) {
            stateCleanupTask.cancel();
            stateCleanupTask = null;
        }
        if (stateTracker != null) {
            stateTracker.clear();
            stateTracker = null;
        }
        stateEngine = null;
        plugin.getScriptModule().getHookBus().clearYamlStages("enchant:");
        registry.clear();
        logicMap.clear();
        logicFactories.clear();
    }

    @Override
    public String getId() {
        return "enchants";
    }

    @Override
    public String getName() {
        return "Enchant System";
    }

    public EnchantmentRegistry getRegistry() {
        return registry;
    }

    /** The transient/persistent state facade — see {@link EnchantStateEngine}. Null before the
     *  first {@link #onEnable()} (or after {@link #onDisable()}). */
    public EnchantStateEngine getStateEngine() {
        return stateEngine;
    }

    public EnchantmentLogic getLogic(String id) {
        return logicMap.get(id.toLowerCase());
    }

    public void registerLogic(String id, EnchantmentLogic logic) {
        logicMap.put(id.toLowerCase(), logic);
    }

    private void loadEnchants() {
        YamlLoader<EnchantmentDefinition> loader = new YamlLoader<>(plugin, "enchants", "Enchantment");
        loader.load(createParser(), definition -> {
            registry.register(definition.getId(), definition);
            registerTriggerStages(definition);
        });
    }

    /** Registers every compiled {@code triggers.<TRIGGER>:} block onto the shared HookBus at
     *  {@code "enchant:<id>:<trigger>"} — mirrors GuiModule's per-definition YAML-stage
     *  registration at load time. Safe to call repeatedly across reloads: {@code onDisable()}
     *  clears every {@code "enchant:"}-prefixed stage first. */
    private void registerTriggerStages(EnchantmentDefinition definition) {
        var hookBus = plugin.getScriptModule().getHookBus();
        for (Map.Entry<EnchantTrigger, EnchantTriggerBlock> entry : definition.getTriggers().entrySet()) {
            String point = EnchantDispatcher.point(definition.getId(), entry.getKey());
            hookBus.registerYamlStage(point, new EnchantTriggerStage(definition.getId(), entry.getValue()));
        }
    }

    private YamlLoader.SectionParser<EnchantmentDefinition> createParser() {
        return (id, section, filePath) -> {
            try {
                String name = section.getString("name", id);
                List<String> description = section.getStringList("description");
                if (description == null) {
                    description = new ArrayList<>();
                }

                int etableMaxLevel = section.getInt("etable-max-level", 5);
                int absoluteMaxLevel = section.getInt("absolute-max-level", 10);

                List<ItemType> targets = parseTargets(section.getStringList("targets"));
                List<String> conflicts = section.getStringList("conflicts");
                if (conflicts == null) {
                    conflicts = new ArrayList<>();
                }

                String logicId = section.getString("logic", "");
                ConfigurationSection logicParams = section.getConfigurationSection("logic-params");
                if (logicParams == null) logicParams = new MemoryConfiguration();

                EnchantmentLogic logic;
                Function<ConfigurationSection, EnchantmentLogic> factory = logicFactories.get(logicId.toLowerCase());
                if (factory != null) {
                    logic = factory.apply(logicParams);
                } else {
                    logic = logicMap.get(logicId.toLowerCase());
                }
                // Warn instead of silently resolving to null (added 2026-08-07) — a typo'd
                // `logic:` id previously produced an enchant that loads fine but does nothing at
                // all, with no indication anything was wrong.
                if (logic == null && !logicId.isEmpty()) {
                    plugin.getLogger().warning("[Enchants] '" + id + "' references unknown logic id '"
                            + logicId + "' — it will have no gameplay effect.");
                }

                Map<String, String> variables = new LinkedHashMap<>();
                ConfigurationSection variablesSection = section.getConfigurationSection("variables");
                if (variablesSection != null) {
                    for (String key : variablesSection.getKeys(false)) {
                        variables.put(key, variablesSection.getString(key));
                    }
                }

                ConditionParser conditionParser = plugin.getScriptModule().getConditionParser();
                EventParser eventParser = plugin.getScriptModule().getEventParser();
                ExpressionParser expressionParser = plugin.getScriptModule().getExpressionParser();

                ConfigurationSection combatSection = section.getConfigurationSection("combat");
                EnchantCombatHook.CompiledCombatModifiers modifyAttack = combatSection == null ? null
                        : parseCombatModifiers(combatSection.getConfigurationSection("modify-attack"), conditionParser, expressionParser);
                EnchantCombatHook.CompiledCombatModifiers modifyDefend = combatSection == null ? null
                        : parseCombatModifiers(combatSection.getConfigurationSection("modify-defend"), conditionParser, expressionParser);

                Map<EnchantTrigger, EnchantTriggerBlock> triggers = parseTriggers(
                        section.getConfigurationSection("triggers"), conditionParser, eventParser);

                ConfigurationSection stateSection = section.getConfigurationSection("state");
                Map<String, TransientStateDefinition> transientStates = parseTransientStates(
                        stateSection == null ? null : stateSection.getConfigurationSection("transient"), id);
                Map<String, PersistentStateDefinition> persistentStates = parsePersistentStates(
                        stateSection == null ? null : stateSection.getConfigurationSection("persistent"), id);

                Map<String, Expression> statBonuses = parseStatBonuses(
                        section.getConfigurationSection("stats"), expressionParser);

                EnchantmentDefinition definition = EnchantmentDefinition.builder(id)
                        .name(name)
                        .description(description)
                        .etableMaxLevel(etableMaxLevel)
                        .absoluteMaxLevel(absoluteMaxLevel)
                        .targets(targets)
                        .conflicts(conflicts)
                        .logic(logic)
                        .variables(variables)
                        .modifyAttack(modifyAttack)
                        .modifyDefend(modifyDefend)
                        .triggers(triggers)
                        .transientStates(transientStates)
                        .persistentStates(persistentStates)
                        .statBonuses(statBonuses)
                        .build();

                return LoadResult.success(definition);
            } catch (Exception e) {
                return LoadResult.failure("[" + filePath + "] Failed to parse enchant '" + id + "': " + e.getMessage());
            }
        };
    }

    /** Compiles one {@code modify-attack:}/{@code modify-defend:} sub-section into a
     *  {@link EnchantCombatHook.CompiledCombatModifiers}, or {@code null} if absent. */
    private EnchantCombatHook.CompiledCombatModifiers parseCombatModifiers(ConfigurationSection section,
            ConditionParser conditionParser, ExpressionParser expressionParser) {
        if (section == null) return null;

        Condition conditions = null;
        List<String> conditionStrings = section.getStringList("conditions");
        if (conditionStrings != null && !conditionStrings.isEmpty()) {
            conditions = conditionParser.parseList(conditionStrings);
        }

        Map<String, Expression> modifiers = new LinkedHashMap<>();
        ConfigurationSection modifiersSection = section.getConfigurationSection("modifiers");
        if (modifiersSection != null) {
            for (String key : modifiersSection.getKeys(false)) {
                String formula = modifiersSection.getString(key);
                if (formula != null) modifiers.put(key, expressionParser.parse(formula));
            }
        }

        return new EnchantCombatHook.CompiledCombatModifiers(conditions, modifiers);
    }

    /** Compiles the {@code triggers:} section's {@code <TRIGGER>:} sub-blocks into
     *  {@link EnchantTriggerBlock}s, keyed by {@link EnchantTrigger}. Unknown trigger names are
     *  warned about and skipped (mirrors the unknown-{@code logic:} warning above). */
    private Map<EnchantTrigger, EnchantTriggerBlock> parseTriggers(ConfigurationSection section,
            ConditionParser conditionParser, EventParser eventParser) {
        Map<EnchantTrigger, EnchantTriggerBlock> result = new EnumMap<>(EnchantTrigger.class);
        if (section == null) return result;

        for (String key : section.getKeys(false)) {
            EnchantTrigger trigger;
            try {
                trigger = EnchantTrigger.valueOf(key.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Enchants] Unknown trigger '" + key + "' — it will never fire.");
                continue;
            }

            ConfigurationSection triggerSection = section.getConfigurationSection(key);
            if (triggerSection == null) continue;

            Condition conditions = null;
            List<String> conditionStrings = triggerSection.getStringList("conditions");
            if (conditionStrings != null && !conditionStrings.isEmpty()) {
                conditions = conditionParser.parseList(conditionStrings);
            }

            List<String> actionStrings = triggerSection.getStringList("actions");
            var actions = eventParser.parseList(actionStrings);

            List<String> failActionStrings = triggerSection.getStringList("fail-actions");
            var failActions = (failActionStrings == null || failActionStrings.isEmpty())
                    ? null : eventParser.parseList(failActionStrings);

            result.put(trigger, new EnchantTriggerBlock(conditions, actions, failActions));
        }
        return result;
    }

    /** Compiles {@code state.transient:} entries into {@link TransientStateDefinition}s, keyed by
     *  state key. An unknown {@code type:} is warned about and the entry is skipped, mirroring the
     *  unknown-{@code logic:}/unknown-trigger warnings above. */
    private Map<String, TransientStateDefinition> parseTransientStates(ConfigurationSection section, String enchantId) {
        Map<String, TransientStateDefinition> result = new LinkedHashMap<>();
        if (section == null) return result;

        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) continue;

            String typeName = entry.getString("type", "HIT_COUNTER");
            try {
                EnchantStateType.valueOf(typeName.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Enchants] '" + enchantId + "' state.transient." + key
                        + " has unknown type '" + typeName + "' — it will never resolve.");
                continue;
            }

            long resetAfterSeconds = entry.getLong("reset-after-seconds", 0L);
            boolean resetOnTargetSwitch = entry.getBoolean("reset-on-target-switch", false);
            int maxStacks = entry.getInt("max-stacks", 0);
            result.put(key, new TransientStateDefinition(resetAfterSeconds, resetOnTargetSwitch, maxStacks));
        }
        return result;
    }

    /** Compiles {@code state.persistent:} entries into {@link PersistentStateDefinition}s, keyed by
     *  state key. Same unknown-{@code type:} warning behavior as {@link #parseTransientStates}. */
    private Map<String, PersistentStateDefinition> parsePersistentStates(ConfigurationSection section, String enchantId) {
        Map<String, PersistentStateDefinition> result = new LinkedHashMap<>();
        if (section == null) return result;

        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) continue;

            String typeName = entry.getString("type", "INTEGER");
            try {
                EnchantStateType.valueOf(typeName.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Enchants] '" + enchantId + "' state.persistent." + key
                        + " has unknown type '" + typeName + "' — it will never resolve.");
                continue;
            }

            result.put(key, new PersistentStateDefinition(entry.getInt("default", 0)));
        }
        return result;
    }

    /** Compiles {@code stats:} entries (a plain {@code statId: "<formula>"} map, {@code $level$}-
     *  scoped like {@code variables:}) into {@link Expression}s, applied additively by {@code
     *  StatManager.recalculateStats}. */
    private Map<String, Expression> parseStatBonuses(ConfigurationSection section, ExpressionParser expressionParser) {
        Map<String, Expression> result = new LinkedHashMap<>();
        if (section == null) return result;

        for (String statId : section.getKeys(false)) {
            String formula = section.getString(statId);
            if (formula != null) result.put(statId, expressionParser.parse(formula));
        }
        return result;
    }

    private List<ItemType> parseTargets(List<String> targetStrings) {
        List<ItemType> targets = new ArrayList<>();
        if (targetStrings != null) {
            for (String target : targetStrings) {
                try {
                    targets.add(ItemType.valueOf(target.toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return targets;
    }
}