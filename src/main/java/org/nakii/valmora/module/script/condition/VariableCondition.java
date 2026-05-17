package org.nakii.valmora.module.script.condition;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Condition;

public record VariableCondition(String path, String operator, String value) implements Condition {

    @Override
    public boolean evaluate(ExecutionContext context) {
        Object resolved = context.getVariableResolver().resolve("$" + path + "$", context);
        String lhs = resolved != null ? resolved.toString() : "null";
        return compare(lhs, operator, value);
    }

    private boolean compare(String lhs, String op, String rhs) {
        // Try numeric comparison first
        try {
            double l = Double.parseDouble(lhs);
            double r = Double.parseDouble(rhs);
            return switch (op) {
                case "==" -> l == r;
                case "!=" -> l != r;
                case ">"  -> l > r;
                case "<"  -> l < r;
                case ">=" -> l >= r;
                case "<=" -> l <= r;
                default   -> false;
            };
        } catch (NumberFormatException ignored) {}
        // Fall back to string comparison
        return switch (op) {
            case "==" -> lhs.equals(rhs);
            case "!=" -> !lhs.equals(rhs);
            default   -> false;
        };
    }
}
