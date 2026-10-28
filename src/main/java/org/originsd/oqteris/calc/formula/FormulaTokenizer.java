package org.originsd.oqteris.calc.formula;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizer for spreadsheet formula expressions.
 */
public class FormulaTokenizer {
    public enum TokenType {
        NUMBER,
        STRING,
        BOOLEAN,
        IDENTIFIER, // Function name or cell reference
        RANGE_COLON,
        COMMA,
        LPAREN,
        RPAREN,
        OP_PLUS,
        OP_MINUS,
        OP_MUL,
        OP_DIV,
        OP_POW,
        OP_CONCAT,
        COMP_EQ,
        COMP_NE,
        COMP_LT,
        COMP_LE,
        COMP_GT,
        COMP_GE
    }

    public static class Token {
        public final TokenType type;
        public final String text;

        public Token(TokenType type, String text) {
            this.type = type;
            this.text = text;
        }

        @Override
        public String toString() {
            return type + "(" + text + ")";
        }
    }

    public static List<Token> tokenize(String formula) {
        List<Token> tokens = new ArrayList<>();
        if (formula == null) return tokens;

        String expr = formula.startsWith("=") ? formula.substring(1).trim() : formula.trim();
        int len = expr.length();
        int i = 0;

        while (i < len) {
            char c = expr.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // String literal "..."
            if (c == '"') {
                int start = i + 1;
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < len) {
                    if (expr.charAt(i) == '"') {
                        if (i + 1 < len && expr.charAt(i + 1) == '"') {
                            sb.append('"');
                            i += 2;
                        } else {
                            i++;
                            break;
                        }
                    } else {
                        sb.append(expr.charAt(i));
                        i++;
                    }
                }
                tokens.add(new Token(TokenType.STRING, sb.toString()));
                continue;
            }

            // Number literal
            if (Character.isDigit(c) || (c == '.' && i + 1 < len && Character.isDigit(expr.charAt(i + 1)))) {
                int start = i;
                boolean hasDot = (c == '.');
                i++;
                while (i < len) {
                    char ch = expr.charAt(i);
                    if (Character.isDigit(ch)) {
                        i++;
                    } else if (ch == '.' && !hasDot) {
                        hasDot = true;
                        i++;
                    } else {
                        break;
                    }
                }
                tokens.add(new Token(TokenType.NUMBER, expr.substring(start, i)));
                continue;
            }

            // Single quote string for sheet names (e.g. 'Sheet 1'!A1)
            if (c == '\'') {
                int start = i;
                i++;
                while (i < len && expr.charAt(i) != '\'') {
                    i++;
                }
                if (i < len) i++; // consume closing quote
                if (i < len && expr.charAt(i) == '!') {
                    i++; // consume '!'
                    // Now read following cell reference
                    while (i < len && (Character.isLetterOrDigit(expr.charAt(i)) || expr.charAt(i) == '$')) {
                        i++;
                    }
                }
                tokens.add(new Token(TokenType.IDENTIFIER, expr.substring(start, i)));
                continue;
            }

            // Identifiers: Function names, cell references, sheet names (e.g. SUM, A1, $A$1, Sheet1!A1)
            if (Character.isLetter(c) || c == '_' || c == '$') {
                int start = i;
                while (i < len) {
                    char ch = expr.charAt(i);
                    if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '$') {
                        i++;
                    } else if (ch == '!' && i + 1 < len && (Character.isLetter(expr.charAt(i + 1)) || expr.charAt(i + 1) == '$')) {
                        i++; // Sheet qualifier
                    } else {
                        break;
                    }
                }
                String text = expr.substring(start, i);
                if (text.equalsIgnoreCase("TRUE") || text.equalsIgnoreCase("FALSE")) {
                    tokens.add(new Token(TokenType.BOOLEAN, text.toUpperCase()));
                } else {
                    tokens.add(new Token(TokenType.IDENTIFIER, text));
                }
                continue;
            }

            // Two-character comparisons
            if (c == '<') {
                if (i + 1 < len && expr.charAt(i + 1) == '=') {
                    tokens.add(new Token(TokenType.COMP_LE, "<="));
                    i += 2;
                } else if (i + 1 < len && expr.charAt(i + 1) == '>') {
                    tokens.add(new Token(TokenType.COMP_NE, "<>"));
                    i += 2;
                } else {
                    tokens.add(new Token(TokenType.COMP_LT, "<"));
                    i++;
                }
                continue;
            }
            if (c == '>') {
                if (i + 1 < len && expr.charAt(i + 1) == '=') {
                    tokens.add(new Token(TokenType.COMP_GE, ">="));
                    i += 2;
                } else {
                    tokens.add(new Token(TokenType.COMP_GT, ">"));
                    i++;
                }
                continue;
            }

            // Single character operators
            switch (c) {
                case '+' -> tokens.add(new Token(TokenType.OP_PLUS, "+"));
                case '-' -> tokens.add(new Token(TokenType.OP_MINUS, "-"));
                case '*' -> tokens.add(new Token(TokenType.OP_MUL, "*"));
                case '/' -> tokens.add(new Token(TokenType.OP_DIV, "/"));
                case '^' -> tokens.add(new Token(TokenType.OP_POW, "^"));
                case '&' -> tokens.add(new Token(TokenType.OP_CONCAT, "&"));
                case '=' -> tokens.add(new Token(TokenType.COMP_EQ, "="));
                case ':' -> tokens.add(new Token(TokenType.RANGE_COLON, ":"));
                case ',' -> tokens.add(new Token(TokenType.COMMA, ","));
                case '(' -> tokens.add(new Token(TokenType.LPAREN, "("));
                case ')' -> tokens.add(new Token(TokenType.RPAREN, ")"));
                default -> {
                    // Ignore unrecognized char
                }
            }
            i++;
        }

        return tokens;
    }
}
