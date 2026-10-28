package org.oqteris.writer.model;

/**
 * Page dimensions, orientation, and margins for a word processor document.
 * Units are points (1 pt = 1/72 inch). Standard A4 is 595.28 x 841.89 pt.
 */
public class PageSetup {
    public enum Orientation {
        PORTRAIT,
        LANDSCAPE
    }

    private double pageWidth = 595.28;  // A4 width
    private double pageHeight = 841.89; // A4 height
    private double marginTop = 72.0;    // 1 inch
    private double marginBottom = 72.0; // 1 inch
    private double marginLeft = 72.0;   // 1 inch
    private double marginRight = 72.0;  // 1 inch
    private Orientation orientation = Orientation.PORTRAIT;

    public PageSetup() {
    }

    public static PageSetup a4() {
        return new PageSetup();
    }

    public static PageSetup letter() {
        PageSetup p = new PageSetup();
        p.pageWidth = 612.0;
        p.pageHeight = 792.0;
        return p;
    }

    public double getPageWidth() {
        return orientation == Orientation.PORTRAIT ? pageWidth : pageHeight;
    }

    public double getPageHeight() {
        return orientation == Orientation.PORTRAIT ? pageHeight : pageWidth;
    }

    public double getPrintableWidth() {
        return getPageWidth() - marginLeft - marginRight;
    }

    public double getPrintableHeight() {
        return getPageHeight() - marginTop - marginBottom;
    }

    public double getMarginTop() {
        return marginTop;
    }

    public void setMarginTop(double marginTop) {
        this.marginTop = Math.max(10.0, marginTop);
    }

    public double getMarginBottom() {
        return marginBottom;
    }

    public void setMarginBottom(double marginBottom) {
        this.marginBottom = Math.max(10.0, marginBottom);
    }

    public double getMarginLeft() {
        return marginLeft;
    }

    public void setMarginLeft(double marginLeft) {
        this.marginLeft = Math.max(10.0, marginLeft);
    }

    public double getMarginRight() {
        return marginRight;
    }

    public void setMarginRight(double marginRight) {
        this.marginRight = Math.max(10.0, marginRight);
    }

    public Orientation getOrientation() {
        return orientation;
    }

    public void setOrientation(Orientation orientation) {
        this.orientation = orientation;
    }
}
