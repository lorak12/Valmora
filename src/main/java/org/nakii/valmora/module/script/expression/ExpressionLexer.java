package org.nakii.valmora.module.script.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Splits an expression into tokens, remembering each token's column so syntax errors can point
 * at it. Unknown characters are reported and skipped (the old regex tokenizer skipped them
 * silently), so a typo never turns a whole expression into nothing.
 */
final class ExpressionLexer {

    enum Type { NUMBER, STRING, VARIABLE, IDENT, OP, LPAREN, RPAREN, COMMA, QUESTION, COLON, EOF }

    record Token(Type type, String text, int column) {
        boolean is(Type t) {
            return type == t;
        }

        boolean isOp(String op) {
            return type == Type.OP && text.equals(op);
        }

        boolean isWord(String word) {
            return type == Type.IDENT && text.equals(word);
        }

        String describe() {
            return switch (type) {
                case EOF -> "end of expression";
                case STRING -> "\"" + text + "\"";
                default -> "'" + text + "'";
            };
        }
    }

    private ExpressionLexer() {}

    /**
     * @param onError receives (message, column) for each lexical problem; lexing continues past it
     */
    static List<Token> tokenize(String input, BiConsumer<String, Integer> onError) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = input.length();
        while (i < n) {
            char c = input.charAt(i);
            int col = i + 1;
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            // $namespace.path$ variables
            if (c == '$') {
                int end = i + 1;
                while (end < n && isVariableChar(input.charAt(end))) end++;
                if (end < n && input.charAt(end) == '$' && end > i + 1) {
                    tokens.add(new Token(Type.VARIABLE, input.substring(i, end + 1), col));
                    i = end + 1;
                } else {
                    onError.accept("unterminated or malformed variable (expected $namespace.path$)", col);
                    i = end;
                }
                continue;
            }
            // numbers: 12, 12.5, .5
            if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(input.charAt(i + 1)))) {
                int end = i;
                while (end < n && Character.isDigit(input.charAt(end))) end++;
                if (end < n && input.charAt(end) == '.' && end + 1 < n && Character.isDigit(input.charAt(end + 1))) {
                    end++;
                    while (end < n && Character.isDigit(input.charAt(end))) end++;
                }
                tokens.add(new Token(Type.NUMBER, input.substring(i, end), col));
                i = end;
                continue;
            }
            // "strings" and 'strings', with \" \' \\ \n \t escapes
            if (c == '"' || c == '\'') {
                StringBuilder sb = new StringBuilder();
                int j = i + 1;
                boolean closed = false;
                while (j < n) {
                    char d = input.charAt(j);
                    if (d == '\\' && j + 1 < n) {
                        char e = input.charAt(j + 1);
                        switch (e) {
                            case '"', '\'', '\\' -> sb.append(e);
                            case 'n' -> sb.append('\n');
                            case 't' -> sb.append('\t');
                            default -> sb.append(d).append(e); // not an escape: keep both, as before
                        }
                        j += 2;
                        continue;
                    }
                    if (d == c) {
                        closed = true;
                        break;
                    }
                    sb.append(d);
                    j++;
                }
                if (!closed) onError.accept("unterminated string starting here", col);
                tokens.add(new Token(Type.STRING, sb.toString(), col));
                i = closed ? j + 1 : n;
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int end = i + 1;
                while (end < n && (Character.isLetterOrDigit(input.charAt(end)) || input.charAt(end) == '_')) end++;
                tokens.add(new Token(Type.IDENT, input.substring(i, end), col));
                i = end;
                continue;
            }
            String two = i + 1 < n ? input.substring(i, i + 2) : "";
            switch (two) {
                case "&&", "||", "==", "!=", ">=", "<=" -> {
                    tokens.add(new Token(Type.OP, two, col));
                    i += 2;
                    continue;
                }
                default -> { }
            }
            switch (c) {
                case '+', '-', '*', '/', '%', '>', '<', '!' -> tokens.add(new Token(Type.OP, String.valueOf(c), col));
                case '(' -> tokens.add(new Token(Type.LPAREN, "(", col));
                case ')' -> tokens.add(new Token(Type.RPAREN, ")", col));
                case ',' -> tokens.add(new Token(Type.COMMA, ",", col));
                case '?' -> tokens.add(new Token(Type.QUESTION, "?", col));
                case ':' -> tokens.add(new Token(Type.COLON, ":", col));
                case '=' -> {
                    onError.accept("'=' is not an operator — use '==' to compare", col);
                    tokens.add(new Token(Type.OP, "==", col));
                }
                case '&' -> {
                    onError.accept("'&' is not an operator — use '&&' or 'and'", col);
                    tokens.add(new Token(Type.OP, "&&", col));
                }
                case '|' -> {
                    onError.accept("'|' is not an operator — use '||' or 'or'", col);
                    tokens.add(new Token(Type.OP, "||", col));
                }
                default -> onError.accept("unexpected character '" + c + "'", col);
            }
            i++;
        }
        tokens.add(new Token(Type.EOF, "", n + 1));
        return tokens;
    }

    private static boolean isVariableChar(char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-';
    }
}
