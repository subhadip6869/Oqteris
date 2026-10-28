package org.originsd.oqteris.calc.model;

import javafx.scene.paint.Color;

import java.util.Objects;

/**
 * Visual formatting attributes for a spreadsheet cell.
 */
public class CellStyle {
    public enum Alignment {
        GENERAL,
        LEFT,
        CENTER,
        RIGHT
    }

    public enum VerticalAlignment {
        TOP,
        CENTER,
        BOTTOM
    }

    public enum BorderType {
        NONE,
        THIN,
        MEDIUM,
        THICK,
        DOUBLE,
        DASHED
    }

    private String fontFamily = "Calibri";
    private double fontSize = 11.0;
    private boolean bold = false;
    private boolean italic = false;
    private boolean underline = false;
    private Color textColor = Color.BLACK;
    private Color fillColor = null; // Transparent / default

    private Alignment alignment = Alignment.GENERAL;
    private VerticalAlignment verticalAlignment = VerticalAlignment.CENTER;
    private boolean wrapText = false;
    private String numberFormat = "General";

    private BorderType borderTop = BorderType.NONE;
    private BorderType borderBottom = BorderType.NONE;
    private BorderType borderLeft = BorderType.NONE;
    private BorderType borderRight = BorderType.NONE;
    private Color borderColor = Color.rgb(212, 212, 212);

    public CellStyle() {
    }

    public CellStyle copy() {
        CellStyle s = new CellStyle();
        s.fontFamily = this.fontFamily;
        s.fontSize = this.fontSize;
        s.bold = this.bold;
        s.italic = this.italic;
        s.underline = this.underline;
        s.textColor = this.textColor;
        s.fillColor = this.fillColor;
        s.alignment = this.alignment;
        s.verticalAlignment = this.verticalAlignment;
        s.wrapText = this.wrapText;
        s.numberFormat = this.numberFormat;
        s.borderTop = this.borderTop;
        s.borderBottom = this.borderBottom;
        s.borderLeft = this.borderLeft;
        s.borderRight = this.borderRight;
        s.borderColor = this.borderColor;
        return s;
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
        this.fontSize = Math.max(6.0, fontSize);
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

    public boolean isUnderline() {
        return underline;
    }

    public void setUnderline(boolean underline) {
        this.underline = underline;
    }

    public Color getTextColor() {
        return textColor;
    }

    public void setTextColor(Color textColor) {
        this.textColor = textColor != null ? textColor : Color.BLACK;
    }

    public Color getFillColor() {
        return fillColor;
    }

    public void setFillColor(Color fillColor) {
        this.fillColor = fillColor;
    }

    public Alignment getAlignment() {
        return alignment;
    }

    public void setAlignment(Alignment alignment) {
        this.alignment = alignment != null ? alignment : Alignment.GENERAL;
    }

    public VerticalAlignment getVerticalAlignment() {
        return verticalAlignment;
    }

    public void setVerticalAlignment(VerticalAlignment verticalAlignment) {
        this.verticalAlignment = verticalAlignment != null ? verticalAlignment : VerticalAlignment.CENTER;
    }

    public boolean isWrapText() {
        return wrapText;
    }

    public void setWrapText(boolean wrapText) {
        this.wrapText = wrapText;
    }

    public String getNumberFormat() {
        return numberFormat;
    }

    public void setNumberFormat(String numberFormat) {
        this.numberFormat = numberFormat != null ? numberFormat : "General";
    }

    public BorderType getBorderTop() {
        return borderTop;
    }

    public void setBorderTop(BorderType borderTop) {
        this.borderTop = borderTop;
    }

    public BorderType getBorderBottom() {
        return borderBottom;
    }

    public void setBorderBottom(BorderType borderBottom) {
        this.borderBottom = borderBottom;
    }

    public BorderType getBorderLeft() {
        return borderLeft;
    }

    public void setBorderLeft(BorderType borderLeft) {
        this.borderLeft = borderLeft;
    }

    public BorderType getBorderRight() {
        return borderRight;
    }

    public void setBorderRight(BorderType borderRight) {
        this.borderRight = borderRight;
    }

    public Color getBorderColor() {
        return borderColor;
    }

    public void setBorderColor(Color borderColor) {
        this.borderColor = borderColor != null ? borderColor : Color.rgb(212, 212, 212);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CellStyle that)) return false;
        return Double.compare(that.fontSize, fontSize) == 0 && bold == that.bold &&
                italic == that.italic && underline == that.underline && wrapText == that.wrapText &&
                Objects.equals(fontFamily, that.fontFamily) && Objects.equals(textColor, that.textColor) &&
                Objects.equals(fillColor, that.fillColor) && alignment == that.alignment &&
                verticalAlignment == that.verticalAlignment && Objects.equals(numberFormat, that.numberFormat) &&
                borderTop == that.borderTop && borderBottom == that.borderBottom &&
                borderLeft == that.borderLeft && borderRight == that.borderRight;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fontFamily, fontSize, bold, italic, underline, textColor, fillColor, alignment, verticalAlignment, wrapText, numberFormat, borderTop, borderBottom, borderLeft, borderRight);
    }
}
