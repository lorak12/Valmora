package org.nakii.valmora.module.script.variable.providers;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Exposes {@code $item.*$} variables attached by {@code AbilityExecutor} before running the
 * {@code item:pre_ability} / {@code item:post_ability} pipeline points (see
 * docs/VALMORA_DOCUMENTATION.md §39) — currently {@code ability_id} and {@code ability_trigger}.
 * Same generic key-value pattern as {@link DamageVariableProvider} / {@link MobVariableProvider}.
 */
public class ItemAbilityVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "item";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;
        return context.get("item:" + path[0]);
    }
}
