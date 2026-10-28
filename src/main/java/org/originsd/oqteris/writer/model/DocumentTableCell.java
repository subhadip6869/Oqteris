package org.originsd.oqteris.writer.model;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Cell within a document table containing paragraphs.
 */
public class DocumentTableCell {
    private final List<Paragraph> paragraphs = new ArrayList<>();
    private Color backgroundColor = null;
    private double paddingTop = 4.0;
    private double paddingBottom = 4.0;
    private double paddingLeft = 6.0;
    private double paddingRight = 6.0;

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
}
