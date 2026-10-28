package org.oqteris.writer.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Row of cells within a document table.
 */
public class DocumentTableRow {
    private final List<DocumentTableCell> cells = new ArrayList<>();
    private double height = 0.0; // 0 = auto

    public DocumentTableRow() {
    }

    public List<DocumentTableCell> getCells() {
        return cells;
    }

    public void addCell(DocumentTableCell cell) {
        if (cell != null) {
            cells.add(cell);
        }
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = Math.max(0.0, height);
    }
}
