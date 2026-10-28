package org.oqteris.calc.formula;

import org.oqteris.calc.model.Cell;
import org.oqteris.calc.model.CellAddress;
import org.oqteris.calc.model.CellRangeAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Concrete AST nodes for formula expressions.
 */
public class ASTNodes {

    public static class LiteralNode implements ExpressionNode {
        private final Object value;

        public LiteralNode(Object value) {
            this.value = value;
        }

        @Override
        public Object evaluate(EvaluationContext context) {
            return value;
        }

        @Override
        public void collectReferences(String defaultSheet, Set<CellAddress> references) {
        }
    }

    public static class CellRefNode implements ExpressionNode {
        private final CellAddress address;

        public CellRefNode(CellAddress address) {
            this.address = address;
        }

        public CellAddress getAddress() {
            return address;
        }

        @Override
        public Object evaluate(EvaluationContext context) {
            Cell cell = context.getCell(address);
            if (cell == null) return 0.0;
            return cell.getEvaluatedValue();
        }

        @Override
        public void collectReferences(String defaultSheet, Set<CellAddress> references) {
            String sheet = address.getSheetName() != null ? address.getSheetName() : defaultSheet;
            references.add(new CellAddress(sheet, address.getRow(), address.getCol()));
        }
    }

    public static class RangeRefNode implements ExpressionNode {
        private final CellRangeAddress range;

        public RangeRefNode(CellRangeAddress range) {
            this.range = range;
        }

        public CellRangeAddress getRange() {
            return range;
        }

        @Override
        public Object evaluate(EvaluationContext context) {
            // Returns 2D grid of values
            List<List<Object>> table = new ArrayList<>();
            for (int r = range.getFirstRow(); r <= range.getLastRow(); r++) {
                List<Object> row = new ArrayList<>();
                for (int c = range.getFirstCol(); c <= range.getLastCol(); c++) {
                    Cell cell = context.getCell(new CellAddress(range.getSheetName(), r, c));
                    row.add(cell != null ? cell.getEvaluatedValue() : null);
                }
                table.add(row);
            }
            return table;
        }

        @Override
        public void collectReferences(String defaultSheet, Set<CellAddress> references) {
            String sheet = range.getSheetName() != null ? range.getSheetName() : defaultSheet;
            for (int r = range.getFirstRow(); r <= range.getLastRow(); r++) {
                for (int c = range.getFirstCol(); c <= range.getLastCol(); c++) {
                    references.add(new CellAddress(sheet, r, c));
                }
            }
        }
    }

    public static class FunctionCallNode implements ExpressionNode {
        private final String functionName;
        private final List<ExpressionNode> arguments;

        public FunctionCallNode(String functionName, List<ExpressionNode> arguments) {
            this.functionName = functionName;
            this.arguments = arguments != null ? arguments : new ArrayList<>();
        }

        @Override
        public Object evaluate(EvaluationContext context) {
            List<Object> evaluatedArgs = new ArrayList<>();
            for (ExpressionNode arg : arguments) {
                evaluatedArgs.add(arg.evaluate(context));
            }
            return FunctionRegistry.call(functionName, evaluatedArgs, context);
        }

        @Override
        public void collectReferences(String defaultSheet, Set<CellAddress> references) {
            for (ExpressionNode arg : arguments) {
                arg.collectReferences(defaultSheet, references);
            }
        }
    }

    public static class BinaryOpNode implements ExpressionNode {
        private final FormulaTokenizer.TokenType operator;
        private final ExpressionNode left;
        private final ExpressionNode right;

        public BinaryOpNode(FormulaTokenizer.TokenType operator, ExpressionNode left, ExpressionNode right) {
            this.operator = operator;
            this.left = left;
            this.right = right;
        }

        @Override
        public Object evaluate(EvaluationContext context) {
            Object lVal = left.evaluate(context);
            Object rVal = right.evaluate(context);

            switch (operator) {
                case OP_PLUS -> {
                    return FunctionRegistry.toDouble(lVal) + FunctionRegistry.toDouble(rVal);
                }
                case OP_MINUS -> {
                    return FunctionRegistry.toDouble(lVal) - FunctionRegistry.toDouble(rVal);
                }
                case OP_MUL -> {
                    return FunctionRegistry.toDouble(lVal) * FunctionRegistry.toDouble(rVal);
                }
                case OP_DIV -> {
                    double divisor = FunctionRegistry.toDouble(rVal);
                    return divisor != 0 ? FunctionRegistry.toDouble(lVal) / divisor : "#DIV/0!";
                }
                case OP_POW -> {
                    return Math.pow(FunctionRegistry.toDouble(lVal), FunctionRegistry.toDouble(rVal));
                }
                case OP_CONCAT -> {
                    return (lVal != null ? lVal.toString() : "") + (rVal != null ? rVal.toString() : "");
                }
                case COMP_EQ -> {
                    return compare(lVal, rVal) == 0;
                }
                case COMP_NE -> {
                    return compare(lVal, rVal) != 0;
                }
                case COMP_LT -> {
                    return compare(lVal, rVal) < 0;
                }
                case COMP_LE -> {
                    return compare(lVal, rVal) <= 0;
                }
                case COMP_GT -> {
                    return compare(lVal, rVal) > 0;
                }
                case COMP_GE -> {
                    return compare(lVal, rVal) >= 0;
                }
                default -> {
                    return "#VALUE!";
                }
            }
        }

        private int compare(Object a, Object b) {
            if (a instanceof Number na && b instanceof Number nb) {
                return Double.compare(na.doubleValue(), nb.doubleValue());
            }
            String sa = a != null ? a.toString() : "";
            String sb = b != null ? b.toString() : "";
            return sa.compareToIgnoreCase(sb);
        }

        @Override
        public void collectReferences(String defaultSheet, Set<CellAddress> references) {
            left.collectReferences(defaultSheet, references);
            right.collectReferences(defaultSheet, references);
        }
    }

    public static class UnaryOpNode implements ExpressionNode {
        private final FormulaTokenizer.TokenType operator;
        private final ExpressionNode operand;

        public UnaryOpNode(FormulaTokenizer.TokenType operator, ExpressionNode operand) {
            this.operator = operator;
            this.operand = operand;
        }

        @Override
        public Object evaluate(EvaluationContext context) {
            Object val = operand.evaluate(context);
            double d = FunctionRegistry.toDouble(val);
            return operator == FormulaTokenizer.TokenType.OP_MINUS ? -d : d;
        }

        @Override
        public void collectReferences(String defaultSheet, Set<CellAddress> references) {
            operand.collectReferences(defaultSheet, references);
        }
    }
}
