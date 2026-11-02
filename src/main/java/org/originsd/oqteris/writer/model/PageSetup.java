package org.originsd.oqteris.writer.model;

/**
 * Page dimensions, orientation, and margins for a word processor document.
 * Units are points (1 pt = 1/72 inch).
 * <p>
 * Page width/height stored are the physical paper dimensions in portrait orientation.
 * {@link #getPageWidth()} / {@link #getPageHeight()} return the effective values
 * after orientation is applied.
 */
public class PageSetup {
    public enum Orientation {
        PORTRAIT,
        LANDSCAPE
    }

    /**
     * Named paper size presets (width × height in points, portrait).
     */
    public enum PaperSize {
        A4(595.28, 841.89, "A4 (210 × 297 mm)"),
        A3(841.89, 1190.55, "A3 (297 × 420 mm)"),
        A5(419.53, 595.28, "A5 (148 × 210 mm)"),
        A6(297.64, 419.53, "A6 (105 × 148 mm)"),
        LETTER(612.0, 792.0, "Letter (8.5 × 11 in)"),
        LEGAL(612.0, 1008.0, "Legal (8.5 × 14 in)"),
        TABLOID(792.0, 1224.0, "Tabloid (11 × 17 in)"),
        EXECUTIVE(522.0, 756.0, "Executive (7.25 × 10.5 in)"),
        STATEMENT(396.0, 612.0, "Statement (5.5 × 8.5 in)"),
        CUSTOM(595.28, 841.89, "Custom");

        public final double widthPt;
        public final double heightPt;
        public final String displayName;

        PaperSize(double w, double h, String name) {
            this.widthPt = w;
            this.heightPt = h;
            this.displayName = name;
        }

        public static PaperSize fromDimensions(double w, double h) {
            final double tol = 2.0;
            for (PaperSize ps : values()) {
                if (ps == CUSTOM) continue;
                if ((Math.abs(ps.widthPt - w) < tol && Math.abs(ps.heightPt - h) < tol)
                        || (Math.abs(ps.widthPt - h) < tol && Math.abs(ps.heightPt - w) < tol)) {
                    return ps;
                }
            }
            return CUSTOM;
        }
    }

    private double pageWidth = 595.28;
    private double pageHeight = 841.89;
    private double marginTop = 72.0;
    private double marginBottom = 72.0;
    private double marginLeft = 72.0;
    private double marginRight = 72.0;
    private Orientation orientation = Orientation.PORTRAIT;
    private PaperSize paperSize = PaperSize.A4;

    public PageSetup() {
    }

    public static PageSetup a4() {
        return of(PaperSize.A4);
    }

    public static PageSetup letter() {
        return of(PaperSize.LETTER);
    }

    public static PageSetup of(PaperSize size) {
        PageSetup p = new PageSetup();
        p.applyPaperSize(size);
        return p;
    }

    public void applyPaperSize(PaperSize size) {
        if (size == null || size == PaperSize.CUSTOM) {
            this.paperSize = PaperSize.CUSTOM;
            return;
        }
        this.paperSize = size;
        this.pageWidth = size.widthPt;
        this.pageHeight = size.heightPt;
    }

    public PaperSize getPaperSize() {
        PaperSize detected = PaperSize.fromDimensions(pageWidth, pageHeight);
        if (detected != PaperSize.CUSTOM) {
            paperSize = detected;
        }
        return paperSize;
    }

    public void setPaperSize(PaperSize paperSize) {
        applyPaperSize(paperSize != null ? paperSize : PaperSize.A4);
    }

    public double getPageWidth() {
        return orientation == Orientation.PORTRAIT ? pageWidth : pageHeight;
    }

    public double getPageHeight() {
        return orientation == Orientation.PORTRAIT ? pageHeight : pageWidth;
    }

    public double getPrintableWidth() {
        return Math.max(50.0, getPageWidth() - marginLeft - marginRight);
    }

    public double getPrintableHeight() {
        return Math.max(50.0, getPageHeight() - marginTop - marginBottom);
    }

    public double getMarginTop() {
        return marginTop;
    }

    public void setMarginTop(double marginTop) {
        this.marginTop = Math.max(0.0, marginTop);
    }

    public double getMarginBottom() {
        return marginBottom;
    }

    public void setMarginBottom(double marginBottom) {
        this.marginBottom = Math.max(0.0, marginBottom);
    }

    public double getMarginLeft() {
        return marginLeft;
    }

    public void setMarginLeft(double marginLeft) {
        this.marginLeft = Math.max(0.0, marginLeft);
    }

    public double getMarginRight() {
        return marginRight;
    }

    public void setMarginRight(double marginRight) {
        this.marginRight = Math.max(0.0, marginRight);
    }

    public void setMargins(double top, double right, double bottom, double left) {
        setMarginTop(top);
        setMarginRight(right);
        setMarginBottom(bottom);
        setMarginLeft(left);
    }

    public void setPageWidth(double pageWidth) {
        this.pageWidth = Math.max(50.0, pageWidth);
        this.paperSize = PaperSize.fromDimensions(this.pageWidth, this.pageHeight);
    }

    public void setPageHeight(double pageHeight) {
        this.pageHeight = Math.max(50.0, pageHeight);
        this.paperSize = PaperSize.fromDimensions(this.pageWidth, this.pageHeight);
    }

    public Orientation getOrientation() {
        return orientation;
    }

    public void setOrientation(Orientation orientation) {
        this.orientation = orientation != null ? orientation : Orientation.PORTRAIT;
    }

    public String getSummary() {
        return getPaperSize().displayName
                + " · " + (orientation == Orientation.LANDSCAPE ? "Landscape" : "Portrait")
                + String.format(" · Margins %.1f\"", marginLeft / 72.0);
    }
}