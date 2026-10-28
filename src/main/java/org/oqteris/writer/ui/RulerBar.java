package org.oqteris.writer.ui;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.oqteris.writer.model.PageSetup;
import org.oqteris.writer.model.Paragraph;
import org.oqteris.writer.model.TabStop;

/**
 * Interactive horizontal document ruler displaying margins, scale markings, and draggable tab stops.
 */
public class RulerBar extends Region {
    private static final double RULER_HEIGHT = 24.0;
    private final Canvas canvas = new Canvas();
    private final WriterDocumentView view;
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

        // The document page in WriterDocumentView is horizontally centered:
        double pageX = (w - pageW) / 2.0;

        // 1. Background outside paper
        gc.setFill(Color.rgb(241, 245, 249)); // #f1f5f9
        gc.fillRect(0, 0, w, h);

        // 2. Paper Area
        // Left margin (shaded)
        gc.setFill(Color.rgb(226, 232, 240)); // #e2e8f0
        gc.fillRect(pageX, 0, marginLeft, h);

        // Printable body (pure white)
        gc.setFill(Color.WHITE);
        gc.fillRect(pageX + marginLeft, 0, printableW, h);

        // Right margin (shaded)
        gc.setFill(Color.rgb(226, 232, 240));
        gc.fillRect(pageX + pageW - marginRight, 0, marginRight, h);

        // Bottom border line
        gc.setStroke(Color.rgb(203, 213, 225)); // #cbd5e1
        gc.setLineWidth(1.0);
        gc.strokeLine(0, h - 0.5, w, h - 0.5);

        // Margin separator lines
        gc.setStroke(Color.rgb(148, 163, 184)); // #94a3b8
        gc.strokeLine(pageX + marginLeft, 0, pageX + marginLeft, h);
        gc.strokeLine(pageX + pageW - marginRight, 0, pageX + pageW - marginRight, h);

        // 3. Ticks and Numbers across printable area
        gc.setFont(Font.font("Segoe UI", 8.5));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFill(Color.rgb(71, 85, 105)); // #475569
        gc.setStroke(Color.rgb(148, 163, 184));
        gc.setLineWidth(0.8);

        double inchPt = 72.0 * zoom;
        int maxInches = (int) Math.ceil(setup.getPrintableWidth() / 72.0);

        for (int i = 0; i <= maxInches; i++) {
            double tickX = pageX + marginLeft + (i * inchPt);
            if (tickX > pageX + pageW - marginRight + 0.5) break;

            // Major tick & number
            if (i > 0 && tickX < pageX + pageW - marginRight - 10) {
                gc.fillText(String.valueOf(i), tickX, h / 2.0);
            }
            gc.strokeLine(tickX, h - 7, tickX, h);

            // Sub-ticks (every 1/4 inch = 18 pt * zoom)
            for (int q = 1; q <= 3; q++) {
                double subX = tickX + (q * (inchPt / 4.0));
                if (subX >= pageX + pageW - marginRight) break;
                double tickH = (q == 2) ? 5.0 : 3.0; // half-inch vs quarter-inch
                gc.strokeLine(subX, h - tickH, subX, h);
            }
        }

        // 4. Margin markers (left & right indent pointers)
        drawMarginMarker(gc, pageX + marginLeft, true);
        drawMarginMarker(gc, pageX + pageW - marginRight, false);

        // 5. Paragraph Tab Stops
        Paragraph activePara = view.getActiveParagraph();
        if (activePara != null) {
            for (TabStop ts : activePara.getTabStops()) {
                double tx = pageX + marginLeft + (ts.getPosition() * zoom);
                drawTabStopMarker(gc, tx, ts);
            }
        }
    }

    private void drawMarginMarker(GraphicsContext gc, double x, boolean isLeft) {
        gc.setFill(Color.rgb(15, 23, 42)); // dark slate
        gc.beginPath();
        if (isLeft) {
            gc.moveTo(x - 4, 2);
            gc.lineTo(x + 4, 2);
            gc.lineTo(x, 8);
        } else {
            gc.moveTo(x - 4, 2);
            gc.lineTo(x + 4, 2);
            gc.lineTo(x, 8);
        }
        gc.closePath();
        gc.fill();
    }

    private void drawTabStopMarker(GraphicsContext gc, double x, TabStop ts) {
        gc.setStroke(Color.rgb(37, 99, 235)); // Vibrant blue #2563eb
        gc.setLineWidth(1.6);
        double yTop = 7.0;
        double yBottom = RULER_HEIGHT - 2.0;

        switch (ts.getAlignment()) {
            case RIGHT -> {
                // Right tab stop symbol: ⅃ (vertical stem with horizontal arm extending left)
                gc.strokeLine(x, yTop, x, yBottom);
                gc.strokeLine(x - 6, yBottom, x, yBottom);
            }
            case CENTER -> {
                // Center tab stop symbol: ┴ (vertical stem with horizontal bar centered)
                gc.strokeLine(x, yTop, x, yBottom);
                gc.strokeLine(x - 4, yBottom, x + 4, yBottom);
            }
            default -> {
                // Left tab stop symbol: L (vertical stem with horizontal arm extending right)
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
        double printableW = setup.getPrintableWidth() * zoom;

        Paragraph p = view.getActiveParagraph();
        if (p == null) return;

        double clickX = e.getX();
        draggedTabStop = null;

        // Check if clicking existing tab stop
        for (TabStop ts : p.getTabStops()) {
            double tx = pageX + marginLeft + (ts.getPosition() * zoom);
            if (Math.abs(clickX - tx) <= 8.0) {
                draggedTabStop = ts;
                break;
            }
        }

        // If clicking printable area without an existing tab stop, add a new one!
        if (draggedTabStop == null && clickX >= pageX + marginLeft && clickX <= pageX + marginLeft + printableW) {
            double posPt = (clickX - pageX - marginLeft) / zoom;
            // If placed near right margin (> 70% of printable width), default to RIGHT tab stop
            TabStop.Alignment align = (posPt > setup.getPrintableWidth() * 0.7) ? TabStop.Alignment.RIGHT : TabStop.Alignment.LEFT;
            draggedTabStop = new TabStop(posPt, align);
            p.addTabStop(draggedTabStop);
            view.getDocument().setModified(true);
            view.relayout();
            redraw();
        }
    }

    private void handleMouseDragged(MouseEvent e) {
        if (draggedTabStop == null) return;
        PageSetup setup = view.getWriterDocument().getPageSetup();
        double zoom = view.getCurrentZoom();
        double pageW = setup.getPageWidth() * zoom;
        double pageX = (getWidth() - pageW) / 2.0;
        double marginLeft = setup.getMarginLeft() * zoom;

        double newPosPt = (e.getX() - pageX - marginLeft) / zoom;
        draggedTabStop.setPosition(Math.max(0.0, Math.min(newPosPt, setup.getPrintableWidth())));
        view.getDocument().setModified(true);
        view.relayout();
        redraw();
    }

    private void handleMouseReleased(MouseEvent e) {
        if (draggedTabStop != null) {
            // If dragged outside ruler vertically, remove the tab stop
            if (e.getY() < -15 || e.getY() > RULER_HEIGHT + 20) {
                Paragraph p = view.getActiveParagraph();
                if (p != null) {
                    p.removeTabStop(draggedTabStop);
                }
            }
            draggedTabStop = null;
            view.getDocument().setModified(true);
            view.relayout();
            redraw();
        }
    }
}
