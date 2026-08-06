package org.nakii.valmora.module.script.variable.providers;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Exposes {@code $curve.level$} to XP-curve formulas loaded from {@code skills/xp_curves.yml}
 * (Phase 3.2 — see docs/REFACTOR/PROGRESS.md). Reads from the Phase 1.2
 * {@link ExecutionContext} key-value store — {@code XpCurveRegistry} attaches
 * {@code "curve:level"} before evaluating a curve's formula once per level at load time.
 */
public class CurveVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "curve";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;
        return context.get("curve:" + path[0]);
    }
}
