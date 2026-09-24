package org.nakii.valmora.module.script.expression;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.api.scripting.ExpressionEvaluator;
import org.nakii.valmora.module.script.ScriptModule;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of ExpressionEvaluator. Parsed expressions are cached by their text
 * (bounded; a new evaluator — i.e. every script-module enable — starts empty), so formulas
 * evaluated on hot paths (damage, event arguments, mob scaling) aren't re-parsed per call.
 */
public class ExpressionEvaluatorImpl implements ExpressionEvaluator {

    private static final int MAX_CACHED = 2048;

    private final ScriptModule module;
    private final Map<String, Expression> cache = new ConcurrentHashMap<>();

    public ExpressionEvaluatorImpl(ScriptModule module) {
        this.module = module;
    }

    @Override
    public Object evaluate(String rawExpression, ExecutionContext context) {
        return compile(rawExpression).evaluate(context);
    }

    /** The parsed form of {@code rawExpression}, from the cache when possible. */
    public Expression compile(String rawExpression) {
        if (rawExpression == null) return module.getExpressionParser().parse(null);
        Expression cached = cache.get(rawExpression);
        if (cached != null) return cached;
        Expression parsed = module.getExpressionParser().parse(rawExpression);
        if (cache.size() >= MAX_CACHED) cache.clear();
        cache.put(rawExpression, parsed);
        return parsed;
    }
}
