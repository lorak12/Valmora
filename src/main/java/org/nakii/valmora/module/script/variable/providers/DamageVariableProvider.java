package org.nakii.valmora.module.script.variable.providers;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Exposes {@code $dmg.*$} variables to the combat formula expressions loaded from
 * {@code damage_formula.yml} (Phase 2.2 — see docs/REFACTOR/PROGRESS.md).
 *
 * <p>Reads from the generic per-invocation key-value store added to {@link ExecutionContext} in
 * Phase 1.2 — {@link org.nakii.valmora.module.combat.DamageCalculator} attaches
 * {@code "dmg:base_damage"}, {@code "dmg:strength"}, {@code "dmg:crit_damage"},
 * {@code "dmg:defense"}, etc. before evaluating each compiled formula, so no second variable
 * resolution mechanism is needed for combat math.
 */
public class DamageVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "dmg";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;
        return context.get("dmg:" + path[0]);
    }
}
