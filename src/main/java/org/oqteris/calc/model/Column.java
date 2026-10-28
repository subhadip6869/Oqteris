package org.oqteris.calc.model;

/**
 * Column definition holding width and visibility for a spreadsheet column.
 */
public class Column {
    private final int colIndex;
    private double width = 85.0;
    private boolean hidden = false;

    public Column(int colIndex) {
        this.colIndex = colIndex;
    }

    public int getColIndex() {
        return colIndex;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = Math.max(15.0, width);
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}
