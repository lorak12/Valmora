package org.nakii.valmora.module.script.variable;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.module.script.ScriptModule;

/**
 * Default implementation of VariableResolver.
 * Handles parsing of "$namespace.path$" and delegating to providers.
 */
public class VariableResolverImpl implements VariableResolver {

    private final ScriptModule module;

    public VariableResolverImpl(ScriptModule module) {
        this.module = module;
    }

    @Override
    public Object resolve(String path, ExecutionContext context) {
        if (path == null || path.isEmpty()) return null;

        // Strip $ if present
        String cleanPath = path;
        if (cleanPath.startsWith("$") && cleanPath.endsWith("$")) {
            cleanPath = cleanPath.substring(1, cleanPath.length() - 1);
        }

        String[] parts = cleanPath.split("\\.");
        if (parts.length < 1) return null;

        String namespace = parts[0];
        String[] remainingPath = new String[parts.length - 1];
        System.arraycopy(parts, 1, remainingPath, 0, remainingPath.length);

        return module.getVariableProviderRegistry().get(namespace)
                .map(provider -> provider.resolve(remainingPath, context))
                .orElseGet(() -> {
                    if (context instanceof org.nakii.valmora.module.gui.GuiExecutionContext guiCtx) {
                        Object loopVar = guiCtx.getLoopVars().get(namespace);
                        if (loopVar != null) {
                            Object current = loopVar;
                            for (String key : remainingPath) {
                                if (current == null) break;
                                if (current instanceof java.util.Map<?, ?> map) {
                                    current = map.get(key);
                                } else if (current instanceof org.bukkit.configuration.ConfigurationSection section) {
                                    current = section.get(key);
                                } else {
                                    current = null;
                                    break;
                                }
                            }
                            if (current != null || remainingPath.length == 0) return current;
                        }
                    }
                    
                    // Legacy: fall back to ConfigurationSection params (useful for simple dynamic execution contexts)
                    if (context.getParams() != null) {
                        Object cfgParam = context.getParams().get(namespace);
                        if (cfgParam != null) {
                            Object current = cfgParam;
                            for (String key : remainingPath) {
                                if (current == null) break;
                                if (current instanceof java.util.Map<?, ?> map) {
                                    current = map.get(key);
                                } else if (current instanceof org.bukkit.configuration.ConfigurationSection section) {
                                    current = section.get(key);
                                } else {
                                    current = null;
                                    break;
                                }
                            }
                            if (current != null || remainingPath.length == 0) return current;
                        }
                    }
                    return null;
                });
    }
}
