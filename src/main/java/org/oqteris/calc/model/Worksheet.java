package org.oqteris.calc.model;

import java.util.*;

/**
 * Represents a single worksheet grid of cells, rows, and columns.
 */
public class Worksheet {
    private String name;
    private final Map<Integer, Row> rows = new HashMap<>();
    private final Map<Integer, Column> columns = new HashMap<>();
    private final List<CellRangeAddress> mergedRanges = new ArrayList<>();

    private double defaultRowHeight = 24.0;
    private double defaultColumnWidth = 85.0;

    private int freezeRows = 0;
    private int freezeCols = 0;
    private CellRangeAddress autoFilterRange = null;

    public Worksheet(String name) {
        this.name = name != null ? name : "Sheet1";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : "Sheet";
    }

    public Row getRow(int rowIndex) {
        return rows.get(rowIndex);
    }

    public Row getOrCreateRow(int rowIndex) {
        return rows.computeIfAbsent(rowIndex, Row::new);
    }

    public Column getColumn(int colIndex) {
        return columns.get(colIndex);
    }

    public Column getOrCreateColumn(int colIndex) {
        return columns.computeIfAbsent(colIndex, Column::new);
    }

    public Cell getCell(int row, int col) {
        Row r = rows.get(row);
        return r != null ? r.getCell(col) : null;
    }

    public Cell getOrCreateCell(int row, int col) {
        return getOrCreateRow(row).getOrCreateCell(col);
    }

    public Cell getCell(CellAddress addr) {
        return addr != null ? getCell(addr.getRow(), addr.getCol()) : null;
    }

    public double getRowHeight(int row) {
        Row r = rows.get(row);
        return r != null ? r.getHeight() : defaultRowHeight;
    }

    public void setRowHeight(int row, double height) {
        getOrCreateRow(row).setHeight(height);
    }

    public double getColumnWidth(int col) {
        Column c = columns.get(col);
        return c != null ? c.getWidth() : defaultColumnWidth;
    }

    public void setColumnWidth(int col, double width) {
        getOrCreateColumn(col).setWidth(width);
    }

    public double getDefaultRowHeight() {
        return defaultRowHeight;
    }

    public void setDefaultRowHeight(double defaultRowHeight) {
        this.defaultRowHeight = defaultRowHeight;
    }

    public double getDefaultColumnWidth() {
        return defaultColumnWidth;
    }

    public void setDefaultColumnWidth(double defaultColumnWidth) {
        this.defaultColumnWidth = defaultColumnWidth;
    }

    public int getFreezeRows() {
        return freezeRows;
    }

    public void setFreezeRows(int freezeRows) {
        this.freezeRows = Math.max(0, freezeRows);
    }

    public int getFreezeCols() {
        return freezeCols;
    }

    public void setFreezeCols(int freezeCols) {
        this.freezeCols = Math.max(0, freezeCols);
    }

    public CellRangeAddress getAutoFilterRange() {
        return autoFilterRange;
    }

    public void setAutoFilterRange(CellRangeAddress autoFilterRange) {
        this.autoFilterRange = autoFilterRange;
    }

    public List<CellRangeAddress> getMergedRanges() {
        return Collections.unmodifiableList(mergedRanges);
    }

    public void addMergedRange(CellRangeAddress range) {
        if (range != null && !mergedRanges.contains(range)) {
            mergedRanges.add(range);
        }
    }

    public void removeMergedRange(CellRangeAddress range) {
        mergedRanges.remove(range);
    }

    public CellRangeAddress getMergedRangeAt(int row, int col) {
        for (CellRangeAddress r : mergedRanges) {
            if (r.contains(row, col)) {
                return r;
            }
        }
        return null;
    }

    public boolean isMergedMaster(int row, int col) {
        CellRangeAddress r = getMergedRangeAt(row, col);
        return r != null && r.getFirstRow() == row && r.getFirstCol() == col;
    }

    public boolean isMergedSlave(int row, int col) {
        CellRangeAddress r = getMergedRangeAt(row, col);
        return r != null && (r.getFirstRow() != row || r.getFirstCol() != col);
    }

    public int getMaxRow() {
        int max = -1;
        for (int r : rows.keySet()) {
            if (r > max) max = r;
        }
        return Math.max(max, 0);
    }

    public int getMaxCol() {
        int max = -1;
        for (Row r : rows.values()) {
            int c = r.getMaxColumnIndex();
            if (c > max) max = c;
        }
        return Math.max(max, 0);
    }

    private static class CellSnapshot {
        final CellType type;
        final Object rawValue;
        final String formula;
        final CellStyle style;
        final boolean isNumeric;
        final double numericValue;
        final String stringValue;

        CellSnapshot(Cell cell) {
            if (cell != null) {
                this.type = cell.getType();
                this.rawValue = cell.getRawValue();
                this.formula = cell.getFormula();
                this.style = cell.getStyle().copy();
                this.isNumeric = cell.isNumeric();
                this.numericValue = cell.getNumericValue();
                this.stringValue = cell.getStringValue();
            } else {
                this.type = CellType.BLANK;
                this.rawValue = null;
                this.formula = null;
                this.style = new CellStyle();
                this.isNumeric = false;
                this.numericValue = 0;
                this.stringValue = "";
            }
        }
    }

    /**
     * Sorts rows within a range by a specific column key.
     */
    public void sortRange(CellRangeAddress range, int sortCol, boolean ascending) {
        if (range == null) return;
        int r1 = range.getFirstRow();
        int r2 = range.getLastRow();
        int c1 = range.getFirstCol();
        int c2 = range.getLastCol();

        List<List<CellSnapshot>> rowData = new ArrayList<>();
        for (int r = r1; r <= r2; r++) {
            List<CellSnapshot> rowCells = new ArrayList<>();
            for (int c = c1; c <= c2; c++) {
                rowCells.add(new CellSnapshot(getCell(r, c)));
            }
            rowData.add(rowCells);
        }

        int colOffset = sortCol - c1;
        rowData.sort((rowA, rowB) -> {
            CellSnapshot cellA = (colOffset >= 0 && colOffset < rowA.size()) ? rowA.get(colOffset) : null;
            CellSnapshot cellB = (colOffset >= 0 && colOffset < rowB.size()) ? rowB.get(colOffset) : null;

            if (cellA == null && cellB == null) return 0;
            if (cellA == null) return ascending ? 1 : -1;
            if (cellB == null) return ascending ? -1 : 1;

            if (cellA.isNumeric && cellB.isNumeric) {
                int cmp = Double.compare(cellA.numericValue, cellB.numericValue);
                return ascending ? cmp : -cmp;
            }

            int cmp = cellA.stringValue.compareToIgnoreCase(cellB.stringValue);
            return ascending ? cmp : -cmp;
        });

        // Write back
        for (int i = 0; i < rowData.size(); i++) {
            int targetRow = r1 + i;
            List<CellSnapshot> srcCells = rowData.get(i);
            for (int j = 0; j < srcCells.size(); j++) {
                int targetCol = c1 + j;
                CellSnapshot src = srcCells.get(j);
                if (src.type != CellType.BLANK) {
                    Cell dest = getOrCreateCell(targetRow, targetCol);
                    if (src.type == CellType.FORMULA) {
                        dest.setFormula(src.formula);
                    } else {
                        dest.setValue(src.rawValue);
                    }
                    dest.setStyle(src.style.copy());
                } else {
                    Row r = rows.get(targetRow);
                    if (r != null) r.removeCell(targetCol);
                }
            }
        }
    }
}
