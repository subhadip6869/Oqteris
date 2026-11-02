package org.originsd.oqteris.writer.model;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Cell within a document table containing paragraphs.
 */
public class DocumentTableCell {
    public enum VAlign { TOP, CENTER, BOTTOM }

    private final List<Paragraph> paragraphs = new ArrayList<>();
    private Color backgroundColor = null;
    private double paddingTop = 3.0;
    private double paddingBottom = 3.0;
    private double paddingLeft = 5.4;
    private double paddingRight = 5.4;
    private int colSpan = 1;
    private VAlign verticalAlignment = VAlign.TOP;
    private boolean borderTop = true;
    private boolean borderBottom = true;
    private boolean borderLeft = true;
    private boolean borderRight = true;

    public DocumentTableCell() {
        paragraphs.add(new Paragraph());
    }

    public DocumentTableCell(String text) {
        paragraphs.add(new Paragraph(text));
    }

    public List<Paragraph> getParagraphs() {
        return paragraphs;
    }

    public void addParagraph(Paragraph p) {
        if (p != null) {
            paragraphs.add(p);
        }
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public double getPaddingTop() {
        return paddingTop;
    }

    public void setPaddingTop(double paddingTop) {
        this.paddingTop = paddingTop;
    }

    public double getPaddingBottom() {
        return paddingBottom;
    }

    public void setPaddingBottom(double paddingBottom) {
        this.paddingBottom = paddingBottom;
    }

    public double getPaddingLeft() {
        return paddingLeft;
    }

    public void setPaddingLeft(double paddingLeft) {
        this.paddingLeft = paddingLeft;
    }

    public double getPaddingRight() {
        return paddingRight;
    }

    public void setPaddingRight(double paddingRight) {
        this.paddingRight = paddingRight;
    }

    public int getColSpan() {
        return colSpan;
    }

    public void setColSpan(int colSpan) {
        this.colSpan = Math.max(1, colSpan);
    }

    public VAlign getVerticalAlignment() {
        return verticalAlignment;
    }

    public void setVerticalAlignment(VAlign verticalAlignment) {
        this.verticalAlignment = verticalAlignment != null ? verticalAlignment : VAlign.TOP;
    }

    public boolean isBorderTop() {
        return borderTop;
    }

    public void setBorderTop(boolean borderTop) {
        this.borderTop = borderTop;
    }

    public boolean isBorderBottom() {
        return borderBottom;
    }

    public void setBorderBottom(boolean borderBottom) {
        this.borderBottom = borderBottom;
    }

    public boolean isBorderLeft() {
        return borderLeft;
    }

    public void setBorderLeft(boolean borderLeft) {
        this.borderLeft = borderLeft;
    }

    public boolean isBorderRight() {
        return borderRight;
    }

    public void setBorderRight(boolean borderRight) {
        this.borderRight = borderRight;
    }
}
