package org.nakii.valmora.module.script;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.api.pipeline.HookBus;
import org.nakii.valmora.api.registry.Registry;
import org.nakii.valmora.api.registry.SimpleRegistry;
import org.nakii.valmora.module.script.variable.VariableProvider;
import org.nakii.valmora.module.script.variable.VariableResolverImpl;
import org.nakii.valmora.module.script.variable.providers.*;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.impl.*;

import org.nakii.valmora.module.script.expression.ExpressionParser;
import org.nakii.valmora.module.script.expression.ExpressionEvaluatorImpl;
import org.nakii.valmora.api.scripting.ExpressionEvaluator;
import org.nakii.valmora.module.script.condition.ConditionParser;
import org.nakii.valmora.module.script.event.EventParser;
import org.nakii.valmora.api.scripting.VariableResolver;

/**
 * Core module for the Valmora Scripting System.
 * Manages variables, expressions, conditions, and events.
 */
public class ScriptModule implements ReloadableModule {

    private final Valmora plugin;
    private final Registry<VariableProvider> variableProviderRegistry = new SimpleRegistry<>();
    private final Registry<EventFactory> eventFactoryRegistry = new SimpleRegistry<>();
    /**
     * Shared "trigger name -> conditions -> pass/fail actions" dispatch bus, hosted here because
     * it's generic scripting infra (same primitive as GUI event blocks / item abilities / mob
     * abilities, just reusable instead of reimplemented per-domain — see
     * docs/COMBAT_PIPELINE_ANALYSIS.md). Held as a field so it survives {@code /valmora reload}:
     * Java-registered hooks from addon plugins are not tied to this module's onEnable/onDisable
     * cycle. Domain loaders (e.g. combat's {@code CombatPipelineLoader}) clear and re-register only
     * their own YAML-loaded stages on reload via {@link HookBus#clearYamlStages(String)}.
     */
    private final HookBus hookBus;

    private VariableResolver variableResolver;
    private ExpressionParser expressionParser;
    private ExpressionEvaluator expressionEvaluator;
    private ConditionParser conditionParser;
    private EventParser eventParser;
    private DelayedEventTracker delayedEvents;

    /**
     * Compiled action/condition lists by content — for code paths that only have the raw strings
     * at runtime (quest-board rewards, pet milestones, collection rewards, ...). Loaders warm it by
     * compiling their lists inside their load scope, so problems are reported at load time and
     * nothing is re-parsed per execution. Cleared on enable and whenever {@code ScriptEpoch} moves.
     */
    private final java.util.Map<java.util.List<String>, org.nakii.valmora.api.scripting.CompiledEvent> eventCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.List<String>, org.nakii.valmora.api.scripting.Condition> conditionCache = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile int cacheEpoch;
    private final org.nakii.valmora.module.script.compile.ScriptChecks scriptChecks = new org.nakii.valmora.module.script.compile.ScriptChecks();
    private static final int MAX_CACHED = 4096;

    public ScriptModule(Valmora plugin) {
        this.plugin = plugin;
        this.hookBus = new HookBus(plugin);
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Initializing Script Engine...");
        
        this.variableResolver = new VariableResolverImpl(this);
        this.expressionParser = new ExpressionParser();
        ExpressionParser.setVariableListener(scriptChecks::onVariable);
        this.expressionEvaluator = new ExpressionEvaluatorImpl(this);
        this.conditionParser = new ConditionParser(this.expressionParser);
        this.eventParser = new EventParser(this);
        this.delayedEvents = new DelayedEventTracker(plugin);
        eventCache.clear();
        conditionCache.clear();
        // No server in plain unit tests (they build a ScriptModule off a mocked plugin).
        if (plugin.getServer() != null && plugin.getServer().getPluginManager() != null) {
            plugin.getServer().getPluginManager().registerEvents(delayedEvents, plugin);
        }

        // Register default providers
        registerProvider(new PlayerVariableProvider());
        registerProvider(new SystemVariableProvider());
        registerProvider(new WorldVariableProvider());
        registerProvider(new ServerVariableProvider());
        registerProvider(new PropVariableProvider());
        registerProvider(new ParamVariableProvider());
        registerProvider(new RangeVariableProvider());
        registerProvider(new TimeVariableProvider());
        registerProvider(new TargetVariableProvider());
        registerProvider(new DamageVariableProvider());
        registerProvider(new CurveVariableProvider());
        registerProvider(new MobVariableProvider());
        registerProvider(new ResourceVariableProvider());
        registerProvider(new FishingVariableProvider());
        registerProvider(new ItemAbilityVariableProvider());
        registerProvider(new MathVariableProvider());

        // Register default events
        registerEvent(new ConditionEvent(this));
        registerEvent(new GiveEvent());
        registerEvent(new VariableEvent());
        registerEvent(new TagEvent());
        registerEvent(new TeleportEventFactory());
        registerEvent(new SpawnMobEventFactory());
        registerEvent(new StatModifyEventFactory());
        registerEvent(new ForeachEventFactory(this));
        registerEvent(new RunScriptEventFactory(this));
        registerEvent(new InterruptEventFactory());
        registerEvent(new NotifyEventFactory());
        registerEvent(new CounterEventFactory());
        registerEvent(new EntityEventFactory(this));
        registerEvent(new ApplyPotionEventFactory());
        registerEvent(new EconomyCoinsEventFactory(true));
        registerEvent(new EconomyCoinsEventFactory(false));
        // Generic entity heal/damage/lightning — added for the enchant overhaul's trigger actions
        // (life_steal/thorns/thunderlord) but domain-agnostic, so registered here rather than
        // enchant-private (see HealEventFactory's class doc).
        registerEvent(new HealEventFactory());
        registerEvent(new DamageEventFactory());
        registerEvent(new StrikeLightningEventFactory());
    }

