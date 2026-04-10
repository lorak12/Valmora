package org.nakii.valmora.api.scripting;

import org.nakii.valmora.api.execution.ExecutionContext;

/**
 * Represents a compiled expression (AST) that can be evaluated to a value.
 */
public interface Expression {

    /**
     * Evaluates this expression within the given context.
     * @param context the execution context
     * @return the result of the evaluation
     */
    Object evaluate(ExecutionContext context);
}
