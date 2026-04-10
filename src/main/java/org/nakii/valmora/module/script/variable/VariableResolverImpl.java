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
                .orElse(null);
    }
}
