package org.nakii.valmora.module.script.variable.providers;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Exposes {@code $death.*$} variables published by {@code death_pipeline.yml}'s flows
 * ({@code DeathFlows}): {@code keep_inventory}, {@code keep_experience}, {@code zone} while a
 * player dies, and {@code is_bed_spawn}/{@code is_anchor_spawn} while they respawn. Same generic
 * key-value pattern as {@link FishingVariableProvider}.
 */
public class DeathVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "death";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;
        return context.get("death:" + path[0]);
    }
}
