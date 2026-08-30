package org.nakii.valmora.module.enchant;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Resolves the bare {@code $level$} token used throughout enchant {@code variables:}/{@code
 * combat:} formulas (e.g. {@code "1 + (0.05 * $level$)"}) — a plain no-dot variable resolves via
 * {@code VariableResolverImpl} by treating the whole token as a namespace with an empty remaining
 * path, so this is a one-line provider registered under namespace {@code "level"} rather than
 * folded into {@link EnchantVariableProvider} (one class = one namespace). Equivalent to
 * {@code $enchant.level$}; both read the same {@code "enchant:level"} attachment.
 */
public class EnchantLevelVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "level";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        return context.get("enchant:level", 0);
    }
}
