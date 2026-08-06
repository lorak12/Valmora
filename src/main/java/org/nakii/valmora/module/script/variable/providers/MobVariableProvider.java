package org.nakii.valmora.module.script.variable.providers;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Exposes {@code $mob.*$} variables attached to the context by callers that know the victim's
 * Valmora mob identity — currently {@code MobDeathListener} sets {@code "mob:id"} and
 * {@code "mob:level"} before running the {@code combat:on_death} pipeline point (see
 * docs/COMBAT_PIPELINE_ANALYSIS.md). Same generic key-value pattern as {@link DamageVariableProvider}.
 */
public class MobVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "mob";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;
        return context.get("mob:" + path[0]);
    }
}
