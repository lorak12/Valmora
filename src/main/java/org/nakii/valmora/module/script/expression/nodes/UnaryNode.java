package org.nakii.valmora.module.script.expression.nodes;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.module.script.expression.FunctionRegistry;

/** {@code !x} / {@code not x} (boolean negation) and {@code -x} (numeric negation). */
public record UnaryNode(String op, Expression operand) implements Expression {

    @Override
    public Object evaluate(ExecutionContext context) {
        Object v = operand.evaluate(context);
        if (op.equals("-")) return -FunctionRegistry.num(v);
        return !LogicalNode.truthy(v);
    }
}
