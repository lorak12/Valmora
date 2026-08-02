package org.nakii.valmora.module.script.expression.nodes;

import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Expression;

import java.util.List;

/**
 * Expression node representing a math function call such as {@code floor(x)},
 * {@code min(a, b, ...)} or {@code log10(x)}. Arguments are evaluated to doubles.
 */
public record FunctionNode(String name, List<Expression> args) implements Expression {

    @Override
    public Object evaluate(ExecutionContext context) {
        double[] values = new double[args.size()];
        for (int i = 0; i < args.size(); i++) {
            values[i] = toDouble(args.get(i).evaluate(context));
        }

        return switch (name.toLowerCase()) {
            case "floor" -> Math.floor(arg(values, 0));
            case "ceil"  -> Math.ceil(arg(values, 0));
            case "round" -> (double) Math.round(arg(values, 0));
            case "abs"   -> Math.abs(arg(values, 0));
            case "sqrt"  -> Math.sqrt(arg(values, 0));
            case "log10" -> Math.log10(arg(values, 0));
            case "log"   -> Math.log(arg(values, 0));
            case "pow"   -> Math.pow(arg(values, 0), arg(values, 1));
            case "min"   -> reduce(values, true);
            case "max"   -> reduce(values, false);
            default      -> 0.0;
        };
    }

    private static double arg(double[] values, int index) {
        return index < values.length ? values[index] : 0.0;
    }

    private static double reduce(double[] values, boolean min) {
        if (values.length == 0) return 0.0;
        double acc = values[0];
        for (int i = 1; i < values.length; i++) {
            acc = min ? Math.min(acc, values[i]) : Math.max(acc, values[i]);
        }
        return acc;
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof Boolean b) return b ? 1.0 : 0.0;
        if (o instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }
}
