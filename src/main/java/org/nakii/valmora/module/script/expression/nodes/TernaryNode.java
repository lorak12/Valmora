package org.nakii.valmora.module.script.expression.nodes;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Expression;

/**
 * Expression node representing ternary operator (condition ? trueVal : falseVal).
 */
public record TernaryNode(Expression condition, Expression trueVal, Expression falseVal) implements Expression {

    @Override
    public Object evaluate(ExecutionContext context) {
        Object result = condition.evaluate(context);
        boolean isTrue = result instanceof Boolean b && b;
        return isTrue ? trueVal.evaluate(context) : falseVal.evaluate(context);
    }
}
