package org.oqteris.calc.render;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.oqteris.calc.model.*;
import org.oqteris.core.render.FontManager;

import java.util.function.BiConsumer;

/**
 * High-performance virtualized 2D canvas rendering the active worksheet grid,
 * cells, headers, borders, selection rectangles, and freeze panes.
 */
public class SpreadsheetCanvas extends Canvas {
    private static final double HEADER_ROW_HEIGHT = 24.0;
    private static final double HEADER_COL_WIDTH = 45.0;

    private Worksheet worksheet;
    private double scrollX = 0;
    private double scrollY = 0;

    // Selection state
    private int activeRow = 0;
    private int activeCol = 0;
    private CellRangeAddress selectedRange = new CellRangeAddress(0, 0, 0, 0);

    // Callbacks
    private BiConsumer<Integer, Integer> onCellSelected;
    private BiConsumer<Integer, Integer> onCellDoubleClicked;

    public SpreadsheetCanvas() {
        widthProperty().addListener(e -> render());
        heightProperty().addListener(e -> render());

        setupMouseHandlers();
    }

    public void setWorksheet(Worksheet worksheet) {
        this.worksheet = worksheet;
        render();
    }

    public Worksheet getWorksheet() {
        return worksheet;
    }

    public void setScrollOffsets(double x, double y) {
        this.scrollX = Math.max(0, x);
        this.scrollY = Math.max(0, y);
        render();
    }

    public double getScrollX() {
        return scrollX;
    }

    public double getScrollY() {
        return scrollY;
    }

    public int getActiveRow() {
        return activeRow;
    }

    public int getActiveCol() {
        return activeCol;
    }

    public CellRangeAddress getSelectedRange() {
        return selectedRange;
    }

    public void setActiveCell(int row, int col) {
        this.activeRow = Math.max(0, row);
        this.activeCol = Math.max(0, col);
        this.selectedRange = new CellRangeAddress(activeRow, activeRow, activeCol, activeCol);
        if (onCellSelected != null) {
            onCellSelected.accept(activeRow, activeCol);
        }
        render();
    }

    public void setSelectedRange(CellRangeAddress range) {
        if (range != null) {
            this.selectedRange = range;
            render();
        }
    }

    public void setOnCellSelected(BiConsumer<Integer, Integer> listener) {
        this.onCellSelected = listener;
    }

    public void setOnCellDoubleClicked(BiConsumer<Integer, Integer> listener) {
        this.onCellDoubleClicked = listener;
    }

