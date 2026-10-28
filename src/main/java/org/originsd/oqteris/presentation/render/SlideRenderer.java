package org.originsd.oqteris.presentation.render;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.originsd.oqteris.core.render.FontManager;
import org.originsd.oqteris.presentation.model.*;

/**
 * High-precision slide rendering engine for canvas editing and presentation display.
 */
public class SlideRenderer {
    private static final FontManager FONTS = FontManager.getInstance();

    public static void renderSlide(GraphicsContext gc, Slide slide, double slideW, double slideH, double scale, SlideShape selectedShape) {
        if (gc == null || slide == null || slideW <= 0 || slideH <= 0) return;

        gc.save();
        gc.scale(scale, scale);

        // 1. Background
        gc.setFill(slide.getBackgroundColor() != null ? slide.getBackgroundColor() : Color.WHITE);
        gc.fillRect(0, 0, slideW, slideH);

        // 2. Render Shapes in z-order
        for (SlideShape shape : slide.getShapes()) {
            renderShape(gc, shape);
        }

        // 3. Selection Bounding Box & Handles
        if (selectedShape != null && slide.getShapes().contains(selectedShape)) {
            renderSelectionHandles(gc, selectedShape);
        }

        gc.restore();
    }

    private static void renderShape(GraphicsContext gc, SlideShape shape) {
        gc.save();

        if (shape.getRotation() != 0) {
            double cx = shape.getX() + shape.getWidth() / 2.0;
            double cy = shape.getY() + shape.getHeight() / 2.0;
            gc.translate(cx, cy);
            gc.rotate(shape.getRotation());
            gc.translate(-cx, -cy);
        }

        gc.setGlobalAlpha(shape.getOpacity());

        switch (shape.getShapeType()) {
            case TEXT_BOX -> renderTextBox(gc, (TextBoxShape) shape);
            case GEOMETRIC -> renderGeometric(gc, (GeometricShape) shape);
            case IMAGE -> renderImage(gc, (ImageShape) shape);
            case TABLE -> renderTable(gc, (TableShape) shape);
        }

        gc.restore();
    }

    private static void renderTextBox(GraphicsContext gc, TextBoxShape tb) {
        double x = tb.getX();
        double y = tb.getY();
        double w = tb.getWidth();
        double h = tb.getHeight();

        // Optional background / border
        if (tb.getFillColor() != null && tb.getFillColor() != Color.TRANSPARENT) {
            gc.setFill(tb.getFillColor());
            gc.fillRect(x, y, w, h);
        }
        if (tb.getStrokeColor() != null && tb.getStrokeColor() != Color.TRANSPARENT && tb.getStrokeWidth() > 0) {
            gc.setStroke(tb.getStrokeColor());
            gc.setLineWidth(tb.getStrokeWidth());
            gc.strokeRect(x, y, w, h);
        }

        // Typography
        Font font = FONTS.getFont(tb.getFontFamily(), tb.getFontSize(), tb.isBold(), tb.isItalic());
        gc.setFont(font);
        gc.setFill(tb.getTextColor());
        gc.setTextBaseline(VPos.TOP);

        TextAlignment ta = switch (tb.getAlignment()) {
            case CENTER -> TextAlignment.CENTER;
            case RIGHT -> TextAlignment.RIGHT;
            default -> TextAlignment.LEFT;
        };
        gc.setTextAlign(ta);

        double lineH = tb.getFontSize() * 1.3;
        String[] lines = tb.getText().split("\n");
        double textY = y + 4;

        for (String line : lines) {
            double textX = x + 4;
            if (tb.getAlignment() == TextBoxShape.Alignment.CENTER) {
                textX = x + (w / 2.0);
            } else if (tb.getAlignment() == TextBoxShape.Alignment.RIGHT) {
                textX = x + w - 4;
            }

            gc.fillText(line, textX, textY);
            textY += lineH;
            if (textY > y + h) break;
        }
    }

