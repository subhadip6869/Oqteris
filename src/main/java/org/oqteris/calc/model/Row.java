package org.oqteris.calc.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a row of cells in a worksheet.
 */
public class Row {
    private final int rowIndex;
    private double height = 24.0;
    private boolean hidden = false;
    private final Map<Integer, Cell> cells = new HashMap<>();

    public Row(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = Math.max(10.0, height);
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public Cell getCell(int col) {
        return cells.get(col);
    }

    public Cell getOrCreateCell(int col) {
        return cells.computeIfAbsent(col, c -> new Cell(rowIndex, c));
    }

    public void removeCell(int col) {
        cells.remove(col);
    }

    public Map<Integer, Cell> getCells() {
        return Collections.unmodifiableMap(cells);
    }

    public int getMaxColumnIndex() {
        int max = -1;
        for (int c : cells.keySet()) {
            if (c > max) max = c;
        }
        return max;
    }
}
