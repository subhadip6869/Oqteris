package org.originsd.oqteris.presentation.model;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Slide shape representing a structured data table.
 */
public class TableShape extends SlideShape {
    private final int rows;
    private final int cols;
    private final List<List<String>> cellData = new ArrayList<>();

    public TableShape(int rows, int cols, double x, double y, double width, double height) {
        super(ShapeType.TABLE, x, y, width, height);
        this.rows = Math.max(1, rows);
        this.cols = Math.max(1, cols);

        setFillColor(Color.WHITE);
        setStrokeColor(Color.rgb(180, 180, 180));

        for (int r = 0; r < this.rows; r++) {
            List<String> row = new ArrayList<>();
            for (int c = 0; c < this.cols; c++) {
                row.add(r == 0 ? "Header " + (c + 1) : "Cell " + (r + 1) + "," + (c + 1));
            }
            cellData.add(row);
        }
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public String getCell(int r, int c) {
        if (r >= 0 && r < rows && c >= 0 && c < cols) {
            return cellData.get(r).get(c);
        }
        return "";
    }

    public void setCell(int r, int c, String value) {
        if (r >= 0 && r < rows && c >= 0 && c < cols) {
            cellData.get(r).set(c, value != null ? value : "");
        }
    }

    @Override
    public SlideShape copy() {
        TableShape s = new TableShape(rows, cols, getX(), getY(), getWidth(), getHeight());
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                s.setCell(r, c, getCell(r, c));
            }
        }
        s.setFillColor(getFillColor());
        s.setStrokeColor(getStrokeColor());
        s.setStrokeWidth(getStrokeWidth());
        s.setRotation(getRotation());
        return s;
    }
}
