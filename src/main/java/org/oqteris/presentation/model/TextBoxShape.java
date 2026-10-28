package org.oqteris.presentation.model;

import javafx.scene.paint.Color;

/**
 * Text box shape on a slide with typography and formatting.
 */
public class TextBoxShape extends SlideShape {
    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    private String text = "Click to add text";
    private String fontFamily = "Calibri";
    private double fontSize = 20.0;
    private boolean bold = false;
    private boolean italic = false;
    private Color textColor = Color.rgb(30, 30, 30);
    private Alignment alignment = Alignment.LEFT;

    public TextBoxShape(double x, double y, double width, double height) {
        super(ShapeType.TEXT_BOX, x, y, width, height);
        setFillColor(Color.TRANSPARENT);
        setStrokeColor(Color.TRANSPARENT);
    }

    public TextBoxShape(double x, double y, double width, double height, String text, double fontSize, boolean bold) {
        this(x, y, width, height);
        this.text = text != null ? text : "";
        this.fontSize = fontSize;
        this.bold = bold;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily != null ? fontFamily : "Calibri";
    }

    public double getFontSize() {
        return fontSize;
    }

    public void setFontSize(double fontSize) {
        this.fontSize = Math.max(8.0, fontSize);
    }

    public boolean isBold() {
        return bold;
    }

    public void setBold(boolean bold) {
        this.bold = bold;
    }

    public boolean isItalic() {
        return italic;
    }

    public void setItalic(boolean italic) {
        this.italic = italic;
    }

    public Color getTextColor() {
        return textColor;
    }

    public void setTextColor(Color textColor) {
        this.textColor = textColor != null ? textColor : Color.BLACK;
    }

    public Alignment getAlignment() {
        return alignment;
    }

    public void setAlignment(Alignment alignment) {
        this.alignment = alignment != null ? alignment : Alignment.LEFT;
    }

    @Override
    public SlideShape copy() {
        TextBoxShape s = new TextBoxShape(getX(), getY(), getWidth(), getHeight(), text, fontSize, bold);
        s.fontFamily = this.fontFamily;
        s.italic = this.italic;
        s.textColor = this.textColor;
        s.alignment = this.alignment;
        s.setFillColor(getFillColor());
        s.setStrokeColor(getStrokeColor());
        s.setStrokeWidth(getStrokeWidth());
        s.setRotation(getRotation());
        return s;
    }
}
