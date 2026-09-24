package org.nakii.valmora.module.script.variable.providers;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Exposes {@code $mob.*$} variables attached to the context by callers that know a Valmora mob's
 * identity — {@code MobDeathListener} sets {@code "mob:id"} and {@code "mob:level"} before the
 * {@code combat:on_death} pipeline point, and {@code BossController} sets {@code id}, {@code level},
 * {@code health}, {@code max_health}, {@code health_percent}, {@code ability_id} and
 * {@code ability_trigger} before {@code mob:pre_ability}/{@code post_ability}. Same generic
 * key-value pattern as {@link DamageVariableProvider}.
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
