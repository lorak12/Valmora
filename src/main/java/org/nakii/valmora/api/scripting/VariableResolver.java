package org.nakii.valmora.api.scripting;

import org.nakii.valmora.api.execution.ExecutionContext;

/**
 * Interface that allows resolving variable paths into objects.
 */
public interface VariableResolver {

    /**
     * Resolves a variable path into an object using the provided context.
     * @param path specific path (e.g., "$player.stat.HEALTH$")
     * @param context the context to use for resolution
     * @return the resolved object, or null if not found or invalid
     */
    Object resolve(String path, ExecutionContext context);
}
