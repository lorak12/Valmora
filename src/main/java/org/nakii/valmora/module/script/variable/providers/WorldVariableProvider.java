package org.nakii.valmora.module.script.variable.providers;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Handles world-related variables: $world.name$.
 */
public class WorldVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "world";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;

        String key = path[0];
        if (key.equalsIgnoreCase("name")) {
            return context.getLocation().getWorld().getName();
        }

        if (key.equalsIgnoreCase("dimension")) {
            return context.getLocation().getWorld().getEnvironment().name();
        }

        return null;
    }
}
