package org.originsd.oqteris.writer.ui;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.originsd.oqteris.writer.model.PageSetup;
import org.originsd.oqteris.writer.model.Paragraph;
import org.originsd.oqteris.writer.model.TabStop;

/**
 * Interactive horizontal document ruler displaying margins, scale markings,
 * indent markers and draggable tab stops. Scale and paper area always follow
 * the active document's PageSetup (no hard-coded preset margins).
 */
public class RulerBar extends Region {
    private static final double RULER_HEIGHT = 26.0;
    private final Canvas canvas = new Canvas();
    private final WriterDocumentView view;

    private enum DragTarget {NONE, LEFT_MARGIN, RIGHT_MARGIN, LEFT_INDENT, FIRST_LINE_INDENT, RIGHT_INDENT, TAB}

    private DragTarget dragTarget = DragTarget.NONE;
    private TabStop draggedTabStop = null;

    public RulerBar(WriterDocumentView view) {
        this.view = view;
        getChildren().add(canvas);
        setPrefHeight(RULER_HEIGHT);
        setMinHeight(RULER_HEIGHT);
        setMaxHeight(RULER_HEIGHT);

        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);

        widthProperty().addListener((obs, oldW, newW) -> {
            canvas.setWidth(newW.doubleValue());
            redraw();
        });
    }

    public void redraw() {
        double w = getWidth() > 0 ? getWidth() : canvas.getWidth();
        double h = RULER_HEIGHT;
        if (w <= 0) return;
        canvas.setWidth(w);
        canvas.setHeight(h);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        PageSetup setup = view.getWriterDocument().getPageSetup();
        double zoom = view.getCurrentZoom();
        double pageW = setup.getPageWidth() * zoom;
        double marginLeft = setup.getMarginLeft() * zoom;
        double marginRight = setup.getMarginRight() * zoom;
        double printableW = setup.getPrintableWidth() * zoom;

        // Document page is horizontally centered in the view
        double pageX = (w - pageW) / 2.0;

        // 1. Background outside paper
        gc.setFill(Color.rgb(241, 245, 249));
        gc.fillRect(0, 0, w, h);

        // 2. Paper area
        gc.setFill(Color.rgb(226, 232, 240));
        gc.fillRect(pageX, 0, marginLeft, h);                          // left margin
        gc.setFill(Color.WHITE);
        gc.fillRect(pageX + marginLeft, 0, printableW, h);             // printable
        gc.setFill(Color.rgb(226, 232, 240));
        gc.fillRect(pageX + pageW - marginRight, 0, marginRight, h);   // right margin

        // Bottom border
        gc.setStroke(Color.rgb(203, 213, 225));
        gc.setLineWidth(1.0);
        gc.strokeLine(0, h - 0.5, w, h - 0.5);

        // Margin separators
        gc.setStroke(Color.rgb(148, 163, 184));
        gc.strokeLine(pageX + marginLeft, 0, pageX + marginLeft, h);
        gc.strokeLine(pageX + pageW - marginRight, 0, pageX + pageW - marginRight, h);

        // 3. Ticks & numbers across the full page (including margins) so the scale matches paper
        gc.setFont(Font.font("Segoe UI", 8.0));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.TOP);
        gc.setFill(Color.rgb(71, 85, 105));
        gc.setStroke(Color.rgb(148, 163, 184));
        gc.setLineWidth(0.75);

        double inchPt = 72.0 * zoom;
        // Start from 0 at left edge of paper
        int maxInches = (int) Math.ceil(setup.getPageWidth() / 72.0) + 1;
        for (int i = 0; i <= maxInches; i++) {
            double tickX = pageX + (i * inchPt);
            if (tickX > pageX + pageW + 1) break;

            if (i > 0 && tickX < pageX + pageW - 8) {
                gc.fillText(String.valueOf(i), tickX, 2);
            }
            gc.strokeLine(tickX, h - 8, tickX, h);

            for (int q = 1; q <= 3; q++) {
                double subX = tickX + (q * (inchPt / 4.0));
                if (subX >= pageX + pageW) break;
                double tickH = (q == 2) ? 5.5 : 3.5;
                gc.strokeLine(subX, h - tickH, subX, h);
            }
        }

        // 4. Margin markers (draggable triangles)
        drawMarginMarker(gc, pageX + marginLeft, true);
        drawMarginMarker(gc, pageX + pageW - marginRight, false);

        // 5. Paragraph indent markers (for active paragraph)
        Paragraph activePara = view.getActiveParagraph();
        if (activePara != null) {
            double leftIndentX = pageX + marginLeft + activePara.getLeftIndent() * zoom;
            double firstLineX = leftIndentX + activePara.getFirstLineIndent() * zoom;
            double rightIndentX = pageX + pageW - marginRight - activePara.getRightIndent() * zoom;

            // First-line indent (upper triangle)
            drawIndentMarker(gc, firstLineX, true, Color.rgb(37, 99, 235));
            // Hanging / left indent (lower triangle)
            drawIndentMarker(gc, leftIndentX, false, Color.rgb(15, 23, 42));
            // Right indent
            drawIndentMarker(gc, rightIndentX, false, Color.rgb(15, 23, 42));

            // 6. Tab stops
            for (TabStop ts : activePara.getTabStops()) {
                double tx = pageX + marginLeft + (ts.getPosition() * zoom);
                drawTabStopMarker(gc, tx, ts);
            }
        }
    }

    private void drawMarginMarker(GraphicsContext gc, double x, boolean isLeft) {
        gc.setFill(Color.rgb(15, 23, 42));
        gc.beginPath();
        gc.moveTo(x - 5, 1);
        gc.lineTo(x + 5, 1);
        gc.lineTo(x, 9);
        gc.closePath();
        gc.fill();
    }

    private void drawIndentMarker(GraphicsContext gc, double x, boolean upper, Color color) {
        gc.setFill(color);
        gc.beginPath();
        if (upper) {
            gc.moveTo(x - 4, 10);
            gc.lineTo(x + 4, 10);
            gc.lineTo(x, 16);
        } else {
            gc.moveTo(x - 4, 22);
            gc.lineTo(x + 4, 22);
            gc.lineTo(x, 16);
        }
        gc.closePath();
        gc.fill();
    }

    private void drawTabStopMarker(GraphicsContext gc, double x, TabStop ts) {
        gc.setStroke(Color.rgb(37, 99, 235));
        gc.setLineWidth(1.5);
        double yTop = 8.0;
        double yBottom = RULER_HEIGHT - 2.0;

        switch (ts.getAlignment()) {
            case RIGHT -> {
                gc.strokeLine(x, yTop, x, yBottom);
                gc.strokeLine(x - 6, yBottom, x, yBottom);
            }
            case CENTER -> {
                gc.strokeLine(x, yTop, x, yBottom);
                gc.strokeLine(x - 4, yBottom, x + 4, yBottom);
            }
            case DECIMAL -> {
                gc.strokeLine(x, yTop, x, yBottom);
                gc.strokeLine(x - 3, yBottom, x + 3, yBottom);
                gc.strokeOval(x - 1.5, yBottom - 4, 3, 3);
            }
            default -> { // LEFT
                gc.strokeLine(x, yTop, x, yBottom);
                gc.strokeLine(x, yBottom, x + 6, yBottom);
            }
        }
    }

    private void handleMousePressed(MouseEvent e) {
        PageSetup setup = view.getWriterDocument().getPageSetup();
        double zoom = view.getCurrentZoom();
        double pageW = setup.getPageWidth() * zoom;
        double pageX = (getWidth() - pageW) / 2.0;
        double marginLeft = setup.getMarginLeft() * zoom;
        double marginRight = setup.getMarginRight() * zoom;
        double printableW = setup.getPrintableWidth() * zoom;

        Paragraph p = view.getActiveParagraph();
        double clickX = e.getX();
        dragTarget = DragTarget.NONE;
        draggedTabStop = null;

        // Hit-test order: tabs → indents → margins
        if (p != null) {
            for (TabStop ts : p.getTabStops()) {
                double tx = pageX + marginLeft + (ts.getPosition() * zoom);
                if (Math.abs(clickX - tx) <= 7.0) {
                    draggedTabStop = ts;
                    dragTarget = DragTarget.TAB;
                    return;
                }
            }

            double leftIndentX = pageX + marginLeft + p.getLeftIndent() * zoom;
            double firstLineX = leftIndentX + p.getFirstLineIndent() * zoom;
            double rightIndentX = pageX + pageW - marginRight - p.getRightIndent() * zoom;

            if (Math.abs(clickX - firstLineX) <= 6) {
                dragTarget = DragTarget.FIRST_LINE_INDENT;
                return;
            }
            if (Math.abs(clickX - leftIndentX) <= 6) {
                dragTarget = DragTarget.LEFT_INDENT;
                return;
            }
            if (Math.abs(clickX - rightIndentX) <= 6) {
                dragTarget = DragTarget.RIGHT_INDENT;
                return;
            }
        }

        if (Math.abs(clickX - (pageX + marginLeft)) <= 6) {
            dragTarget = DragTarget.LEFT_MARGIN;
            return;
        }
        if (Math.abs(clickX - (pageX + pageW - marginRight)) <= 6) {
            dragTarget = DragTarget.RIGHT_MARGIN;
            return;
        }

        // Click in printable area → add new tab stop
        if (p != null && clickX >= pageX + marginLeft && clickX <= pageX + marginLeft + printableW) {
            double posPt = (clickX - pageX - marginLeft) / zoom;
            TabStop.Alignment align = (posPt > setup.getPrintableWidth() * 0.7)
                    ? TabStop.Alignment.RIGHT : TabStop.Alignment.LEFT;
            draggedTabStop = new TabStop(posPt, align);
            p.addTabStop(draggedTabStop);
            view.getDocument().setModified(true);
            view.relayout();
            redraw();
            dragTarget = DragTarget.TAB;
        }
    }

    private void handleMouseDragged(MouseEvent e) {
        if (dragTarget == DragTarget.NONE) return;

        PageSetup setup = view.getWriterDocument().getPageSetup();
        double zoom = view.getCurrentZoom();
        double pageW = setup.getPageWidth() * zoom;
        double pageX = (getWidth() - pageW) / 2.0;
        double marginLeft = setup.getMarginLeft() * zoom;

        double posFromLeftMargin = (e.getX() - pageX - marginLeft) / zoom;
        Paragraph p = view.getActiveParagraph();

        switch (dragTarget) {
            case LEFT_MARGIN -> {
                double newLeft = Math.max(0, Math.min((e.getX() - pageX) / zoom,
                        setup.getPageWidth() - setup.getMarginRight() - 50));
                setup.setMarginLeft(newLeft);
                view.getDocument().setModified(true);
                view.relayout();
            }
            case RIGHT_MARGIN -> {
                double fromRight = (pageX + pageW - e.getX()) / zoom;
                double newRight = Math.max(0, Math.min(fromRight,
                        setup.getPageWidth() - setup.getMarginLeft() - 50));
                setup.setMarginRight(newRight);
                view.getDocument().setModified(true);
                view.relayout();
            }
            case LEFT_INDENT -> {
                if (p != null) {
                    p.setLeftIndent(Math.max(0, Math.min(posFromLeftMargin, setup.getPrintableWidth() - 20)));
                    view.getDocument().setModified(true);
                    view.relayout();
                }
            }
            case FIRST_LINE_INDENT -> {
                if (p != null) {
                    double rel = posFromLeftMargin - p.getLeftIndent();
                    p.setFirstLineIndent(Math.max(-p.getLeftIndent(), Math.min(rel, setup.getPrintableWidth() - 20)));
                    view.getDocument().setModified(true);
                    view.relayout();
                }
            }
            case RIGHT_INDENT -> {
                if (p != null) {
                    double fromRight = (pageX + pageW - setup.getMarginRight() * zoom - e.getX()) / zoom;
                    p.setRightIndent(Math.max(0, Math.min(fromRight, setup.getPrintableWidth() - 20)));
                    view.getDocument().setModified(true);
                    view.relayout();
                }
            }
            case TAB -> {
                if (draggedTabStop != null) {
                    draggedTabStop.setPosition(Math.max(0, Math.min(posFromLeftMargin, setup.getPrintableWidth())));
                    view.getDocument().setModified(true);
                    view.relayout();
                }
            }
            default -> {
            }
        }
        redraw();
    }

    private void handleMouseReleased(MouseEvent e) {
        if (dragTarget == DragTarget.TAB && draggedTabStop != null) {
            // Drag off the ruler vertically → remove tab stop
            if (e.getY() < -12 || e.getY() > RULER_HEIGHT + 18) {
                Paragraph p = view.getActiveParagraph();
                if (p != null) {
                    p.removeTabStop(draggedTabStop);
                    view.getDocument().setModified(true);
                    view.relayout();
                }
            }
        }
        dragTarget = DragTarget.NONE;
        draggedTabStop = null;
        redraw();
    }
}