    private void setupMouseHandlers() {
        setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                int[] coords = hitTest(e.getX(), e.getY());
                if (coords != null) {
                    if (e.getClickCount() == 2) {
                        if (onCellDoubleClicked != null) {
                            onCellDoubleClicked.accept(coords[0], coords[1]);
                        }
                    } else {
                        setActiveCell(coords[0], coords[1]);
                    }
                }
            }
        });

        setOnMouseDragged(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                int[] coords = hitTest(e.getX(), e.getY());
                if (coords != null) {
                    selectedRange = new CellRangeAddress(activeRow, coords[0], activeCol, coords[1]);
                    render();
                }
            }
        });
    }

    private int[] hitTest(double mouseX, double mouseY) {
        if (worksheet == null || mouseX < HEADER_COL_WIDTH || mouseY < HEADER_ROW_HEIGHT) {
            return null;
        }

        // Find column
        double currentX = HEADER_COL_WIDTH - scrollX;
        int col = -1;
        for (int c = 0; c < 1000; c++) {
            double w = worksheet.getColumnWidth(c);
            if (mouseX >= currentX && mouseX < currentX + w) {
                col = c;
                break;
            }
            currentX += w;
            if (currentX > getWidth()) break;
        }

        // Find row
        double currentY = HEADER_ROW_HEIGHT - scrollY;
        int row = -1;
        for (int r = 0; r < 5000; r++) {
            double h = worksheet.getRowHeight(r);
            if (mouseY >= currentY && mouseY < currentY + h) {
                row = r;
                break;
            }
            currentY += h;
            if (currentY > getHeight()) break;
        }

        if (row >= 0 && col >= 0) {
            return new int[]{row, col};
        }
        return null;
    }

    public void render() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0 || worksheet == null) return;

        gc.clearRect(0, 0, w, h);

        // 1. Compute visible rows and columns
        int startCol = 0;
        double xOffset = 0;
        double accumulatedX = 0;
        while (accumulatedX + worksheet.getColumnWidth(startCol) <= scrollX && startCol < 1000) {
            accumulatedX += worksheet.getColumnWidth(startCol);
            startCol++;
        }
        xOffset = HEADER_COL_WIDTH + (accumulatedX - scrollX);

        int startRow = 0;
        double yOffset = 0;
        double accumulatedY = 0;
        while (accumulatedY + worksheet.getRowHeight(startRow) <= scrollY && startRow < 5000) {
            accumulatedY += worksheet.getRowHeight(startRow);
            startRow++;
        }
        yOffset = HEADER_ROW_HEIGHT + (accumulatedY - scrollY);

        // 2. Draw cell backgrounds and content
        double drawY = yOffset;
        for (int r = startRow; drawY < h && r < 5000; r++) {
            double rowH = worksheet.getRowHeight(r);
            double drawX = xOffset;

            for (int c = startCol; drawX < w && c < 1000; c++) {
                double colW = worksheet.getColumnWidth(c);

                // Check merged
                if (worksheet.isMergedSlave(r, c)) {
                    drawX += colW;
                    continue;
                }

                CellRangeAddress merged = worksheet.getMergedRangeAt(r, c);
                double cellW = colW;
                double cellH = rowH;
                if (merged != null && worksheet.isMergedMaster(r, c)) {
                    cellW = 0;
                    for (int mc = merged.getFirstCol(); mc <= merged.getLastCol(); mc++) {
                        cellW += worksheet.getColumnWidth(mc);
                    }
                    cellH = 0;
                    for (int mr = merged.getFirstRow(); mr <= merged.getLastRow(); mr++) {
                        cellH += worksheet.getRowHeight(mr);
                    }
                }

                Cell cell = worksheet.getCell(r, c);
                drawCell(gc, cell, r, c, drawX, drawY, cellW, cellH);

                drawX += colW;
            }
            drawY += rowH;
        }

        // 3. Draw gridlines
        gc.setStroke(Color.rgb(225, 225, 225));
        gc.setLineWidth(1.0);

        // Vertical grid lines
        double lineX = xOffset;
        for (int c = startCol; lineX < w && c < 1000; c++) {
            lineX += worksheet.getColumnWidth(c);
            gc.strokeLine(lineX, HEADER_ROW_HEIGHT, lineX, h);
        }

        // Horizontal grid lines
        double lineY = yOffset;
        for (int r = startRow; lineY < h && r < 5000; r++) {
            lineY += worksheet.getRowHeight(r);
            gc.strokeLine(HEADER_COL_WIDTH, lineY, w, lineY);
        }

        // 4. Draw selection highlight
        drawSelectionHighlight(gc, startRow, startCol, xOffset, yOffset);

        // 5. Draw Row & Column Headers
        drawHeaders(gc, startRow, startCol, xOffset, yOffset, w, h);

        // 6. Top-left corner box
        gc.setFill(Color.rgb(240, 240, 240));
        gc.fillRect(0, 0, HEADER_COL_WIDTH, HEADER_ROW_HEIGHT);
        gc.setStroke(Color.rgb(200, 200, 200));
        gc.strokeRect(0, 0, HEADER_COL_WIDTH, HEADER_ROW_HEIGHT);
    }

    private void drawCell(GraphicsContext gc, Cell cell, int r, int c, double x, double y, double w, double h) {
        CellStyle style = cell != null ? cell.getStyle() : null;

        // Fill background
        if (style != null && style.getFillColor() != null) {
            gc.setFill(style.getFillColor());
            gc.fillRect(x, y, w, h);
        } else {
            gc.setFill(Color.WHITE);
            gc.fillRect(x, y, w, h);
        }

        if (cell == null) return;

        String text = cell.getDisplayText();
        if (text == null || text.isEmpty()) return;

        // Font
        Font font = FontManager.getInstance().getFont(
                style != null ? style.getFontFamily() : "Segoe UI",
                style != null ? style.getFontSize() : 11.0,
                style != null && style.isBold(),
                style != null && style.isItalic()
        );
        gc.setFont(font);
        gc.setFill(style != null ? style.getTextColor() : Color.BLACK);

        // Alignment
        CellStyle.Alignment align = style != null ? style.getAlignment() : CellStyle.Alignment.GENERAL;
        if (align == CellStyle.Alignment.GENERAL) {
            align = cell.isNumeric() ? CellStyle.Alignment.RIGHT : CellStyle.Alignment.LEFT;
        }

        double textX = x + 4;
        if (align == CellStyle.Alignment.RIGHT) {
            gc.setTextAlign(TextAlignment.RIGHT);
            textX = x + w - 4;
        } else if (align == CellStyle.Alignment.CENTER) {
            gc.setTextAlign(TextAlignment.CENTER);
            textX = x + (w / 2.0);
        } else {
            gc.setTextAlign(TextAlignment.LEFT);
            textX = x + 4;
        }

        gc.setTextBaseline(VPos.CENTER);
        double textY = y + (h / 2.0);

        // Clip text to cell box
        gc.save();
        gc.beginPath();
        gc.rect(x + 1, y + 1, w - 2, h - 2);
        gc.clip();
        gc.fillText(text, textX, textY);
        gc.restore();
    }

    private void drawSelectionHighlight(GraphicsContext gc, int startRow, int startCol, double xOffset, double yOffset) {
        if (selectedRange == null) return;

        // Calculate rect for selectedRange
        double rx = getColX(selectedRange.getFirstCol(), startCol, xOffset);
        double ry = getRowY(selectedRange.getFirstRow(), startRow, yOffset);
        double rw = 0;
        for (int c = selectedRange.getFirstCol(); c <= selectedRange.getLastCol(); c++) {
            rw += worksheet.getColumnWidth(c);
        }
        double rh = 0;
        for (int r = selectedRange.getFirstRow(); r <= selectedRange.getLastRow(); r++) {
            rh += worksheet.getRowHeight(r);
        }

        // Selection fill
        gc.setFill(Color.rgb(0, 120, 215, 0.18));
        gc.fillRect(rx, ry, rw, rh);

        // Selection border
        gc.setStroke(Color.rgb(0, 120, 215));
        gc.setLineWidth(2.0);
        gc.strokeRect(rx, ry, rw, rh);

        // Fill handle (bottom-right square)
        gc.setFill(Color.rgb(0, 120, 215));
        gc.fillRect(rx + rw - 3, ry + rh - 3, 6, 6);
    }

    private double getColX(int col, int startCol, double xOffset) {
        double currentX = xOffset;
        if (col >= startCol) {
            for (int c = startCol; c < col; c++) {
                currentX += worksheet.getColumnWidth(c);
            }
        } else {
            for (int c = col; c < startCol; c++) {
                currentX -= worksheet.getColumnWidth(c);
            }
        }
        return currentX;
    }

    private double getRowY(int row, int startRow, double yOffset) {
        double currentY = yOffset;
        if (row >= startRow) {
            for (int r = startRow; r < row; r++) {
                currentY += worksheet.getRowHeight(r);
            }
        } else {
            for (int r = row; r < startRow; r++) {
                currentY -= worksheet.getRowHeight(r);
            }
        }
        return currentY;
    }

    private void drawHeaders(GraphicsContext gc, int startRow, int startCol, double xOffset, double yOffset, double canvasW, double canvasH) {
        Font headerFont = Font.font("Segoe UI", 11.0);
        gc.setFont(headerFont);
        gc.setTextBaseline(VPos.CENTER);
        gc.setTextAlign(TextAlignment.CENTER);

        // Column Headers (Top)
        double colX = xOffset;
        for (int c = startCol; colX < canvasW && c < 1000; c++) {
            double colW = worksheet.getColumnWidth(c);
            boolean isSelected = selectedRange != null && c >= selectedRange.getFirstCol() && c <= selectedRange.getLastCol();

            gc.setFill(isSelected ? Color.rgb(220, 235, 252) : Color.rgb(245, 245, 245));
            gc.fillRect(colX, 0, colW, HEADER_ROW_HEIGHT);

            gc.setStroke(Color.rgb(215, 215, 215));
            gc.strokeRect(colX, 0, colW, HEADER_ROW_HEIGHT);

            gc.setFill(isSelected ? Color.rgb(0, 102, 204) : Color.rgb(80, 80, 80));
            gc.fillText(CellAddress.colToLetters(c), colX + (colW / 2.0), HEADER_ROW_HEIGHT / 2.0);

            colX += colW;
        }

        // Row Headers (Left)
        double rowY = yOffset;
        for (int r = startRow; rowY < canvasH && r < 5000; r++) {
            double rowH = worksheet.getRowHeight(r);
            boolean isSelected = selectedRange != null && r >= selectedRange.getFirstRow() && r <= selectedRange.getLastRow();

            gc.setFill(isSelected ? Color.rgb(220, 235, 252) : Color.rgb(245, 245, 245));
            gc.fillRect(0, rowY, HEADER_COL_WIDTH, rowH);

            gc.setStroke(Color.rgb(215, 215, 215));
            gc.strokeRect(0, rowY, HEADER_COL_WIDTH, rowH);

            gc.setFill(isSelected ? Color.rgb(0, 102, 204) : Color.rgb(80, 80, 80));
            gc.fillText(String.valueOf(r + 1), HEADER_COL_WIDTH / 2.0, rowY + (rowH / 2.0));

            rowY += rowH;
        }
    }
}
