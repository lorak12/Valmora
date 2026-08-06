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

    public ScriptModule(Valmora plugin) {
        this.plugin = plugin;
        this.hookBus = new HookBus(plugin);
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("Initializing Script Engine...");
        
        this.variableResolver = new VariableResolverImpl(this);
        this.expressionParser = new ExpressionParser();
        this.expressionEvaluator = new ExpressionEvaluatorImpl(this);
        this.conditionParser = new ConditionParser(this.expressionParser);
        this.eventParser = new EventParser(this);

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
    }

    public void registerProvider(VariableProvider provider) {
        variableProviderRegistry.register(provider.getNamespace(), provider);
    }

    public void registerEvent(EventFactory factory) {
        eventFactoryRegistry.register(factory.getName(), factory);
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Disabling Script Engine...");
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

    /** @return the shared pipeline dispatch bus (see {@link HookBus}'s class doc). */
    public HookBus getHookBus() {
        return hookBus;
    }
}
