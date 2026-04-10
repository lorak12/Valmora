package org.nakii.valmora.module.script.expression.nodes;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Expression;

/**
 * Expression node representing a constant value (number, string, boolean).
 */
public record LiteralNode(Object value) implements Expression {

    @Override
    public Object evaluate(ExecutionContext context) {
        return value;
    }
}
