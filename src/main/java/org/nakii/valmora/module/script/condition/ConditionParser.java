package org.nakii.valmora.module.script.condition;

import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.infrastructure.config.diag.Diagnostics;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.refs.Kinds;
import org.nakii.valmora.module.quest.points.PointCondition;
import org.nakii.valmora.module.script.expression.ExpressionParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses condition strings:
 * <ul>
 *   <li>a keyword condition — {@code tag vip}, {@code health 10}, {@code zone hub}, ... (see
 *       {@link #registerKeyword} for the list and for adding more);</li>
 *   <li>an expression — {@code $player.level$ >= 10 and $time.is_day$};</li>
 *   <li>any mix of the two combined with {@code and} / {@code or} / {@code not} / {@code !} and
 *       parentheses — {@code tag vip or (zone hub and health 10)}.</li>
 * </ul>
 * A leading {@code !} on a single condition negates it, as before. A list of conditions in YAML
 * means all of them ({@link #parseList}).
 */
public class ConditionParser {

    private final ExpressionParser expressionParser;
    private final Map<String, ConditionKeyword> keywords = new LinkedHashMap<>();

    public ConditionParser(ExpressionParser expressionParser) {
        this.expressionParser = expressionParser;
        registerBuiltins();
    }

    /** Adds (or replaces) a keyword condition. */
    public void registerKeyword(ConditionKeyword keyword) {
        keywords.put(keyword.name().toLowerCase(Locale.ROOT), keyword);
    }

    public Map<String, ConditionKeyword> getKeywords() {
        return java.util.Collections.unmodifiableMap(keywords);
    }

    private void registerBuiltins() {
        registerKeyword(ConditionKeyword.of("tag", 1, 1, "tag <tag>", (text, a) -> new TagCondition(text)));
        registerKeyword(ConditionKeyword.of("health", 1, 1, "health <min>", (text, a) -> {
            Double d = number(text);
            return d == null ? null : new HealthCondition(d);
        }));
        registerKeyword(ConditionKeyword.of("hunger", 1, 1, "hunger <min>", (text, a) -> {
            Double d = number(text);
            return d == null || d != Math.rint(d) ? null : new HungerCondition(d.intValue());
        }));
        registerKeyword(ConditionKeyword.of("location", 2, 2, "location <x;y;z;world> <radius>", (text, a) -> {
            if (a.length < 2) return null;
            Double radius = number(a[1]);
            return radius == null ? null : LocationCondition.parse(a[0], radius);
        }));
        registerKeyword(ConditionKeyword.of("zone", 1, 1, "zone <zone>", (text, a) -> {
            ref(Kinds.ZONE, text);
            return new ZoneCondition(text);
        }));
        registerKeyword(ConditionKeyword.of("block", 1, 1, "block <material>", (text, a) -> {
            BlockLookCondition c = BlockLookCondition.parse(text);
            if (c.material() == null) {
                Diagnostics.warn("block: unknown material '" + text + "' — the condition is always false",
                        org.nakii.valmora.infrastructure.config.read.ConfigReader.materialHint(text));
            }
            return c;
        }));
        registerKeyword(ConditionKeyword.of("variable", 3, 3, "variable <path> <==|!=|>|<|>=|<=> <value>", (text, a) -> {
            if (a.length < 3) return null;
            if (!List.of("==", "!=", ">", "<", ">=", "<=").contains(a[1])) return null;
            return new VariableCondition(a[0], a[1], a[2]);
        }));
        registerKeyword(ConditionKeyword.of("objective", 1, 1, "objective <objective>", (text, a) -> new ObjectiveActiveCondition(text)));
        registerKeyword(ConditionKeyword.of("quest", 2, 2, "quest <quest> <status>", (text, a) -> {
            if (a.length < 2) return null;
            ref(Kinds.QUEST, a[0]);
            return new QuestStatusCondition(a[0], a[1]);
        }));
        registerKeyword(ConditionKeyword.of("point", 2, 2, "point <category> <min>", (text, a) -> {
            if (a.length < 2) return null;
            Double d = number(a[1]);
            return d == null || d != Math.rint(d) ? null : new PointCondition(a[0], d.intValue());
        }));
    }

    /**
     * Parses a single condition string (see the class doc for the syntax).
     */
    public Condition parse(String raw) {
        if (raw == null || raw.isBlank()) return new ConditionGroup(new ArrayList<>());
        String clean = raw.trim();

        List<Tok> tokens = tokenize(clean);
        if (isCombination(tokens)) {
            Combo combo = new Combo(clean, tokens);
            Condition result = combo.parseOr();
            if (combo.pos < tokens.size()) {
                Diagnostics.error("condition \"" + clean + "\": unexpected '" + tokens.get(combo.pos).text
                        + "' — check the parentheses");
            }
            return result;
        }
        return parseSingle(clean);
    }

    /** One condition: {@code !cond}, a keyword condition, or an expression — the original syntax. */
    private Condition parseSingle(String clean) {
        if (clean.startsWith("!")) {
            Condition inner = parseSingle(clean.substring(1).trim());
            return ctx -> !inner.evaluate(ctx);
        }
        int space = clean.indexOf(' ');
        if (space > 0) {
            ConditionKeyword keyword = keywords.get(clean.substring(0, space).toLowerCase(Locale.ROOT));
            // Keywords are matched case-sensitively in lowercase, as before ("Tag x" is an expression).
            if (keyword != null && clean.substring(0, space).equals(keyword.name())) {
                String argText = clean.substring(space + 1).trim();
                String[] args = argText.isEmpty() ? new String[0] : argText.split("\\s+");
                Condition c = args.length >= keyword.minArgs() ? keyword.compile(argText, args) : null;
                if (c != null) return c;
                Diagnostics.warn("condition \"" + clean + "\": malformed '" + keyword.name()
                        + "' condition — treated as an expression, which is probably always false",
                        "usage: " + keyword.usage());
                // Same fallback as always, without piling the expression's own complaints on top.
                try (LoadScope ignored = LoadScope.enter(null, null, null, d -> { })) {
                    return new ExpressionCondition(expressionParser.parse(clean));
                }
            }
        }
        return new ExpressionCondition(expressionParser.parse(clean));
    }

    /**
     * Parses a list of condition strings into an AND group.
     */
    public ConditionGroup parseList(List<String> list) {
        if (list == null || list.isEmpty()) return new ConditionGroup(new ArrayList<>());
        List<Condition> conditions = new ArrayList<>();
        LoadScope scope = LoadScope.current().orElse(null);
        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            if (scope != null && list.size() > 1) {
                try (LoadScope ignored = scope.sub(String.valueOf(i)).push()) {
                    conditions.add(parse(s));
                }
            } else {
                conditions.add(parse(s));
            }
        }
        return new ConditionGroup(conditions);
    }

    /**
     * Parses a YAML condition value: a string, a list (all must pass), or a map with any of
     * {@code all:} / {@code any:} / {@code none:} lists.
     */
    public Condition parseValue(Object value) {
        if (value == null) return new ConditionGroup(new ArrayList<>());
        if (value instanceof String s) return parse(s);
        if (value instanceof List<?> list) {
            List<String> lines = new ArrayList<>();
            for (Object o : list) if (o != null) lines.add(String.valueOf(o));
            return parseList(lines);
        }
        Map<?, ?> map = value instanceof org.bukkit.configuration.ConfigurationSection cs ? cs.getValues(false)
                : value instanceof Map<?, ?> m ? m : null;
        if (map == null) return parse(String.valueOf(value));
        List<Condition> parts = new ArrayList<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String key = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
            List<Condition> children = new ArrayList<>();
            if (e.getValue() instanceof List<?> l) {
                for (Object o : l) children.add(parseValue(o));
            } else {
                children.add(parseValue(e.getValue()));
            }
            switch (key) {
                case "all" -> parts.add(new ConditionGroup(children));
                case "any" -> parts.add(ctx -> children.stream().anyMatch(c -> c.evaluate(ctx)));
                case "none" -> parts.add(ctx -> children.stream().noneMatch(c -> c.evaluate(ctx)));
                default -> Diagnostics.warn("unknown condition group '" + e.getKey() + "' — use all:, any: or none:");
            }
        }
        return new ConditionGroup(parts);
    }

    /**
     * Parses a comma-separated inline condition token (used by EventParser and objective suffixes).
     * Each name may be prefixed with '!' for negation.
     * Names are treated as inline condition strings (not package-named conditions).
     */
    public ConditionGroup parseInlineList(String raw) {
        if (raw == null || raw.isEmpty()) return new ConditionGroup(new ArrayList<>());
        List<Condition> conditions = new ArrayList<>();
        for (String token : raw.split(",")) {
            String t = token.trim();
            if (!t.isEmpty()) conditions.add(parse(t));
        }
        return new ConditionGroup(conditions);
    }

    // ------------------------------------------------------------------ combinations

    /** A word of a condition string, with its position in the original text. */
    private record Tok(String text, int start, int end) {
        boolean is(String s) {
            return text.equals(s);
        }
    }

    /**
     * Splits on whitespace, keeping quoted text and {@code $variables$} whole and making
     * {@code (}, {@code )} and a leading {@code !} separate tokens.
     */
    private static List<Tok> tokenize(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '(' || c == ')') {
                out.add(new Tok(String.valueOf(c), i, i + 1));
                i++;
                continue;
            }
            if (c == '!' && i + 1 < n && s.charAt(i + 1) != '=') {
                out.add(new Tok("!", i, i + 1));
                i++;
                continue;
            }
            int start = i;
            while (i < n) {
                char d = s.charAt(i);
                if (Character.isWhitespace(d) || d == '(' || d == ')') break;
                if (d == '"' || d == '\'') {
                    int close = s.indexOf(d, i + 1);
                    i = close < 0 ? n : close + 1;
                    continue;
                }
                if (d == '$') {
                    int close = s.indexOf('$', i + 1);
                    i = close < 0 ? n : close + 1;
                    continue;
                }
                i++;
            }
            out.add(new Tok(s.substring(start, i), start, i));
        }
        return out;
    }

    private static boolean isJoiner(Tok t) {
        return t.is("and") || t.is("or") || t.is("&&") || t.is("||");
    }

    /**
     * Whether the string combines conditions with and/or/not in a way the expression language
     * can't express on its own — i.e. a keyword condition appears as an operand. Plain expressions
     * ({@code $a$ > 1 and $b$ < 2}) stay single expressions, exactly as before.
     */
    private boolean isCombination(List<Tok> tokens) {
        boolean hasOperator = false;
        boolean keywordOperand = false;
        boolean atomStart = true;
        for (int i = 0; i < tokens.size(); i++) {
            Tok t = tokens.get(i);
            if (isJoiner(t)) {
                hasOperator = true;
                atomStart = true;
                continue;
            }
            if (t.is("not") || t.is("!") || t.is("(")) {
                if (!t.is("(")) hasOperator = true;
                atomStart = true;
                continue;
            }
            if (atomStart && keywords.containsKey(t.text) && i + 1 < tokens.size()
                    && !isJoiner(tokens.get(i + 1)) && !tokens.get(i + 1).is(")")) {
                keywordOperand = true;
            }
            atomStart = false;
        }
        return keywordOperand && (hasOperator || tokens.get(0).is("("));
    }

    /** Recursive descent over the tokens: or := and ('or' and)*; and := unary ('and' unary)*; unary := ('not'|'!') unary | '(' or ')' | atom. */
    private final class Combo {
        private final String source;
        private final List<Tok> tokens;
        private int pos;
        private int depth;

        Combo(String source, List<Tok> tokens) {
            this.source = source;
            this.tokens = tokens;
        }

        private Tok peek() {
            return pos < tokens.size() ? tokens.get(pos) : null;
        }

        Condition parseOr() {
            List<Condition> any = new ArrayList<>();
            any.add(parseAnd());
            while (peek() != null && (peek().is("or") || peek().is("||"))) {
                pos++;
                any.add(parseAnd());
            }
            if (any.size() == 1) return any.get(0);
            return ctx -> {
                for (Condition c : any) if (c.evaluate(ctx)) return true;
                return false;
            };
        }

        Condition parseAnd() {
            List<Condition> all = new ArrayList<>();
            all.add(parseUnary());
            while (peek() != null && (peek().is("and") || peek().is("&&"))) {
                pos++;
                all.add(parseUnary());
            }
            return all.size() == 1 ? all.get(0) : new ConditionGroup(all);
        }

        Condition parseUnary() {
            Tok t = peek();
            if (t == null) {
                Diagnostics.error("condition \"" + source + "\": ends where a condition was expected");
                return ctx -> false;
            }
            if (t.is("not") || t.is("!")) {
                pos++;
                Condition inner = parseUnary();
                return ctx -> !inner.evaluate(ctx);
            }
            if (t.is("(") && isGroup()) {
                pos++;
                if (++depth > 50) {
                    Diagnostics.error("condition \"" + source + "\": nested too deeply");
                    return ctx -> false;
                }
                Condition inner = parseOr();
                depth--;
                if (peek() != null && peek().is(")")) {
                    pos++;
                } else {
                    Diagnostics.error("condition \"" + source + "\": missing ')'");
                }
                return inner;
            }
            return parseAtom();
        }

        /** A '(' at operand position opens a group unless its ')' is followed by more of an expression. */
        private boolean isGroup() {
            int level = 0;
            for (int i = pos; i < tokens.size(); i++) {
                Tok t = tokens.get(i);
                if (t.is("(")) level++;
                else if (t.is(")") && --level == 0) {
                    Tok after = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
                    return after == null || isJoiner(after) || after.is(")");
                }
            }
            return true;
        }

        /** Tokens up to the next top-level and/or or unmatched ')' form one condition. */
        private Condition parseAtom() {
            int startIdx = pos;
            int level = 0;
            while (pos < tokens.size()) {
                Tok t = tokens.get(pos);
                if (level == 0 && (isJoiner(t) || t.is(")"))) break;
                if (t.is("(")) level++;
                if (t.is(")")) level--;
                pos++;
            }
            if (pos == startIdx) {
                Diagnostics.error("condition \"" + source + "\": expected a condition before '" + tokens.get(pos).text + "'");
                return ctx -> false;
            }
            String text = source.substring(tokens.get(startIdx).start, tokens.get(pos - 1).end);
            return parseSingle(text);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Double number(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void ref(String kind, String id) {
        if (id != null && !id.contains("$")) LoadScope.current().ifPresent(s -> s.ref(kind, id.trim()));
    }
}
