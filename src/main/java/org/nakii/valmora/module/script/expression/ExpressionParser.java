package org.nakii.valmora.module.script.expression;

import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.infrastructure.config.diag.Diagnostics;
import org.nakii.valmora.infrastructure.config.diag.Severity;
import org.nakii.valmora.infrastructure.config.diag.Suggestions;
import org.nakii.valmora.module.script.expression.ExpressionLexer.Token;
import org.nakii.valmora.module.script.expression.ExpressionLexer.Type;
import org.nakii.valmora.module.script.expression.nodes.BinaryOpNode;
import org.nakii.valmora.module.script.expression.nodes.FunctionNode;
import org.nakii.valmora.module.script.expression.nodes.LiteralNode;
import org.nakii.valmora.module.script.expression.nodes.LogicalNode;
import org.nakii.valmora.module.script.expression.nodes.TernaryNode;
import org.nakii.valmora.module.script.expression.nodes.UnaryNode;
import org.nakii.valmora.module.script.expression.nodes.VariableNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Parses a string expression into an AST (Abstract Syntax Tree). Recursive descent, lowest
 * precedence first:
 * <pre>
 * ternary := or ('?' ternary ':' ternary)?
 * or      := and (('or' | '||') and)*
 * and     := cmp (('and' | '&&') cmp)*
 * cmp     := add (('==' | '!=' | '>' | '<' | '>=' | '<=') add)*
 * add     := mul (('+' | '-') mul)*
 * mul     := unary (('*' | '/' | '%') unary)*
 * unary   := ('!' | 'not' | '-') unary | primary
 * primary := number | "text" | 'text' | $variable$ | true | false | null
 *          | name '(' args ')' | name | '(' ternary ')'
 * </pre>
 * A bare {@code name} is text ({@code $prop.mode$ == deposit}).
 *
 * <p>Stateless and thread-safe: every {@link #parse} call has its own state. Problems (unknown
 * characters, unbalanced parentheses, leftover tokens, unknown functions or wrong argument counts)
 * are reported through {@link Diagnostics} — attributed to the file/entry being loaded, or logged
 * once at runtime. Parsing still recovers the way the old parser did (a missing {@code )} is
 * assumed, leftovers are ignored), so an expression that used to work keeps working.
 */
public class ExpressionParser {

    // HC-291: guards against a malformed/malicious content-pack expression (e.g. thousands of
    // nested parens) blowing the call stack during parsing — this recursive-descent parser
    // recurses once per nesting level. scripting.limits.max-expression-depth.
    private static final int DEFAULT_MAX_DEPTH = 100;

    /** Told about every {@code $namespace...$} variable parsed, for the deferred unknown-namespace check. */
    private static volatile Consumer<String> variableListener;

    public static void setVariableListener(Consumer<String> listener) {
        variableListener = listener;
    }

    private static final java.util.regex.Pattern VARIABLE_TOKEN = java.util.regex.Pattern.compile("\\$[A-Za-z0-9._-]+\\$");

    /**
     * Reports the {@code $variables$} inside free text (event arguments, templates) to the
     * variable listener, as if they had been parsed — they're resolved at runtime only.
     */
    public static void noticeVariablesIn(String text) {
        Consumer<String> listener = variableListener;
        if (listener == null || text == null || text.indexOf('$') < 0) return;
        java.util.regex.Matcher m = VARIABLE_TOKEN.matcher(text);
        while (m.find()) listener.accept(m.group());
    }

    public Expression parse(String input) {
        if (input == null || input.isBlank()) return new LiteralNode(null);
        return new State(input, maxDepth()).parseAll();
    }

    private static int maxDepth() {
        try {
            var plugin = org.nakii.valmora.Valmora.getInstance();
            return plugin != null && plugin.getConfig() != null
                    ? plugin.getConfig().getInt("scripting.limits.max-expression-depth", DEFAULT_MAX_DEPTH)
                    : DEFAULT_MAX_DEPTH;
        } catch (RuntimeException e) {
            return DEFAULT_MAX_DEPTH;
        }
    }

    /** One parse: tokens, cursor and depth. */
    private static final class State {
        private final String input;
        private final int maxDepth;
        private final List<Token> tokens;
        private int cursor;
        private int depth;
        private boolean reported;

        State(String input, int maxDepth) {
            this.input = input;
            this.maxDepth = maxDepth;
            this.tokens = ExpressionLexer.tokenize(input, this::error);
        }

        Expression parseAll() {
            try {
                Expression result = parseTernary();
                if (!peek().is(Type.EOF)) {
                    error("unexpected " + peek().describe() + " after the end of the expression"
                            + (peek().is(Type.COLON) ? " (a ':' without '?')" : "") + " — ignored", peek().column());
                }
                return result;
            } catch (DepthExceeded e) {
                error("nesting exceeds scripting.limits.max-expression-depth (" + maxDepth + ")", 0);
                return new LiteralNode(null);
            } catch (RuntimeException e) {
                error("could not be parsed: " + e, 0);
                return new LiteralNode(null);
            }
        }

        // ------------------------------------------------------------ grammar

        private Expression parseTernary() {
            enter();
            try {
                Expression cond = parseOr();
                if (peek().is(Type.QUESTION)) {
                    next();
                    Expression whenTrue = parseTernary();
                    expect(Type.COLON, "':' (ternary needs both branches: cond ? a : b)");
                    Expression whenFalse = parseTernary();
                    return new TernaryNode(cond, whenTrue, whenFalse);
                }
                return cond;
            } finally {
                depth--;
            }
        }

        private Expression parseOr() {
            Expression left = parseAnd();
            while (peek().isWord("or") || peek().isOp("||")) {
                next();
                left = new LogicalNode(left, false, parseAnd());
            }
            return left;
        }

