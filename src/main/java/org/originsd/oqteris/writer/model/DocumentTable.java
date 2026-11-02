package org.originsd.oqteris.writer.model;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Table element containing rows, columns, borders, and cells.
 */
public class DocumentTable implements DocumentElement {
    public enum Alignment { LEFT, CENTER, RIGHT }

    private final List<DocumentTableRow> rows = new ArrayList<>();
    private int columnCount = 1;
    private double[] columnWidths = null;
    private Color borderColor = Color.rgb(200, 200, 200);
    private double borderWidth = 1.0;
    private Alignment alignment = Alignment.LEFT;

    public DocumentTable() {
    }

    public DocumentTable(int numRows, int numCols) {
        this.columnCount = Math.max(1, numCols);
        for (int r = 0; r < numRows; r++) {
            DocumentTableRow row = new DocumentTableRow();
            for (int c = 0; c < numCols; c++) {
                row.addCell(new DocumentTableCell());
            }
            rows.add(row);
        }
    }

    @Override
    public ElementType getElementType() {
        return ElementType.TABLE;
    }

    public List<DocumentTableRow> getRows() {
        return rows;
    }

    public void addRow(DocumentTableRow row) {
        if (row != null) {
            rows.add(row);
            if (row.getCells().size() > columnCount) {
                columnCount = row.getCells().size();
            }
        }
    }

    public int getColumnCount() {
        return columnCount;
    }

    public void setColumnCount(int columnCount) {
        this.columnCount = Math.max(1, columnCount);
    }

    public double[] getColumnWidths() {
        return columnWidths;
    }

    public void setColumnWidths(double[] columnWidths) {
        this.columnWidths = columnWidths;
    }

    public Color getBorderColor() {
        return borderColor;
    }

    public void setBorderColor(Color borderColor) {
        this.borderColor = borderColor != null ? borderColor : Color.rgb(200, 200, 200);
    }

    public double getBorderWidth() {
        return borderWidth;
    }

    public void setBorderWidth(double borderWidth) {
        this.borderWidth = Math.max(0.5, borderWidth);
    }

    public Alignment getAlignment() {
        return alignment;
    }

    public void setAlignment(Alignment alignment) {
        this.alignment = alignment != null ? alignment : Alignment.LEFT;
    }

    public DocumentTableCell getCell(int row, int col) {
        if (row >= 0 && row < rows.size()) {
            DocumentTableRow r = rows.get(row);
            if (col >= 0 && col < r.getCells().size()) {
                return r.getCells().get(col);
            }
        }
        return null;
    }

    public void insertRow(int index) {
        DocumentTableRow newRow = new DocumentTableRow();
        for (int c = 0; c < columnCount; c++) {
            newRow.addCell(new DocumentTableCell());
        }
        if (index >= 0 && index <= rows.size()) {
            rows.add(index, newRow);
        } else {
            rows.add(newRow);
        }
    }

    public void removeRow(int index) {
        if (rows.size() > 1 && index >= 0 && index < rows.size()) {
            rows.remove(index);
        }
    }

    public void insertColumn(int colIndex) {
        int targetCol = Math.max(0, Math.min(columnCount, colIndex));
        for (DocumentTableRow r : rows) {
            DocumentTableCell cell = new DocumentTableCell();
            if (targetCol <= r.getCells().size()) {
                r.getCells().add(targetCol, cell);
            } else {
                r.addCell(cell);
            }
        }
        columnCount++;
        columnWidths = null; // reset to recalculate proportionally
    }

    public void removeColumn(int colIndex) {
        if (columnCount > 1 && colIndex >= 0 && colIndex < columnCount) {
            for (DocumentTableRow r : rows) {
                if (colIndex < r.getCells().size()) {
                    r.getCells().remove(colIndex);
                }
            }
            columnCount--;
            columnWidths = null;
        }
    }
}
