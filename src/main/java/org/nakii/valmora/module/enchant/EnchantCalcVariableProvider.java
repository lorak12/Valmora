package org.nakii.valmora.module.enchant;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Resolves {@code $calc.<name>$} — one entry per key declared in an enchant's YAML
 * {@code variables:} block. Each formula (referencing {@code $level$} — really {@code
 * $enchant.level$} — and anything else in scope) is evaluated once per dispatch by
 * {@link EnchantCombatHook}/{@link EnchantDispatcher} and attached as {@code "calc:<name>"}; this
 * provider is just a thin read of that attachment, keeping {@code $calc.bonus$}-style access from
 * the user's original spec while reusing the generic attachment-map mechanism rather than
 * inventing per-enchant provider state.
 */
public class EnchantCalcVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "calc";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;
        return context.get("calc:" + path[0].toLowerCase());
    }
}
