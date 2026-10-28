package org.oqteris.calc.model;

import org.oqteris.calc.format.CellNumberFormatter;

import java.util.Objects;

/**
 * Represents a single cell in a worksheet.
 */
public class Cell {
    private final int row;
    private final int col;
    private CellType type = CellType.BLANK;
    private Object rawValue = null;
    private String formula = null;
    private Object cachedEvaluatedValue = null;
    private CellStyle style = new CellStyle();
    private String comment = null;

    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public CellAddress getAddress() {
        return new CellAddress(row, col);
    }

    public CellType getType() {
        return type;
    }

    public Object getRawValue() {
        return rawValue;
    }

    public String getFormula() {
        return formula;
    }

    public void setFormula(String formula) {
        if (formula != null && formula.startsWith("=")) {
            this.formula = formula.substring(1).trim();
            this.type = CellType.FORMULA;
        } else if (formula != null && !formula.isBlank()) {
            this.formula = formula.trim();
            this.type = CellType.FORMULA;
        } else {
            this.formula = null;
            if (this.type == CellType.FORMULA) {
                this.type = CellType.BLANK;
                this.cachedEvaluatedValue = null;
            }
        }
    }

    public Object getEvaluatedValue() {
        if (type == CellType.FORMULA) {
            return cachedEvaluatedValue;
        }
        return rawValue;
    }

    public void setCachedEvaluatedValue(Object value) {
        this.cachedEvaluatedValue = value;
    }

    public void setValue(Object value) {
        this.formula = null;
        if (value == null) {
            this.type = CellType.BLANK;
            this.rawValue = null;
        } else if (value instanceof Number num) {
            this.type = CellType.NUMERIC;
            this.rawValue = num.doubleValue();
        } else if (value instanceof Boolean bool) {
            this.type = CellType.BOOLEAN;
            this.rawValue = bool;
        } else {
            String str = value.toString();
            if (str.startsWith("=")) {
                setFormula(str);
            } else {
                // Try parse as number
                try {
                    double d = Double.parseDouble(str);
                    this.type = CellType.NUMERIC;
                    this.rawValue = d;
                } catch (NumberFormatException e) {
                    this.type = CellType.STRING;
                    this.rawValue = str;
                }
            }
        }
    }

    public String getDisplayText() {
        Object val = getEvaluatedValue();
        if (val == null) return "";
        return CellNumberFormatter.format(val, style.getNumberFormat());
    }

    public CellStyle getStyle() {
        return style;
    }

    public void setStyle(CellStyle style) {
        this.style = style != null ? style : new CellStyle();
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isNumeric() {
        Object val = getEvaluatedValue();
        return val instanceof Number;
    }

    public double getNumericValue() {
        Object val = getEvaluatedValue();
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }

    public String getStringValue() {
        Object val = getEvaluatedValue();
        return val != null ? val.toString() : "";
    }

    public boolean getBooleanValue() {
        Object val = getEvaluatedValue();
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.doubleValue() != 0;
        return Boolean.parseBoolean(String.valueOf(val));
    }
}
