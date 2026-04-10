package org.nakii.valmora.module.script.expression.nodes;

import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Expression;

/**
 * Expression node representing a dynamic variable resolved at runtime.
 */
public record VariableNode(String path) implements Expression {

    @Override
    public Object evaluate(ExecutionContext context) {
        return ValmoraAPI.getInstance().getScriptModule().getVariableResolver().resolve(path, context);
    }
}
