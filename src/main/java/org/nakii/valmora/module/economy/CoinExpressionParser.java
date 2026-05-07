package org.nakii.valmora.module.economy;

/**
 * Parses coin amount expressions like "2.5k", "1m+500k", "3k-1".
 * Supports k/m/b suffixes and +/-/*\/ arithmetic.
 * Returns 0.0 on any parse error — never throws.
 */
public final class CoinExpressionParser {

    public static double parse(String input) {
        if (input == null || input.isBlank()) return 0.0;
        try {
            return new CoinExpressionParser(input.trim()).parseExpr();
        } catch (Exception e) {
            return 0.0;
        }
    }

    private final String src;
    private int pos;

    private CoinExpressionParser(String src) {
        this.src = src;
        this.pos = 0;
    }

    // expr = term (('+' | '-') term)*
    private double parseExpr() {
        double result = parseTerm();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '+') { pos++; result += parseTerm(); }
            else if (c == '-') { pos++; result -= parseTerm(); }
            else break;
        }
        return result;
    }

    // term = factor (('*' | '/') factor)*
    private double parseTerm() {
        double result = parseFactor();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '*') { pos++; result *= parseFactor(); }
            else if (c == '/') { pos++; double d = parseFactor(); result = d != 0 ? result / d : 0; }
            else break;
        }
        return result;
    }

    // factor = '(' expr ')' | ['-'] NUMBER [SUFFIX]
    private double parseFactor() {
        skipSpaces();
        if (pos >= src.length()) throw new IllegalStateException("Unexpected end of input");

        if (src.charAt(pos) == '(') {
            pos++; // skip '('
            double v = parseExpr();
            skipSpaces();
            if (pos < src.length() && src.charAt(pos) == ')') pos++;
            return v;
        }

        // optional unary minus
        boolean negative = false;
        if (src.charAt(pos) == '-') { negative = true; pos++; }

        // parse number
        int start = pos;
        while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) pos++;
        if (pos == start) throw new IllegalStateException("Expected number at pos " + pos);
        double value = Double.parseDouble(src.substring(start, pos));

        // optional suffix
        if (pos < src.length()) {
            char suffix = src.charAt(pos);
            switch (Character.toLowerCase(suffix)) {
                case 'k' -> { value *= 1_000; pos++; }
                case 'm' -> { value *= 1_000_000; pos++; }
                case 'b' -> { value *= 1_000_000_000; pos++; }
            }
        }

        return negative ? -value : value;
    }

    private void skipSpaces() {
        while (pos < src.length() && src.charAt(pos) == ' ') pos++;
    }
}