    public void registerProvider(VariableProvider provider) {
        variableProviderRegistry.register(provider.getNamespace(), provider);
    }

    public void registerEvent(EventFactory factory) {
        var existing = eventFactoryRegistry.get(factory.getName());
        if (existing.isPresent() && existing.get().getClass() != factory.getClass()) {
            plugin.getLogger().warning("[Script] Event '" + factory.getName() + "' is registered twice ("
                    + existing.get().getClass().getSimpleName() + " and " + factory.getClass().getSimpleName()
                    + ") — the later one wins. Use replaceEvent() if that's intended.");
        }
        eventFactoryRegistry.register(factory.getName(), factory);
    }

    /** Registers {@code factory}, deliberately replacing any earlier event of the same name (no warning). */
    public void replaceEvent(EventFactory factory) {
        eventFactoryRegistry.register(factory.getName(), factory);
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Script Engine...");
        if (delayedEvents != null) {
            delayedEvents.cancelAll();
            org.bukkit.event.HandlerList.unregisterAll(delayedEvents);
            delayedEvents = null;
        }
        variableProviderRegistry.clear();
        eventFactoryRegistry.clear();
    }

    @Override
    public String getId() {
        return "script";
    }

    @Override
    public String getName() {
        return "Script Engine";
    }

    /**
     * @return registry containing all available variable providers.
     */
    public Registry<VariableProvider> getVariableProviderRegistry() {
        return variableProviderRegistry;
    }

    /**
     * @return registry containing all available event factories.
     */
    public Registry<EventFactory> getEventFactoryRegistry() {
        return eventFactoryRegistry;
    }

    /** Owner of scripts' {@code delay:} tasks. */
    public DelayedEventTracker getDelayedEvents() {
        return delayedEvents;
    }

    public VariableResolver getVariableResolver() {
        return variableResolver;
    }

    public Valmora getValmora() {
        return plugin;
    }

    public ExpressionParser getExpressionParser() {
        return expressionParser;
    }

    public ExpressionEvaluator getExpressionEvaluator() {
        return expressionEvaluator;
    }

    public ConditionParser getConditionParser() {
        return conditionParser;
    }

    public EventParser getEventParser() {
        return eventParser;
    }

    /** The compiled form of an action list, compiled once per content (see {@link #eventCache}). */
    public org.nakii.valmora.api.scripting.CompiledEvent compileCached(java.util.List<String> lines) {
        if (lines == null || lines.isEmpty()) return ctx -> {};
        checkCacheEpoch();
        var cached = eventCache.get(lines);
        if (cached != null) return cached;
        var compiled = eventParser.parseList(lines);
        if (eventCache.size() >= MAX_CACHED) eventCache.clear();
        eventCache.put(java.util.List.copyOf(lines), compiled);
        return compiled;
    }

    /** The compiled form of a condition list (all must pass), compiled once per content. */
    public org.nakii.valmora.api.scripting.Condition conditionsCached(java.util.List<String> lines) {
        if (lines == null || lines.isEmpty()) return ctx -> true;
        checkCacheEpoch();
        var cached = conditionCache.get(lines);
        if (cached != null) return cached;
        org.nakii.valmora.api.scripting.Condition compiled = conditionParser.parseList(lines);
        if (conditionCache.size() >= MAX_CACHED) conditionCache.clear();
        conditionCache.put(java.util.List.copyOf(lines), compiled);
        return compiled;
    }

    /**
     * Runs an action list from its raw strings via the cache; a {@code condition} stop is normal,
     * any other failure is logged once per {@code source} instead of escaping.
     */
    public boolean runCached(java.util.List<String> lines, org.nakii.valmora.api.execution.ExecutionContext ctx,
                             org.nakii.valmora.infrastructure.config.diag.ConfigSource source) {
        if (lines == null || lines.isEmpty()) return true;
        return org.nakii.valmora.module.script.compile.ScriptRunner.run(compileCached(lines), ctx, source);
    }

    private void checkCacheEpoch() {
        int epoch = org.nakii.valmora.module.script.compile.ScriptEpoch.current();
        if (epoch != cacheEpoch) {
            eventCache.clear();
            conditionCache.clear();
            cacheEpoch = epoch;
        }
    }

    /**
     * Checks that can only run once every module is enabled — e.g. event names used in scripts that
     * no module registered. Called by {@code ModuleManager} at the end of each load pass.
     */
    public void runDeferredChecks(org.nakii.valmora.infrastructure.config.diag.DiagnosticSink sink) {
        if (eventParser != null) eventParser.runDeferred(sink);
        scriptChecks.report(variableProviderRegistry.getKeys(), sink);
    }

    /** @return the shared pipeline dispatch bus (see {@link HookBus}'s class doc). */
    public HookBus getHookBus() {
        return hookBus;
    }
}
