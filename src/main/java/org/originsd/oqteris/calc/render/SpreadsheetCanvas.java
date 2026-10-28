package org.originsd.oqteris.calc.render;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.originsd.oqteris.calc.model.*;
import org.originsd.oqteris.core.render.FontManager;
import org.originsd.oqteris.ui.ThemeManager;

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
    private double zoom = 1.0;

    // Selection state
    private int activeRow = 0;
    private int activeCol = 0;
    private CellRangeAddress selectedRange = new CellRangeAddress(0, 0, 0, 0);

    // Callbacks
    private BiConsumer<Integer, Integer> onCellSelected;
    private BiConsumer<Integer, Integer> onCellDoubleClicked;
    private BiConsumer<Double, Double> onScrollOffsetsChanged;

    public SpreadsheetCanvas() {
        widthProperty().addListener(e -> render());
        heightProperty().addListener(e -> render());
        ThemeManager.getInstance().darkThemeProperty().addListener(e -> render());

        setupMouseHandlers();
    }

    public void setOnScrollOffsetsChanged(BiConsumer<Double, Double> listener) {
        this.onScrollOffsetsChanged = listener;
    }

    public double getZoom() {
        return zoom;
    }

    public void setZoom(double zoom) {
        this.zoom = Math.max(0.5, Math.min(3.0, zoom));
        render();
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

        setOnScroll(e -> {
            double dx = e.getDeltaX();
            double dy = e.getDeltaY();
            if (e.isShiftDown()) {
                double newX = Math.max(0, scrollX - (dy != 0 ? dy : dx));
                if (onScrollOffsetsChanged != null) {
                    onScrollOffsetsChanged.accept(newX, scrollY);
                } else {
                    setScrollOffsets(newX, scrollY);
                }
            } else {
                double newY = Math.max(0, scrollY - dy);
                double newX = dx != 0 ? Math.max(0, scrollX - dx) : scrollX;
                if (onScrollOffsetsChanged != null) {
                    onScrollOffsetsChanged.accept(newX, newY);
                } else {
                    setScrollOffsets(newX, newY);
                }
            }
        });
    }

    private int[] hitTest(double mouseX, double mouseY) {
        mouseX /= zoom;
        mouseY /= zoom;
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
            if (currentX > mouseX) break; // Fast exit
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
            if (currentY > mouseY) break; // Fast exit
        }

        if (row >= 0 && col >= 0) {
            return new int[]{row, col};
        }
        return null;
    }

    /**
     * Calculates the screen bounds [x, y, width, height] of the specified cell in canvas coordinates.
     */
    public double[] getCellBounds(int row, int col) {
        if (worksheet == null || row < 0 || col < 0) return null;

        double currentX = HEADER_COL_WIDTH - scrollX;
        for (int c = 0; c < col; c++) {
            currentX += worksheet.getColumnWidth(c);
        }
        double colW = worksheet.getColumnWidth(col);

        double currentY = HEADER_ROW_HEIGHT - scrollY;
        for (int r = 0; r < row; r++) {
            currentY += worksheet.getRowHeight(r);
        }
        double rowH = worksheet.getRowHeight(row);

        return new double[]{currentX * zoom, currentY * zoom, colW * zoom, rowH * zoom};
    }

    public void render() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth() / zoom;
        double h = getHeight() / zoom;
        if (w <= 0 || h <= 0 || worksheet == null) return;

        boolean isDark = ThemeManager.getInstance().isDarkTheme();
        Color defaultCellBg = isDark ? Color.rgb(15, 23, 42) : Color.WHITE;
        Color defaultCellText = isDark ? Color.rgb(241, 245, 249) : Color.BLACK;
        Color gridColor = isDark ? Color.rgb(30, 41, 59) : Color.rgb(226, 232, 240);
        Color cornerBg = isDark ? Color.rgb(24, 32, 47) : Color.rgb(240, 240, 240);
        Color cornerBorder = isDark ? Color.rgb(47, 63, 86) : Color.rgb(200, 200, 200);

        gc.save();
        gc.scale(zoom, zoom);
        gc.clearRect(0, 0, w, h);

        // Fill background once for the entire grid surface (dramatically improves rendering performance)
        gc.setFill(defaultCellBg);
        gc.fillRect(HEADER_COL_WIDTH, HEADER_ROW_HEIGHT, w - HEADER_COL_WIDTH, h - HEADER_ROW_HEIGHT);

        // 1. Compute visible rows and columns
        int startCol = 0;
        double accumulatedX = 0;
        while (accumulatedX + worksheet.getColumnWidth(startCol) <= scrollX && startCol < 1000) {
            accumulatedX += worksheet.getColumnWidth(startCol);
            startCol++;
        }
        double xOffset = HEADER_COL_WIDTH + (accumulatedX - scrollX);

        int startRow = 0;
        double accumulatedY = 0;
        while (accumulatedY + worksheet.getRowHeight(startRow) <= scrollY && startRow < 5000) {
            accumulatedY += worksheet.getRowHeight(startRow);
            startRow++;
        }
        double yOffset = HEADER_ROW_HEIGHT + (accumulatedY - scrollY);

        // 2. Draw cell backgrounds, borders and content
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
                if (cell != null) {
                    drawCell(gc, cell, r, c, drawX, drawY, cellW, cellH, defaultCellText);
                }

                drawX += colW;
            }
            drawY += rowH;
        }

        // 3. Draw gridlines
        gc.setStroke(gridColor);
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
        drawSelectionHighlight(gc, startRow, startCol, xOffset, yOffset, isDark);

        // 5. Draw Row & Column Headers
        drawHeaders(gc, startRow, startCol, xOffset, yOffset, w, h, isDark);

        // 6. Top-left corner box
        gc.setFill(cornerBg);
        gc.fillRect(0, 0, HEADER_COL_WIDTH, HEADER_ROW_HEIGHT);
        gc.setStroke(cornerBorder);
        gc.strokeRect(0, 0, HEADER_COL_WIDTH, HEADER_ROW_HEIGHT);

        gc.restore();
    }

    private void drawCell(GraphicsContext gc, Cell cell, int r, int c, double x, double y, double w, double h, Color defaultTextColor) {
        CellStyle style = cell.getStyle();

        // Custom Fill background (only if set!)
        if (style != null && style.getFillColor() != null) {
            gc.setFill(style.getFillColor());
            gc.fillRect(x, y, w, h);
        }

        // Cell borders (if any set)
        if (style != null) {
            Color bColor = style.getBorderColor() != null ? style.getBorderColor() : Color.BLACK;
            if (style.getBorderTop() != CellStyle.BorderType.NONE) {
                gc.setStroke(bColor);
                gc.setLineWidth(1.0);
                gc.strokeLine(x, y, x + w, y);
            }
            if (style.getBorderBottom() != CellStyle.BorderType.NONE) {
                gc.setStroke(bColor);
                gc.setLineWidth(1.0);
                gc.strokeLine(x, y + h, x + w, y + h);
            }
            if (style.getBorderLeft() != CellStyle.BorderType.NONE) {
                gc.setStroke(bColor);
                gc.setLineWidth(1.0);
                gc.strokeLine(x, y, x, y + h);
            }
            if (style.getBorderRight() != CellStyle.BorderType.NONE) {
                gc.setStroke(bColor);
                gc.setLineWidth(1.0);
                gc.strokeLine(x + w, y, x + w, y + h);
            }
        }

        String text = cell.getDisplayText();
        if (text == null || text.isEmpty()) return;

        // Font
        double fontSize = style != null ? style.getFontSize() : 11.0;
        Font font = FontManager.getInstance().getFont(
                style != null ? style.getFontFamily() : "Segoe UI",
                fontSize,
                style != null && style.isBold(),
                style != null && style.isItalic()
        );
        gc.setFont(font);

        Color textColor = (style != null && style.getTextColor() != null && !style.getTextColor().equals(Color.BLACK))
                ? style.getTextColor() : defaultTextColor;
        gc.setFill(textColor);

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

        // Fast text rendering without clip unless text actually overflows cell width
        double approxTextWidth = text.length() * fontSize * 0.6;
        if (approxTextWidth > w - 6) {
            gc.save();
            gc.beginPath();
            gc.rect(x + 1, y + 1, w - 2, h - 2);
            gc.clip();
            gc.fillText(text, textX, textY);
            gc.restore();
        } else {
            gc.fillText(text, textX, textY);
        }
    }

    private void drawSelectionHighlight(GraphicsContext gc, int startRow, int startCol, double xOffset, double yOffset, boolean isDark) {
        if (selectedRange == null) return;

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

        Color selFill = isDark ? Color.rgb(99, 102, 241, 0.22) : Color.rgb(0, 120, 215, 0.18);
        Color selBorder = isDark ? Color.rgb(129, 140, 248) : Color.rgb(0, 120, 215);

        gc.setFill(selFill);
        gc.fillRect(rx, ry, rw, rh);

        gc.setStroke(selBorder);
        gc.setLineWidth(2.0);
        gc.strokeRect(rx, ry, rw, rh);

        gc.setFill(selBorder);
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

    private void drawHeaders(GraphicsContext gc, int startRow, int startCol, double xOffset, double yOffset, double canvasW, double canvasH, boolean isDark) {
        Font headerFont = Font.font("Segoe UI", 11.0);
        gc.setFont(headerFont);
        gc.setTextBaseline(VPos.CENTER);
        gc.setTextAlign(TextAlignment.CENTER);

        Color headerBg = isDark ? Color.rgb(24, 32, 47) : Color.rgb(245, 245, 245);
        Color headerBorder = isDark ? Color.rgb(47, 63, 86) : Color.rgb(215, 215, 215);
        Color headerText = isDark ? Color.rgb(203, 213, 225) : Color.rgb(80, 80, 80);

        Color selectedBg = isDark ? Color.rgb(49, 46, 129) : Color.rgb(220, 235, 252);
        Color selectedBorder = isDark ? Color.rgb(99, 102, 241) : Color.rgb(180, 210, 245);
        Color selectedText = isDark ? Color.rgb(224, 231, 255) : Color.rgb(0, 102, 204);

        // Column Headers (Top)
        double colX = xOffset;
        for (int c = startCol; colX < canvasW && c < 1000; c++) {
            double colW = worksheet.getColumnWidth(c);
            boolean isSelected = selectedRange != null && c >= selectedRange.getFirstCol() && c <= selectedRange.getLastCol();

            gc.setFill(isSelected ? selectedBg : headerBg);
            gc.fillRect(colX, 0, colW, HEADER_ROW_HEIGHT);

            gc.setStroke(isSelected ? selectedBorder : headerBorder);
            gc.strokeRect(colX, 0, colW, HEADER_ROW_HEIGHT);

            gc.setFill(isSelected ? selectedText : headerText);
            gc.fillText(CellAddress.colToLetters(c), colX + (colW / 2.0), HEADER_ROW_HEIGHT / 2.0);

            colX += colW;
        }

        // Row Headers (Left)
        double rowY = yOffset;
        for (int r = startRow; rowY < canvasH && r < 5000; r++) {
            double rowH = worksheet.getRowHeight(r);
            boolean isSelected = selectedRange != null && r >= selectedRange.getFirstRow() && r <= selectedRange.getLastRow();

            gc.setFill(isSelected ? selectedBg : headerBg);
            gc.fillRect(0, rowY, HEADER_COL_WIDTH, rowH);

            gc.setStroke(isSelected ? selectedBorder : headerBorder);
            gc.strokeRect(0, rowY, HEADER_COL_WIDTH, rowH);

            gc.setFill(isSelected ? selectedText : headerText);
            gc.fillText(String.valueOf(r + 1), HEADER_COL_WIDTH / 2.0, rowY + (rowH / 2.0));

            rowY += rowH;
        }
    }
}
