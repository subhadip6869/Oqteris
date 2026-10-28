package org.originsd.oqteris.pdf.model;

import javafx.scene.paint.Color;

/**
 * User-created annotation on a PDF document page.
 */
public class PdfAnnotation {
    public enum Type {
        HIGHLIGHT,
        UNDERLINE,
        NOTE,
        FREEHAND
    }

    private final Type type;
    private final int pageIndex;
    private double x;
    private double y;
    private double width;
    private double height;
    private Color color;
    private String text;

    public PdfAnnotation(Type type, int pageIndex, double x, double y, double width, double height, Color color, String text) {
        this.type = type;
        this.pageIndex = pageIndex;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.color = color != null ? color : Color.YELLOW;
        this.text = text != null ? text : "";
    }

    public Type getType() {
        return type;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
