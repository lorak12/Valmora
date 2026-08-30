package org.nakii.valmora.module.script.event;

import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;

/**
 * Shared helper for {@link EventFactory} implementations whose DSL args may be either a literal
 * number or a {@code $}-containing formula string — extracted from {@code VariableEvent}'s
 * inline convention so {@code heal}/{@code damage}/{@code strike_lightning} (and any future event)
 * don't each duplicate it.
 */
public final class EventArgs {

    private EventArgs() {
    }

    /** Resolves a raw event-arg token to a double: a formula if it contains {@code $}, evaluated
     *  against {@code context} via the expression engine; otherwise a plain {@code Double.parseDouble}
     *  (defaulting to {@code 0.0} on anything unparseable, matching every other event's convention). */
    public static double resolveDouble(String raw, ExecutionContext context) {
        if (raw == null) return 0.0;
        if (raw.contains("$")) {
            Object evaluated = ValmoraAPI.getInstance().getScriptModule().getExpressionEvaluator().evaluate(raw, context);
            if (evaluated instanceof Number n) return n.doubleValue();
            try {
                return Double.parseDouble(String.valueOf(evaluated));
            } catch (NumberFormatException | NullPointerException e) {
                return 0.0;
            }
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
