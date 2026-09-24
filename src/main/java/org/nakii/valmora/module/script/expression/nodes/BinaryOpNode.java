package org.nakii.valmora.module.script.expression.nodes;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Expression;

import java.util.Objects;

/**
 * Expression node representing binary operations (+, -, *, /, ==, !=, >, <, etc.).
 */
public record BinaryOpNode(Expression left, String op, Expression right) implements Expression {

    @Override
    public Object evaluate(ExecutionContext context) {
        Object l = left.evaluate(context);
        Object r = right.evaluate(context);
        return evaluateOp(l, r);
    }

    private Object evaluateOp(Object l, Object r) {
        // Logical operators coerce both sides to booleans.
        if (op.equals("and") || op.equals("&&")) return truthy(l) && truthy(r);
        if (op.equals("or") || op.equals("||")) return truthy(l) || truthy(r);

        // Arithmetic and ordering treat a missing value as 0 and a numeric string as its number
        // (2026-09-24) — previously `$player.var.unset$ + 1` or `"5" > 3` evaluated to null, so a
        // condition on a never-set counter silently always failed. Equality keeps null distinct
        // (`$x$ == null` must still detect "unset"), but still compares "5" == 5 numerically.
        boolean equality = op.equals("==") || op.equals("!=");
        // `null` in an expression is a real null; a few providers (e.g. $gui.input.<id>.material$
        // for an empty slot) return the text "null" instead, so treat that the same way.
        if ("null".equals(l)) l = null;
        if ("null".equals(r)) r = null;
        Double ln0 = asNumber(l, !equality);
        Double rn0 = asNumber(r, !equality);
        if (ln0 != null && rn0 != null && (l instanceof Number || r instanceof Number)) {
            l = ln0;
            r = rn0;
        }
        if (l instanceof Number ln && r instanceof Number rn) {
            double leftVal = ln.doubleValue();
            double rightVal = rn.doubleValue();
            return switch (op) {
                case "+" -> leftVal + rightVal;
                case "-" -> leftVal - rightVal;
                case "*" -> leftVal * rightVal;
                case "/" -> rightVal != 0 ? leftVal / rightVal : 0.0;
                case ">" -> leftVal > rightVal;
                case "<" -> leftVal < rightVal;
                case ">=" -> leftVal >= rightVal;
                case "<=" -> leftVal <= rightVal;
                case "==" -> Math.abs(leftVal - rightVal) < 0.0001;
                case "!=" -> Math.abs(leftVal - rightVal) >= 0.0001;
                default -> null;
            };
        }

        // String and generic comparison
        if (op.equals("==")) return Objects.equals(l, r);
        if (op.equals("!=")) return !Objects.equals(l, r);

        return null;
    }

    /** {@code o} as a number: itself, a numeric string, or — when {@code nullAsZero} — 0 for null. */
    private static Double asNumber(Object o, boolean nullAsZero) {
        if (o instanceof Number n) return n.doubleValue();
        if (o == null) return nullAsZero ? 0.0 : null;
        if (o instanceof String str) {
            try {
                return Double.parseDouble(str.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static boolean truthy(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.doubleValue() != 0.0;
        return o != null;
    }
}
