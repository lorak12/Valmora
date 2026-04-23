package org.nakii.valmora.module.script.variable.providers;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;
import java.util.Map;

public class ParamVariableProvider implements VariableProvider {
    @Override public String getNamespace() { return "param"; }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;
        if (context.getParams() != null) {
            Object current = context.getParams().get(path[0]);
            
            // Loop through remaining path for deep nesting
            for (int i = 1; i < path.length; i++) {
                if (current == null) return null;
                if (current instanceof Map<?, ?> map) {
                    current = map.get(path[i]);
                } else if (current instanceof org.bukkit.configuration.ConfigurationSection section) {
                    current = section.get(path[i]);
                } else {
                    return null;
                }
            }
            return current;
        }
        return null;
    }
}