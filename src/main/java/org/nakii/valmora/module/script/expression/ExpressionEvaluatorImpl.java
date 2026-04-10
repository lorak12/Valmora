package org.nakii.valmora.module.script.expression;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.ExpressionEvaluator;
import org.nakii.valmora.module.script.ScriptModule;

/**
 * Default implementation of ExpressionEvaluator.
 */
public class ExpressionEvaluatorImpl implements ExpressionEvaluator {

    private final ScriptModule module;

    public ExpressionEvaluatorImpl(ScriptModule module) {
        this.module = module;
    }

    @Override
    public Object evaluate(String rawExpression, ExecutionContext context) {
        return module.getExpressionParser().parse(rawExpression).evaluate(context);
    }
}
