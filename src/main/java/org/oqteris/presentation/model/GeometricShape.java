package org.oqteris.presentation.model;

import javafx.scene.paint.Color;

/**
 * Geometric shape placed on a slide (Rectangle, Ellipse, Arrow, Star, Triangle, Line).
 */
public class GeometricShape extends SlideShape {
    public enum Geometry {
        RECTANGLE,
        ROUNDED_RECTANGLE,
        ELLIPSE,
        TRIANGLE,
        ARROW,
        STAR,
        LINE
    }

    private Geometry geometry = Geometry.RECTANGLE;
    private double cornerRadius = 12.0;

    public GeometricShape(Geometry geometry, double x, double y, double width, double height) {
        super(ShapeType.GEOMETRIC, x, y, width, height);
        this.geometry = geometry != null ? geometry : Geometry.RECTANGLE;
        setFillColor(Color.rgb(0, 120, 215));
        setStrokeColor(Color.rgb(0, 90, 160));
        setStrokeWidth(1.5);
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry != null ? geometry : Geometry.RECTANGLE;
    }

    public double getCornerRadius() {
        return cornerRadius;
    }

    public void setCornerRadius(double cornerRadius) {
        this.cornerRadius = Math.max(0, cornerRadius);
    }

    @Override
    public SlideShape copy() {
        GeometricShape s = new GeometricShape(geometry, getX(), getY(), getWidth(), getHeight());
        s.cornerRadius = this.cornerRadius;
        s.setFillColor(getFillColor());
        s.setStrokeColor(getStrokeColor());
        s.setStrokeWidth(getStrokeWidth());
        s.setRotation(getRotation());
        return s;
    }
}
