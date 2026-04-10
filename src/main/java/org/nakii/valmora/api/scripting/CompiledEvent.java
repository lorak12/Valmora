package org.nakii.valmora.api.scripting;

import org.nakii.valmora.api.execution.ExecutionContext;

/**
 * Represents a compiled event that can be executed to perform an action.
 */
public interface CompiledEvent {

    /**
     * Executes this event within the given context.
     * @param context the execution context
     */
    void execute(ExecutionContext context);
}
