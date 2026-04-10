package org.nakii.valmora.module.script.expression;

import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.module.script.expression.nodes.BinaryOpNode;
import org.nakii.valmora.module.script.expression.nodes.LiteralNode;
import org.nakii.valmora.module.script.expression.nodes.TernaryNode;
import org.nakii.valmora.module.script.expression.nodes.VariableNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a string expression into an AST (Abstract Syntax Tree).
 * Implements recursive descent for precedence and grouping.
 */
public class ExpressionParser {

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "\\$[A-Za-z0-9._-]+\\$|" + // Variable: $player.health$
            "\\d+(\\.\\d+)?|" +        // Number: 123.45
            "\"[^\"]*\"|" +            // String literal: "hello"
            "==|!=|>=|<=|>|<|" +       // Comparison Operators
            "[+\\-*/]|" +              // Arithmetic Operators
            "[():?]|" +                // Grouping, Ternary
            "[A-Za-z]+"                // Boolean or generic tokens
    );

    private final List<String> tokens = new ArrayList<>();
    private int cursor = 0;

    public Expression parse(String input) {
        if (input == null || input.isEmpty()) return new LiteralNode(null);

        tokens.clear();
        Matcher matcher = TOKEN_PATTERN.matcher(input);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        cursor = 0;

        try {
            return parseTernary();
        } catch (Exception e) {
            // Safe fallback
            return new LiteralNode(null);
        }
    }

    private Expression parseTernary() {
        Expression left = parseComparison();
        if (match("?")) {
            Expression trueVal = parseTernary(); // Right associative ternary? Usually ternary is right assoc.
            consume(":");
            Expression falseVal = parseTernary();
            return new TernaryNode(left, trueVal, falseVal);
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
            Expression expr = parseTernary();
            consume(")");
            return expr;
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

        return new LiteralNode(token);
    }

    private String next() {
        return cursor < tokens.size() ? tokens.get(cursor++) : null;
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
