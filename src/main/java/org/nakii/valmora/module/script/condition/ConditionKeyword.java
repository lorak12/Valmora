package org.nakii.valmora.module.script.condition;

import org.nakii.valmora.api.scripting.Condition;

/**
 * A condition written as {@code <keyword> <args...>} (e.g. {@code tag vip}, {@code health 10}).
 * Registered on the {@link ConditionParser}; add-ons can add their own with
 * {@link ConditionParser#registerKeyword}.
 */
public interface ConditionKeyword {

    /** The keyword, lowercase (e.g. {@code "tag"}). */
    String name();

    int minArgs();

    int maxArgs();

    /** e.g. {@code "health <min>"} — shown when the arguments are wrong. */
    String usage();

    /**
     * @param argText everything after the keyword, trimmed
     * @param args    {@code argText} split on whitespace
     * @return the condition, or null when the arguments are malformed (the caller reports it and
     *         falls back to treating the text as an expression, as the parser always did)
     */
    Condition compile(String argText, String[] args);

    static ConditionKeyword of(String name, int min, int max, String usage,
                               java.util.function.BiFunction<String, String[], Condition> compile) {
        return new ConditionKeyword() {
            @Override public String name() { return name; }
            @Override public int minArgs() { return min; }
            @Override public int maxArgs() { return max; }
            @Override public String usage() { return usage; }
            @Override public Condition compile(String argText, String[] args) { return compile.apply(argText, args); }
        };
    }
}
