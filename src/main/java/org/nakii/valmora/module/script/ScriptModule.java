package org.nakii.valmora.module.script;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.api.registry.Registry;
import org.nakii.valmora.api.registry.SimpleRegistry;
import org.nakii.valmora.module.script.variable.VariableProvider;
import org.nakii.valmora.module.script.variable.VariableResolverImpl;
import org.nakii.valmora.module.script.variable.providers.PlayerVariableProvider;
import org.nakii.valmora.module.script.variable.providers.ServerVariableProvider;
import org.nakii.valmora.module.script.variable.providers.SystemVariableProvider;
import org.nakii.valmora.module.script.variable.providers.WorldVariableProvider;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.impl.GiveEvent;
import org.nakii.valmora.module.script.event.impl.TagEvent;
import org.nakii.valmora.module.script.event.impl.VariableEvent;
import org.nakii.valmora.module.script.expression.ExpressionParser;
import org.nakii.valmora.module.script.expression.ExpressionEvaluatorImpl;
import org.nakii.valmora.api.scripting.ExpressionEvaluator;
import org.nakii.valmora.module.script.condition.ConditionParser;
import org.nakii.valmora.module.script.event.EventParser;
import org.nakii.valmora.api.scripting.VariableResolver;

import org.nakii.valmora.module.script.variable.providers.ParamVariableProvider;
import org.nakii.valmora.module.script.variable.providers.PropVariableProvider;

/**
 * Core module for the Valmora Scripting System.
 * Manages variables, expressions, conditions, and events.
 */
public class ScriptModule implements ReloadableModule {

    private final Valmora plugin;
    private final Registry<VariableProvider> variableProviderRegistry = new SimpleRegistry<>();
    private final Registry<EventFactory> eventFactoryRegistry = new SimpleRegistry<>();

    private VariableResolver variableResolver;
    private ExpressionParser expressionParser;
    private ExpressionEvaluator expressionEvaluator;
    private ConditionParser conditionParser;
    private EventParser eventParser;

    public ScriptModule(Valmora plugin) {
        this.plugin = plugin;
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

        // Register default events
        registerEvent(new GiveEvent());
        registerEvent(new VariableEvent());
        registerEvent(new TagEvent());
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
}