        private Expression parseAnd() {
            Expression left = parseComparison();
            while (peek().isWord("and") || peek().isOp("&&")) {
                next();
                left = new LogicalNode(left, true, parseComparison());
            }
            return left;
        }

        private Expression parseComparison() {
            Expression left = parseAddition();
            while (peekOp("==", "!=", ">", "<", ">=", "<=")) {
                String op = next().text();
                left = new BinaryOpNode(left, op, parseAddition());
            }
            return left;
        }

        private Expression parseAddition() {
            Expression left = parseMultiplication();
            while (peekOp("+", "-")) {
                String op = next().text();
                left = new BinaryOpNode(left, op, parseMultiplication());
            }
            return left;
        }

        private Expression parseMultiplication() {
            Expression left = parseUnary();
            while (peekOp("*", "/", "%")) {
                String op = next().text();
                left = new BinaryOpNode(left, op, parseUnary());
            }
            return left;
        }

        private Expression parseUnary() {
            Token t = peek();
            boolean notWord = t.isWord("not") && startsOperand(peekAt(1));
            if (t.isOp("!") || notWord || t.isOp("-")) {
                next();
                enter();
                try {
                    Expression operand = parseUnary();
                    return new UnaryNode(t.isOp("-") ? "-" : "!", operand);
                } finally {
                    depth--;
                }
            }
            return parsePrimary();
        }

        private Expression parsePrimary() {
            Token t = next();
            switch (t.type()) {
                case NUMBER -> {
                    return new LiteralNode(Double.parseDouble(t.text()));
                }
                case STRING -> {
                    return new LiteralNode(t.text());
                }
                case VARIABLE -> {
                    Consumer<String> listener = variableListener;
                    if (listener != null) listener.accept(t.text());
                    return new VariableNode(t.text());
                }
                case LPAREN -> {
                    Expression inner = parseTernary();
                    expect(Type.RPAREN, "')' to close the '(' at column " + t.column());
                    return inner;
                }
                case IDENT -> {
                    String word = t.text();
                    if (peek().is(Type.LPAREN)) return parseCall(t);
                    if (word.equalsIgnoreCase("null")) return new LiteralNode(null);
                    if (word.equalsIgnoreCase("true")) return new LiteralNode(true);
                    if (word.equalsIgnoreCase("false")) return new LiteralNode(false);
                    return new LiteralNode(word);
                }
                case EOF -> {
                    error("expression ends where a value was expected", t.column());
                    return new LiteralNode(null);
                }
                default -> {
                    error("unexpected " + t.describe() + " where a value was expected", t.column());
                    return new LiteralNode(null);
                }
            }
        }

        private Expression parseCall(Token name) {
            next(); // (
            enter();
            List<Expression> args = new ArrayList<>();
            try {
                if (!peek().is(Type.RPAREN)) {
                    args.add(parseTernary());
                    while (peek().is(Type.COMMA)) {
                        next();
                        args.add(parseTernary());
                    }
                }
            } finally {
                depth--;
            }
            expect(Type.RPAREN, "')' to close " + name.text() + "(");
            FunctionRegistry.Fn fn = FunctionRegistry.get(name.text());
            if (fn == null) {
                error("unknown function '" + name.text() + "' — it evaluates to 0", name.column(),
                        Suggestions.hint(name.text(), FunctionRegistry.names()));
            } else if (!fn.accepts(args.size())) {
                error(name.text() + "() takes " + arity(fn) + ", got " + args.size() + " — usage: " + fn.usage(), name.column());
            }
            return new FunctionNode(name.text(), args);
        }

        // ------------------------------------------------------------ helpers

        private static String arity(FunctionRegistry.Fn fn) {
            if (fn.minArgs() == fn.maxArgs()) return fn.minArgs() + (fn.minArgs() == 1 ? " argument" : " arguments");
            if (fn.maxArgs() == Integer.MAX_VALUE) return "at least " + fn.minArgs() + " argument" + (fn.minArgs() == 1 ? "" : "s");
            return fn.minArgs() + "-" + fn.maxArgs() + " arguments";
        }

        private static boolean startsOperand(Token t) {
            return switch (t.type()) {
                case NUMBER, STRING, VARIABLE, IDENT, LPAREN -> true;
                case OP -> t.text().equals("!") || t.text().equals("-");
                default -> false;
            };
        }

        private void enter() {
            if (++depth > maxDepth) throw new DepthExceeded();
        }

        private Token peek() {
            return tokens.get(Math.min(cursor, tokens.size() - 1));
        }

        private Token peekAt(int offset) {
            return tokens.get(Math.min(cursor + offset, tokens.size() - 1));
        }

        private boolean peekOp(String... ops) {
            Token t = peek();
            if (t.type() != Type.OP) return false;
            for (String op : ops) if (t.text().equals(op)) return true;
            return false;
        }

        private Token next() {
            Token t = peek();
            if (cursor < tokens.size() - 1) cursor++;
            return t;
        }

        /** Consumes {@code type}, or reports it missing and carries on as if it were there. */
        private void expect(Type type, String what) {
            if (peek().is(type)) {
                next();
            } else {
                error("expected " + what + " but found " + peek().describe(), peek().column());
            }
        }

        private void error(String message, int column) {
            error(message, column, null);
        }

        private void error(String message, int column, String hint) {
            // One report per expression: the first problem is the real one, later ones are usually fallout.
            if (reported) return;
            reported = true;
            String where = column > 0 ? " (column " + column + ")" : "";
            Diagnostics.report(Severity.ERROR, "expression \"" + input + "\": " + message + where, hint);
        }
    }

    private static final class DepthExceeded extends RuntimeException {
        DepthExceeded() {
            super(null, null, false, false);
        }
    }
}
