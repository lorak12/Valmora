package org.nakii.valmora.api.scripting;

import org.nakii.valmora.api.execution.ExecutionContext;

/**
 * Represents a compiled condition that can be evaluated to a boolean value.
 */
public interface Condition {

    /**
     * Evaluates this condition within the given context.
     * @param context the execution context
     * @return true if the condition is met, false otherwise
     */
    boolean evaluate(ExecutionContext context);
}
