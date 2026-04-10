package org.nakii.valmora.module.script.variable;

import org.nakii.valmora.api.execution.ExecutionContext;

/**
 * Provides access to variables within a specific namespace (e.g., "player", "sys").
 */
public interface VariableProvider {

    /**
     * @return the namespace handled by this provider (e.g., "player")
     */
    String getNamespace();

    /**
     * Resolves a value based on the remaining path.
     * @param path the path parts (excluding the namespace)
     * @param context the execution context
     * @return the resolved object or null
     */
    Object resolve(String[] path, ExecutionContext context);
}
