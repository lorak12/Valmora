package org.nakii.valmora.module.script.variable.providers;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Exposes {@code $fishing.*$} variables attached by {@code FishingManager} before running the
 * {@code fishing:pre_catch} / {@code fishing:post_catch} pipeline points (see
 * docs/COMBAT_PIPELINE_ANALYSIS.md) — currently {@code table}, {@code sea_creature}, {@code item},
 * {@code amount}. Same generic key-value pattern as {@link DamageVariableProvider}.
 */
public class FishingVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "fishing";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;
        return context.get("fishing:" + path[0]);
    }
}
