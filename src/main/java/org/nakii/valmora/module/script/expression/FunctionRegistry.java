package org.nakii.valmora.module.script.expression;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Functions callable from expressions ({@code floor($x$)}, {@code contains($name$, "Bob")}, ...).
 * Each declares how many arguments it takes, so a wrong call is reported when the script loads
 * instead of silently evaluating to 0. Add-ons can register more with {@link #register}.
 */
public final class FunctionRegistry {

    /** One function: {@code impl} receives the evaluated arguments (numbers are Doubles). */
    public record Fn(String name, int minArgs, int maxArgs, String usage, Function<Object[], Object> impl) {
        public boolean accepts(int count) {
            return count >= minArgs && count <= maxArgs;
        }
    }

    private static final Map<String, Fn> FUNCTIONS = new ConcurrentHashMap<>();

    static {
        // math
        num1("floor", Math::floor);
        num1("ceil", Math::ceil);
        num1("round", d -> (double) Math.round(d));
        num1("abs", Math::abs);
        num1("sqrt", Math::sqrt);
        num1("log10", Math::log10);
        num1("log", Math::log);
        num1("sign", Math::signum);
        num1("sin", Math::sin);
        num1("cos", Math::cos);
        register(new Fn("pow", 2, 2, "pow(base, exponent)", a -> Math.pow(num(a[0]), num(a[1]))));
        register(new Fn("min", 1, Integer.MAX_VALUE, "min(a, b, ...)", a -> reduce(a, true)));
        register(new Fn("max", 1, Integer.MAX_VALUE, "max(a, b, ...)", a -> reduce(a, false)));
        register(new Fn("clamp", 3, 3, "clamp(value, min, max)",
                a -> Math.max(num(a[1]), Math.min(num(a[2]), num(a[0])))));
        register(new Fn("random", 0, 2, "random() | random(max) | random(min, max)", a -> {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            if (a.length == 0) return r.nextDouble();
            double lo = a.length == 2 ? num(a[0]) : 0.0;
            double hi = a.length == 2 ? num(a[1]) : num(a[0]);
            if (hi <= lo) return lo;
            return lo + r.nextDouble() * (hi - lo);
        }));
        register(new Fn("randint", 2, 2, "randint(min, max) — inclusive whole number", a -> {
            long lo = Math.round(num(a[0]));
            long hi = Math.round(num(a[1]));
            if (hi <= lo) return (double) lo;
            return (double) ThreadLocalRandom.current().nextLong(lo, hi + 1);
        }));
        // text
        register(new Fn("contains", 2, 2, "contains(text, part)", a -> str(a[0]).contains(str(a[1]))));
        register(new Fn("startsWith", 2, 2, "startsWith(text, prefix)", a -> str(a[0]).startsWith(str(a[1]))));
        register(new Fn("endsWith", 2, 2, "endsWith(text, suffix)", a -> str(a[0]).endsWith(str(a[1]))));
        register(new Fn("lower", 1, 1, "lower(text)", a -> str(a[0]).toLowerCase(Locale.ROOT)));
        register(new Fn("upper", 1, 1, "upper(text)", a -> str(a[0]).toUpperCase(Locale.ROOT)));
        register(new Fn("trim", 1, 1, "trim(text)", a -> str(a[0]).trim()));
        register(new Fn("len", 1, 1, "len(text)", a -> (double) str(a[0]).length()));
        register(new Fn("replace", 3, 3, "replace(text, find, with)", a -> str(a[0]).replace(str(a[1]), str(a[2]))));
        // conversion
        register(new Fn("str", 1, 1, "str(value)", a -> str(a[0])));
        register(new Fn("num", 1, 1, "num(value) — 0 when not a number", a -> num(a[0])));
        register(new Fn("isnull", 1, 1, "isnull(value)", a -> a[0] == null || "null".equals(a[0])));
        register(new Fn("default", 2, 2, "default(value, fallback) — fallback when value is null",
                a -> a[0] == null || "null".equals(a[0]) ? a[1] : a[0]));
    }

    private FunctionRegistry() {}

    /** Registers (or replaces) a function. Names are case-insensitive. */
    public static void register(Fn fn) {
        FUNCTIONS.put(fn.name().toLowerCase(Locale.ROOT), fn);
    }

    public static Fn get(String name) {
        return name == null ? null : FUNCTIONS.get(name.toLowerCase(Locale.ROOT));
    }

    public static Collection<String> names() {
        return FUNCTIONS.values().stream().map(Fn::name).toList();
    }

    private static void num1(String name, Function<Double, Double> f) {
        register(new Fn(name, 1, 1, name + "(x)", a -> f.apply(num(a[0]))));
    }

    private static double reduce(Object[] values, boolean min) {
        if (values.length == 0) return 0.0;
        double acc = num(values[0]);
        for (int i = 1; i < values.length; i++) {
            acc = min ? Math.min(acc, num(values[i])) : Math.max(acc, num(values[i]));
        }
        return acc;
    }

    /** Number coercion used by every numeric function: numbers, numeric text, booleans (1/0); else 0. */
    public static double num(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof Boolean b) return b ? 1.0 : 0.0;
        if (o instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }

    /** Text form of a value: whole numbers without {@code .0}, null as empty text. */
    public static String str(Object o) {
        if (o == null) return "";
        if (o instanceof Double d && d == Math.rint(d) && !d.isInfinite() && Math.abs(d) < 1e15) {
            return String.valueOf(d.longValue());
        }
        if (o instanceof Float f && f == Math.rint(f) && !f.isInfinite()) {
            return String.valueOf((long) f.floatValue());
        }
        return String.valueOf(o);
    }

    /** For tests. */
    static List<String> sortedNames() {
        return FUNCTIONS.keySet().stream().sorted().toList();
    }
}
