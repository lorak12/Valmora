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
}
