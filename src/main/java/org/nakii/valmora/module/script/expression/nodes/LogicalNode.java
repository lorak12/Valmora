package org.nakii.valmora.module.script.expression.nodes;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Expression;

/**
 * {@code and} / {@code or} with short-circuiting: the right side is only evaluated when it can
 * change the result (so {@code $x$ != null and $x$ > 5} never evaluates the comparison on null,
 * and an expensive variable on the right isn't resolved needlessly).
 */
public record LogicalNode(Expression left, boolean and, Expression right) implements Expression {

    @Override
    public Object evaluate(ExecutionContext context) {
        boolean l = truthy(left.evaluate(context));
        if (and && !l) return false;
        if (!and && l) return true;
        return truthy(right.evaluate(context));
    }

    static boolean truthy(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.doubleValue() != 0.0;
        if ("false".equals(o) || "null".equals(o)) return false;
        return o != null;
    }
}