    private static void renderGeometric(GraphicsContext gc, GeometricShape shape) {
        double x = shape.getX();
        double y = shape.getY();
        double w = shape.getWidth();
        double h = shape.getHeight();

        boolean hasFill = shape.getFillColor() != null && shape.getFillColor() != Color.TRANSPARENT;
        boolean hasStroke = shape.getStrokeColor() != null && shape.getStrokeWidth() > 0;

        if (hasFill) gc.setFill(shape.getFillColor());
        if (hasStroke) {
            gc.setStroke(shape.getStrokeColor());
            gc.setLineWidth(shape.getStrokeWidth());
        }

        switch (shape.getGeometry()) {
            case RECTANGLE -> {
                if (hasFill) gc.fillRect(x, y, w, h);
                if (hasStroke) gc.strokeRect(x, y, w, h);
            }
            case ROUNDED_RECTANGLE -> {
                double r = shape.getCornerRadius();
                if (hasFill) gc.fillRoundRect(x, y, w, h, r, r);
                if (hasStroke) gc.strokeRoundRect(x, y, w, h, r, r);
            }
            case ELLIPSE -> {
                if (hasFill) gc.fillOval(x, y, w, h);
                if (hasStroke) gc.strokeOval(x, y, w, h);
            }
            case TRIANGLE -> {
                double[] xPoints = {x + w / 2.0, x + w, x};
                double[] yPoints = {y, y + h, y + h};
                if (hasFill) gc.fillPolygon(xPoints, yPoints, 3);
                if (hasStroke) gc.strokePolygon(xPoints, yPoints, 3);
            }
            case ARROW -> {
                double stemH = h * 0.4;
                double stemY = y + (h - stemH) / 2.0;
                double headW = w * 0.35;
                double stemW = w - headW;

                double[] xPoints = {x, x + stemW, x + stemW, x + w, x + stemW, x + stemW, x};
                double[] yPoints = {stemY, stemY, y, y + h / 2.0, y + h, stemY + stemH, stemY + stemH};
                if (hasFill) gc.fillPolygon(xPoints, yPoints, 7);
                if (hasStroke) gc.strokePolygon(xPoints, yPoints, 7);
            }
            case STAR -> {
                drawStar(gc, x + w / 2.0, y + h / 2.0, w / 2.0, w / 4.0, hasFill, hasStroke);
            }
            case LINE -> {
                if (hasStroke) {
                    gc.strokeLine(x, y + h / 2.0, x + w, y + h / 2.0);
                }
            }
        }
    }

    private static void drawStar(GraphicsContext gc, double cx, double cy, double outerR, double innerR, boolean fill, boolean stroke) {
        int points = 5;
        double[] xPoints = new double[points * 2];
        double[] yPoints = new double[points * 2];
        double angleStep = Math.PI / points;

        for (int i = 0; i < points * 2; i++) {
            double r = (i % 2 == 0) ? outerR : innerR;
            double a = i * angleStep - Math.PI / 2.0;
            xPoints[i] = cx + r * Math.cos(a);
            yPoints[i] = cy + r * Math.sin(a);
        }

        if (fill) gc.fillPolygon(xPoints, yPoints, points * 2);
        if (stroke) gc.strokePolygon(xPoints, yPoints, points * 2);
    }

    private static void renderImage(GraphicsContext gc, ImageShape is) {
        if (is.getImage() != null) {
            gc.drawImage(is.getImage(), is.getX(), is.getY(), is.getWidth(), is.getHeight());
        }
    }

    private static void renderTable(GraphicsContext gc, TableShape ts) {
        double x = ts.getX();
        double y = ts.getY();
        double w = ts.getWidth();
        double h = ts.getHeight();

        int rows = ts.getRows();
        int cols = ts.getCols();
        double cellW = w / cols;
        double cellH = h / rows;

        gc.setFont(Font.font("Segoe UI", 11.0));
        gc.setTextBaseline(VPos.CENTER);
        gc.setTextAlign(TextAlignment.CENTER);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double cx = x + c * cellW;
                double cy = y + r * cellH;

                gc.setFill(r == 0 ? Color.rgb(230, 240, 250) : Color.WHITE);
                gc.fillRect(cx, cy, cellW, cellH);

                gc.setStroke(ts.getStrokeColor());
                gc.setLineWidth(1.0);
                gc.strokeRect(cx, cy, cellW, cellH);

                gc.setFill(Color.BLACK);
                gc.fillText(ts.getCell(r, c), cx + cellW / 2.0, cy + cellH / 2.0);
            }
        }
    }

    private static void renderSelectionHandles(GraphicsContext gc, SlideShape shape) {
        double x = shape.getX();
        double y = shape.getY();
        double w = shape.getWidth();
        double h = shape.getHeight();

        gc.setStroke(Color.rgb(0, 120, 215));
        gc.setLineWidth(1.5);
        gc.setLineDashes(4.0);
        gc.strokeRect(x, y, w, h);
        gc.setLineDashes(null);

        // 8 handles (rectangles)
        double handleSize = 7.0;
        double half = handleSize / 2.0;
        gc.setFill(Color.WHITE);
        gc.setStroke(Color.rgb(0, 120, 215));
        gc.setLineWidth(1.5);

        double[][] handles = {
                {x - half, y - half},                 // NW
                {x + w / 2.0 - half, y - half},       // N
                {x + w - half, y - half},             // NE
                {x + w - half, y + h / 2.0 - half},   // E
                {x + w - half, y + h - half},         // SE
                {x + w / 2.0 - half, y + h - half},   // S
                {x - half, y + h - half},             // SW
                {x - half, y + h / 2.0 - half}        // W
        };

        for (double[] pos : handles) {
            gc.fillRect(pos[0], pos[1], handleSize, handleSize);
            gc.strokeRect(pos[0], pos[1], handleSize, handleSize);
        }
    }
}
