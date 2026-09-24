package org.nakii.valmora.module.script.expression.nodes;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.module.script.expression.FunctionRegistry;

import java.util.List;

/**
 * Expression node representing a function call such as {@code floor(x)}, {@code min(a, b, ...)}
 * or {@code contains($name$, "Bob")} — see {@link FunctionRegistry} for the list. An unknown
 * function evaluates to 0 (and was already reported when the expression was parsed).
 */
public record FunctionNode(String name, List<Expression> args) implements Expression {

    @Override
    public Object evaluate(ExecutionContext context) {
        FunctionRegistry.Fn fn = FunctionRegistry.get(name);
        if (fn == null) return 0.0;
        Object[] values = new Object[args.size()];
        for (int i = 0; i < args.size(); i++) {
            values[i] = args.get(i).evaluate(context);
        }
        if (!fn.accepts(values.length)) {
            // Pad missing numeric arguments with 0 like the old implementation did.
            Object[] padded = new Object[Math.max(fn.minArgs(), Math.min(values.length, fn.maxArgs()))];
            for (int i = 0; i < padded.length; i++) padded[i] = i < values.length ? values[i] : 0.0;
            values = padded;
        }
        return fn.impl().apply(values);
    }
}
