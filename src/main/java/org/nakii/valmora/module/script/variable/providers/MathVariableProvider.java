package org.nakii.valmora.module.script.variable.providers;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

/**
 * Exposes {@code $math.*$} — currently just {@code $math.random$}, a fresh {@code [0, 1)} double
 * per resolution, for RNG-driven pipeline stage conditions (see docs/COMBAT_PIPELINE_ANALYSIS.md
 * Example 4's "chaos" patterns, e.g. {@code "condition $math.random$ < 0.3"}).
 */
public class MathVariableProvider implements VariableProvider {

    @Override
    public String getNamespace() {
        return "math";
    }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;
        return switch (path[0].toLowerCase()) {
            case "random" -> Math.random();
            default -> null;
        };
    }
}
