package org.oqteris.presentation.model;

import javafx.scene.paint.Color;

/**
 * Base abstract class for all visual objects placed on a presentation slide.
 */
public abstract class SlideShape {
    public enum ShapeType {
        TEXT_BOX,
        GEOMETRIC,
        IMAGE,
        TABLE
    }

    private final ShapeType shapeType;
    private double x;
    private double y;
    private double width;
    private double height;
    private double rotation = 0.0; // degrees

    private Color fillColor = Color.rgb(0, 120, 215);
    private Color strokeColor = Color.rgb(0, 90, 160);
    private double strokeWidth = 1.0;
    private double opacity = 1.0;

    public SlideShape(ShapeType shapeType, double x, double y, double width, double height) {
        this.shapeType = shapeType;
        this.x = x;
        this.y = y;
        this.width = Math.max(10.0, width);
        this.height = Math.max(10.0, height);
    }

    public ShapeType getShapeType() {
        return shapeType;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = Math.max(10.0, width);
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = Math.max(10.0, height);
    }

    public double getRotation() {
        return rotation;
    }

    public void setRotation(double rotation) {
        this.rotation = rotation;
    }

    public Color getFillColor() {
        return fillColor;
    }

    public void setFillColor(Color fillColor) {
        this.fillColor = fillColor;
    }

    public Color getStrokeColor() {
        return strokeColor;
    }

    public void setStrokeColor(Color strokeColor) {
        this.strokeColor = strokeColor;
    }

    public double getStrokeWidth() {
        return strokeWidth;
    }

    public void setStrokeWidth(double strokeWidth) {
        this.strokeWidth = Math.max(0.0, strokeWidth);
    }

    public double getOpacity() {
        return opacity;
    }

    public void setOpacity(double opacity) {
        this.opacity = Math.max(0.0, Math.min(1.0, opacity));
    }

    public boolean contains(double px, double py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    public abstract SlideShape copy();
}
