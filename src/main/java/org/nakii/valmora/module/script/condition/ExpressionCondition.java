package org.nakii.valmora.module.script.condition;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.api.scripting.Expression;

/**
 * Condition that evaluates a boolean expression.
 */
public record ExpressionCondition(Expression expression) implements Condition {

    @Override
    public boolean evaluate(ExecutionContext context) {
        Object result = expression.evaluate(context);
        return result instanceof Boolean b && b;
    }
}
