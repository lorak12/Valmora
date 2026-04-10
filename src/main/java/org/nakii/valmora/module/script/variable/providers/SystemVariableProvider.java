package org.nakii.valmora.module.script.variable.providers;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Handles system-related variables: $system.time$.
 */
public class SystemVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "system";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;

        String key = path[0];
        if (key.equalsIgnoreCase("time")) {
            return System.currentTimeMillis();
        }

        return null;
    }
}
