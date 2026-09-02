package org.nakii.valmora.module.script.expression;

import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.module.script.expression.nodes.BinaryOpNode;
import org.nakii.valmora.module.script.expression.nodes.FunctionNode;
import org.nakii.valmora.module.script.expression.nodes.LiteralNode;
import org.nakii.valmora.module.script.expression.nodes.TernaryNode;
import org.nakii.valmora.module.script.expression.nodes.VariableNode;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a string expression into an AST (Abstract Syntax Tree).
 * Implements recursive descent for precedence and grouping.
 */
public class ExpressionParser {

    // Decoupled from any plugin instance (this class is constructed with a no-arg constructor
    // across ~10 call sites, several in tests) — a standalone java.util.logging.Logger, same
    // pattern as other plugin-instance-free classes in this codebase.
    private static final Logger LOGGER = Logger.getLogger(ExpressionParser.class.getName());

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "\\$[A-Za-z0-9._-]+\\$|" + // Variable: $player.health$
            "\\d+(\\.\\d+)?|" +        // Number: 123.45
            "\"[^\"]*\"|" +            // String literal: "hello"
            "&&|\\|\\||" +             // Logical AND / OR
            "==|!=|>=|<=|>|<|" +       // Comparison Operators
            "[+\\-*/]|" +              // Arithmetic Operators
            "[():?,]|" +               // Grouping, Ternary, arg separator
            "[A-Za-z][A-Za-z0-9_]*"    // Boolean, function names, or generic tokens
    );

    private final List<String> tokens = new ArrayList<>();
    private int cursor = 0;

    // HC-291: guards against a malformed/malicious content-pack expression (e.g. thousands of
    // nested parens) blowing the call stack during parsing — this recursive-descent parser
    // recurses once per nesting level, so unbounded input depth previously meant an unbounded
    // StackOverflowError risk on the main thread. scripting.limits.max-expression-depth.
    private static final int DEFAULT_MAX_DEPTH = 100;
    private int maxDepth = DEFAULT_MAX_DEPTH;
    private int depth = 0;

    public Expression parse(String input) {
        if (input == null || input.isEmpty()) return new LiteralNode(null);

        var plugin = org.nakii.valmora.Valmora.getInstance();
        maxDepth = plugin != null ? plugin.getConfig().getInt("scripting.limits.max-expression-depth", DEFAULT_MAX_DEPTH) : DEFAULT_MAX_DEPTH;
        depth = 0;

        tokens.clear();
        Matcher matcher = TOKEN_PATTERN.matcher(input);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        cursor = 0;

        try {
            return parseTernary();
        } catch (Exception e) {
            // Safe fallback — still evaluates to null rather than throwing at YAML-load or
            // execution time, but now at least logged instead of silently swallowed.
            LOGGER.warning("[Script] Failed to parse expression '" + input + "': " + e);
            return new LiteralNode(null);
        }
    }

    /** Bumps the recursion-depth counter, throwing once {@code maxDepth} is exceeded — call at
     *  the top of every method that recurses back into the grammar (parseTernary and down). */
    private void enterRecursion() {
        if (++depth > maxDepth) {
            throw new IllegalStateException("expression nesting exceeds scripting.limits.max-expression-depth (" + maxDepth + ")");
        }
    }

    private void exitRecursion() {
        depth--;
    }

    private Expression parseTernary() {
        Expression left = parseLogicalOr();
        if (match("?")) {
            Expression trueVal = parseTernary(); // Right associative ternary? Usually ternary is right assoc.
            consume(":");
            Expression falseVal = parseTernary();
            return new TernaryNode(left, trueVal, falseVal);
        }
        return left;
    }

    private Expression parseLogicalOr() {
        Expression left = parseLogicalAnd();
        while (matchAny("or", "||")) {
            Expression right = parseLogicalAnd();
            left = new BinaryOpNode(left, "or", right);
        }
        return left;
    }

    private Expression parseLogicalAnd() {
        Expression left = parseComparison();
        while (matchAny("and", "&&")) {
            Expression right = parseComparison();
            left = new BinaryOpNode(left, "and", right);
        }
        return left;
    }

    private Expression parseComparison() {
        Expression left = parseAddition();
        while (matchAny("==", "!=", ">", "<", ">=", "<=")) {
            String op = tokens.get(cursor - 1);
            Expression right = parseAddition();
            left = new BinaryOpNode(left, op, right);
        }
        return left;
    }

    private Expression parseAddition() {
        Expression left = parseMultiplication();
        while (matchAny("+", "-")) {
            String op = tokens.get(cursor - 1);
            Expression right = parseMultiplication();
            left = new BinaryOpNode(left, op, right);
        }
        return left;
    }

    private Expression parseMultiplication() {
        Expression left = parsePrimary();
        while (matchAny("*", "/")) {
            String op = tokens.get(cursor - 1);
            Expression right = parsePrimary();
            left = new BinaryOpNode(left, op, right);
        }
        return left;
    }

    private Expression parsePrimary() {
        String token = next();
        if (token == null) return new LiteralNode(null);

        if (token.equals("(")) {
            enterRecursion();
            Expression expr = parseTernary();
            exitRecursion();
            consume(")");
            return expr;
        }

        // Unary minus — a standalone "-" reaches here (not parseAddition's binary "-") only at
        // the start of an expression, right after "(", ",", "?", ":", or another operator, e.g.
        // "-5", "(-5)", "max(-5, 0)", "$x$ > -5". Synthesized as 0 - operand so it composes
        // naturally with the existing BinaryOpNode arithmetic (and binds at primary precedence,
        // so "-2*3" correctly parses as (-2)*3, not -(2*3)).
        if (token.equals("-")) {
            enterRecursion();
            Expression operand = parsePrimary();
            exitRecursion();
            return new BinaryOpNode(new LiteralNode(0.0), "-", operand);
        }

        if (token.startsWith("$")) {
            return new VariableNode(token);
        }

        if (token.startsWith("\"")) {
            return new LiteralNode(token.substring(1, token.length() - 1));
        }

        if (token.equalsIgnoreCase("true")) return new LiteralNode(true);
        if (token.equalsIgnoreCase("false")) return new LiteralNode(false);

        if (Character.isDigit(token.charAt(0))) {
            return new LiteralNode(Double.parseDouble(token));
        }

        // Function call: identifier immediately followed by '('
        if (Character.isLetter(token.charAt(0)) && peek("(")) {
            consume("(");
            enterRecursion();
            List<Expression> args = new ArrayList<>();
            if (!peek(")")) {
                args.add(parseTernary());
                while (match(",")) {
                    args.add(parseTernary());
                }
            }
            exitRecursion();
            consume(")");
            return new FunctionNode(token, args);
        }

        return new LiteralNode(token);
    }

    private String next() {
        return cursor < tokens.size() ? tokens.get(cursor++) : null;
    }

    private boolean peek(String expected) {
        return cursor < tokens.size() && tokens.get(cursor).equals(expected);
    }

    private boolean match(String expected) {
        if (cursor < tokens.size() && tokens.get(cursor).equals(expected)) {
            cursor++;
            return true;
        }
        return false;
    }

    private boolean matchAny(String... expected) {
        if (cursor >= tokens.size()) return false;
        String token = tokens.get(cursor);
        for (String s : expected) {
            if (token.equals(s)) {
                cursor++;
                return true;
            }
        }
        return false;
    }

    private void consume(String expected) {
        if (!match(expected)) {
            // In a real parser we'd throw an exception, but here we just skip if missing for robustness
        }
    }
}
