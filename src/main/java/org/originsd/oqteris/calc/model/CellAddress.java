package org.originsd.oqteris.calc.model;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a spreadsheet cell coordinate (0-based row and column),
 * optionally qualified with a sheet name.
 */
public class CellAddress {
    private static final Pattern CELL_PATTERN = Pattern.compile("^(?:'?([^'!\\[\\]]+)'?!)?\\$?([A-Za-z]+)\\$?([0-9]+)$");

    private final String sheetName;
    private final int row;
    private final int col;

    public CellAddress(int row, int col) {
        this(null, row, col);
    }

    public CellAddress(String sheetName, int row, int col) {
        this.sheetName = sheetName;
        this.row = Math.max(0, row);
        this.col = Math.max(0, col);
    }

    public String getSheetName() {
        return sheetName;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    /**
     * Converts column index (0-based) to Excel letter notation (0 -> "A", 27 -> "AB").
     */
    public static String colToLetters(int col) {
        StringBuilder sb = new StringBuilder();
        int c = col;
        while (c >= 0) {
            sb.insert(0, (char) ('A' + (c % 26)));
            c = (c / 26) - 1;
        }
        return sb.toString();
    }

    /**
     * Converts Excel column letters to 0-based column index ("A" -> 0, "AB" -> 27).
     */
    public static int lettersToCol(String letters) {
        int col = 0;
        String upper = letters.toUpperCase();
        for (int i = 0; i < upper.length(); i++) {
            col = col * 26 + (upper.charAt(i) - 'A' + 1);
        }
        return col - 1;
    }

    /**
     * Parses standard A1 notation (e.g. "A1", "Sheet1!$B$5", "'Sales Data'!C10").
     */
    public static CellAddress parse(String ref) {
        if (ref == null) return null;
        Matcher m = CELL_PATTERN.matcher(ref.trim());
        if (m.matches()) {
            String sheet = m.group(1);
            String colLetters = m.group(2);
            int rowNumber = Integer.parseInt(m.group(3)) - 1;
            int colNumber = lettersToCol(colLetters);
            return new CellAddress(sheet, rowNumber, colNumber);
        }
        return null;
    }

    /**
     * Formats this address in standard A1 notation.
     */
    public String toA1() {
        String colStr = colToLetters(col);
        String rowStr = String.valueOf(row + 1);
        if (sheetName != null && !sheetName.isBlank()) {
            if (sheetName.contains(" ")) {
                return "'" + sheetName + "'!" + colStr + rowStr;
            } else {
                return sheetName + "!" + colStr + rowStr;
            }
        }
        return colStr + rowStr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CellAddress that)) return false;
        return row == that.row && col == that.col && Objects.equals(sheetName, that.sheetName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sheetName, row, col);
    }

    @Override
    public String toString() {
        return toA1();
    }
}
