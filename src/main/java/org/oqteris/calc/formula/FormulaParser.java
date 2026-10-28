package org.oqteris.calc.formula;

import org.oqteris.calc.model.CellAddress;
import org.oqteris.calc.model.CellRangeAddress;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses tokenized Excel formulas into an executable ExpressionNode AST.
 */
public class FormulaParser {
    private final List<FormulaTokenizer.Token> tokens;
    private int pos = 0;

    public FormulaParser(List<FormulaTokenizer.Token> tokens) {
        this.tokens = tokens != null ? tokens : new ArrayList<>();
    }

    public static ExpressionNode parse(String formula) {
        if (formula == null || formula.isBlank()) {
            return new ASTNodes.LiteralNode("");
        }
        List<FormulaTokenizer.Token> tokens = FormulaTokenizer.tokenize(formula);
        FormulaParser parser = new FormulaParser(tokens);
        return parser.parseExpression();
    }

    public ExpressionNode parseExpression() {
        return parseComparison();
    }

    private ExpressionNode parseComparison() {
        ExpressionNode left = parseConcat();
        while (match(FormulaTokenizer.TokenType.COMP_EQ, FormulaTokenizer.TokenType.COMP_NE,
                FormulaTokenizer.TokenType.COMP_LT, FormulaTokenizer.TokenType.COMP_LE,
                FormulaTokenizer.TokenType.COMP_GT, FormulaTokenizer.TokenType.COMP_GE)) {
            FormulaTokenizer.Token op = previous();
            ExpressionNode right = parseConcat();
            left = new ASTNodes.BinaryOpNode(op.type, left, right);
        }
        return left;
    }

    private ExpressionNode parseConcat() {
        ExpressionNode left = parseAdditive();
        while (match(FormulaTokenizer.TokenType.OP_CONCAT)) {
            FormulaTokenizer.Token op = previous();
            ExpressionNode right = parseAdditive();
            left = new ASTNodes.BinaryOpNode(op.type, left, right);
        }
        return left;
    }

    private ExpressionNode parseAdditive() {
        ExpressionNode left = parseMultiplicative();
        while (match(FormulaTokenizer.TokenType.OP_PLUS, FormulaTokenizer.TokenType.OP_MINUS)) {
            FormulaTokenizer.Token op = previous();
            ExpressionNode right = parseMultiplicative();
            left = new ASTNodes.BinaryOpNode(op.type, left, right);
        }
        return left;
    }

    private ExpressionNode parseMultiplicative() {
        ExpressionNode left = parsePower();
        while (match(FormulaTokenizer.TokenType.OP_MUL, FormulaTokenizer.TokenType.OP_DIV)) {
            FormulaTokenizer.Token op = previous();
            ExpressionNode right = parsePower();
            left = new ASTNodes.BinaryOpNode(op.type, left, right);
        }
        return left;
    }

    private ExpressionNode parsePower() {
        ExpressionNode left = parseUnary();
        if (match(FormulaTokenizer.TokenType.OP_POW)) {
            FormulaTokenizer.Token op = previous();
            ExpressionNode right = parsePower(); // Right-associative
            left = new ASTNodes.BinaryOpNode(op.type, left, right);
        }
        return left;
    }

    private ExpressionNode parseUnary() {
        if (match(FormulaTokenizer.TokenType.OP_PLUS, FormulaTokenizer.TokenType.OP_MINUS)) {
            FormulaTokenizer.Token op = previous();
            ExpressionNode operand = parseUnary();
            return new ASTNodes.UnaryOpNode(op.type, operand);
        }
        return parsePrimary();
    }

    private ExpressionNode parsePrimary() {
        if (match(FormulaTokenizer.TokenType.NUMBER)) {
            try {
                return new ASTNodes.LiteralNode(Double.parseDouble(previous().text));
            } catch (Exception e) {
                return new ASTNodes.LiteralNode(0.0);
            }
        }

        if (match(FormulaTokenizer.TokenType.STRING)) {
            return new ASTNodes.LiteralNode(previous().text);
        }

        if (match(FormulaTokenizer.TokenType.BOOLEAN)) {
            return new ASTNodes.LiteralNode(Boolean.parseBoolean(previous().text));
        }

        if (match(FormulaTokenizer.TokenType.IDENTIFIER)) {
            String id = previous().text;

            // Check if function call (followed by '(')
            if (match(FormulaTokenizer.TokenType.LPAREN)) {
                List<ExpressionNode> args = new ArrayList<>();
                if (!check(FormulaTokenizer.TokenType.RPAREN)) {
                    do {
                        args.add(parseExpression());
                    } while (match(FormulaTokenizer.TokenType.COMMA));
                }
                consume(FormulaTokenizer.TokenType.RPAREN, "Expected ')' after function arguments.");
                return new ASTNodes.FunctionCallNode(id, args);
            }

            // Check if Range reference (followed by ':' and another identifier)
            if (match(FormulaTokenizer.TokenType.RANGE_COLON)) {
                if (match(FormulaTokenizer.TokenType.IDENTIFIER)) {
                    String rightId = previous().text;
                    CellRangeAddress range = CellRangeAddress.parse(id + ":" + rightId);
                    if (range != null) {
                        return new ASTNodes.RangeRefNode(range);
                    }
                }
            }

            // Single Cell Reference or Named Range
            CellAddress addr = CellAddress.parse(id);
            if (addr != null) {
                return new ASTNodes.CellRefNode(addr);
            }

            // Fallback as string identifier
            return new ASTNodes.LiteralNode(id);
        }

        if (match(FormulaTokenizer.TokenType.LPAREN)) {
            ExpressionNode expr = parseExpression();
            consume(FormulaTokenizer.TokenType.RPAREN, "Expected ')' after expression.");
            return expr;
        }

        // Error fallback
        return new ASTNodes.LiteralNode("#VALUE!");
    }

    private boolean match(FormulaTokenizer.TokenType... types) {
        for (FormulaTokenizer.TokenType t : types) {
            if (check(t)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(FormulaTokenizer.TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private FormulaTokenizer.Token advance() {
        if (!isAtEnd()) pos++;
        return previous();
    }

    private boolean isAtEnd() {
        return pos >= tokens.size();
    }

    private FormulaTokenizer.Token peek() {
        return tokens.get(pos);
    }

    private FormulaTokenizer.Token previous() {
        return tokens.get(pos - 1);
    }

    private FormulaTokenizer.Token consume(FormulaTokenizer.TokenType type, String errorMsg) {
        if (check(type)) return advance();
        return new FormulaTokenizer.Token(type, "");
    }
}
