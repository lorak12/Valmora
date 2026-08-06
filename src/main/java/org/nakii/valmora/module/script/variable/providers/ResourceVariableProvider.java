package org.nakii.valmora.module.script.variable.providers;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Exposes {@code $resource.*$} variables attached by {@code ResourceManager} before running the
 * {@code resource:pre_break} / {@code resource:post_break} pipeline points (see
 * docs/COMBAT_PIPELINE_ANALYSIS.md) — currently {@code material} and {@code stage}. Same generic
 * key-value pattern as {@link DamageVariableProvider} / {@link MobVariableProvider}.
 */
public class ResourceVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "resource";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;
        return context.get("resource:" + path[0]);
    }
}
