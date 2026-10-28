package org.oqteris.calc.model;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a contiguous rectangular range of cells (e.g. A1:C10).
 */
public class CellRangeAddress {
    private static final Pattern RANGE_PATTERN = Pattern.compile("^(?:'?([^'!\\[\\]]+)'?!)?\\$?([A-Za-z]+)\\$?([0-9]+):\\$?([A-Za-z]+)\\$?([0-9]+)$");

    private final String sheetName;
    private final int firstRow;
    private final int lastRow;
    private final int firstCol;
    private final int lastCol;

    public CellRangeAddress(int firstRow, int lastRow, int firstCol, int lastCol) {
        this(null, firstRow, lastRow, firstCol, lastCol);
    }

    public CellRangeAddress(String sheetName, int firstRow, int lastRow, int firstCol, int lastCol) {
        this.sheetName = sheetName;
        this.firstRow = Math.min(firstRow, lastRow);
        this.lastRow = Math.max(firstRow, lastRow);
        this.firstCol = Math.min(firstCol, lastCol);
        this.lastCol = Math.max(firstCol, lastCol);
    }

    public static CellRangeAddress parse(String ref) {
        if (ref == null) return null;
        Matcher m = RANGE_PATTERN.matcher(ref.trim());
        if (m.matches()) {
            String sheet = m.group(1);
            int r1 = Integer.parseInt(m.group(3)) - 1;
            int c1 = CellAddress.lettersToCol(m.group(2));
            int r2 = Integer.parseInt(m.group(5)) - 1;
            int c2 = CellAddress.lettersToCol(m.group(4));
            return new CellRangeAddress(sheet, r1, r2, c1, c2);
        }
        // Check single cell fallback
        CellAddress single = CellAddress.parse(ref);
        if (single != null) {
            return new CellRangeAddress(single.getSheetName(), single.getRow(), single.getRow(), single.getCol(), single.getCol());
        }
        return null;
    }

    public String getSheetName() {
        return sheetName;
    }

    public int getFirstRow() {
        return firstRow;
    }

    public int getLastRow() {
        return lastRow;
    }

    public int getFirstCol() {
        return firstCol;
    }

    public int getLastCol() {
        return lastCol;
    }

    public int getRowCount() {
        return lastRow - firstRow + 1;
    }

    public int getColumnCount() {
        return lastCol - firstCol + 1;
    }

    public boolean contains(int row, int col) {
        return row >= firstRow && row <= lastRow && col >= firstCol && col <= lastCol;
    }

    public boolean contains(CellAddress addr) {
        if (addr == null) return false;
        if (sheetName != null && addr.getSheetName() != null && !sheetName.equalsIgnoreCase(addr.getSheetName())) {
            return false;
        }
        return contains(addr.getRow(), addr.getCol());
    }

    public boolean intersects(CellRangeAddress other) {
        if (other == null) return false;
        return firstRow <= other.lastRow && lastRow >= other.firstRow &&
               firstCol <= other.lastCol && lastCol >= other.firstCol;
    }

    public String toA1() {
        String start = CellAddress.colToLetters(firstCol) + (firstRow + 1);
        String end = CellAddress.colToLetters(lastCol) + (lastRow + 1);
        if (sheetName != null && !sheetName.isBlank()) {
            String prefix = sheetName.contains(" ") ? "'" + sheetName + "'!" : sheetName + "!";
            return prefix + start + ":" + end;
        }
        return start + ":" + end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CellRangeAddress that)) return false;
        return firstRow == that.firstRow && lastRow == that.lastRow &&
               firstCol == that.firstCol && lastCol == that.lastCol &&
               Objects.equals(sheetName, that.sheetName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sheetName, firstRow, lastRow, firstCol, lastCol);
    }

    @Override
    public String toString() {
        return toA1();
    }
}
