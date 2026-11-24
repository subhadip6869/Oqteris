package app.vercel.originsd.oqteris.pdf.print;

import javafx.beans.property.*;
import javafx.print.PageOrientation;
import javafx.print.PaperSource;
import javafx.print.Printer;

/**
 * Encapsulates all user-facing print settings for the Oqteris PDF print dialog.
 * Models the same set of options available in professional PDF readers like Foxit and Adobe Acrobat.
 */
public class PrintOptions {

    // -------------------------------------------------------------------------
    // Page Range
    // -------------------------------------------------------------------------
    public enum PageRangeMode { ALL, CURRENT, CUSTOM }

    private final ObjectProperty<PageRangeMode> pageRangeMode =
            new SimpleObjectProperty<>(PageRangeMode.ALL);
    /** Custom range string, e.g. "1,3,5-8". Only used when pageRangeMode == CUSTOM. */
    private final StringProperty customPageRange = new SimpleStringProperty("1");

    // -------------------------------------------------------------------------
    // Printer / Copies
    // -------------------------------------------------------------------------
    private final ObjectProperty<Printer> printer =
            new SimpleObjectProperty<>(Printer.getDefaultPrinter());
    private final IntegerProperty copies    = new SimpleIntegerProperty(1);
    private final BooleanProperty collate   = new SimpleBooleanProperty(true);
    private final BooleanProperty reverseOrder = new SimpleBooleanProperty(false);

    // -------------------------------------------------------------------------
    // Page Handling Mode (Scale, Multiple pages per sheet, Booklet)
    // -------------------------------------------------------------------------
    public enum PageHandlingMode { SCALE, MULTIPLE, BOOKLET }

    private final ObjectProperty<PageHandlingMode> pageHandlingMode =
            new SimpleObjectProperty<>(PageHandlingMode.SCALE);

    // -------------------------------------------------------------------------
    // Page Layout / Orientation
    // -------------------------------------------------------------------------
    public enum OrientationMode { PORTRAIT, LANDSCAPE, AUTO }

    private final ObjectProperty<OrientationMode> orientation =
            new SimpleObjectProperty<>(OrientationMode.AUTO);

    // -------------------------------------------------------------------------
    // Margins
    // -------------------------------------------------------------------------
    public enum MarginMode {
        NONE(0.0, "None (0 mm)"),
        SMALL(14.173, "Small (5 mm)"),
        NORMAL(28.346, "Normal (10 mm)"),
        LARGE(56.693, "Large (20 mm)"),
        CUSTOM(0.0, "Custom");

        private final double points;
        private final String label;
        MarginMode(double points, String label) {
            this.points = points;
            this.label = label;
        }
        public double getPoints() { return points; }
        public String getLabel() { return label; }
        @Override public String toString() { return label; }
    }

    private final ObjectProperty<MarginMode> marginMode =
            new SimpleObjectProperty<>(MarginMode.NONE);
    private final DoubleProperty customMarginMm = new SimpleDoubleProperty(10.0);

    // -------------------------------------------------------------------------
    // Pages per Sheet (N-Up printing)
    // -------------------------------------------------------------------------
    public enum PagesPerSheet { ONE, TWO, FOUR, SIX, NINE, SIXTEEN }

    private final ObjectProperty<PagesPerSheet> pagesPerSheet =
            new SimpleObjectProperty<>(PagesPerSheet.ONE);
    /** When N-up > 1: order pages left-to-right then top-to-bottom, or top-to-bottom then left-to-right. */
    private final BooleanProperty nupHorizontalFirst = new SimpleBooleanProperty(true);
    /** Draw a thin border around each page thumbnail in N-up layout. */
    private final BooleanProperty nupPageBorders     = new SimpleBooleanProperty(true);

    // -------------------------------------------------------------------------
    // Duplex / Two-Sided Printing
    // -------------------------------------------------------------------------
    public enum DuplexMode { NONE, LONG_EDGE, SHORT_EDGE }

    private final ObjectProperty<DuplexMode> duplexMode =
            new SimpleObjectProperty<>(DuplexMode.NONE);

    // -------------------------------------------------------------------------
    // Booklet Printing
    // -------------------------------------------------------------------------
    private final BooleanProperty bookletPrint = new SimpleBooleanProperty(false);
    public enum BookletSubset { BOTH_SIDES, FRONT_SIDE_ONLY, BACK_SIDE_ONLY }
    private final ObjectProperty<BookletSubset> bookletSubset =
            new SimpleObjectProperty<>(BookletSubset.BOTH_SIDES);
    /** Flip booklet from left-to-right (default) or top-to-bottom (calendar-style). */
    private final BooleanProperty bookletFlipOnShortEdge = new SimpleBooleanProperty(false);

    // -------------------------------------------------------------------------
    // Fit & Scaling
    // -------------------------------------------------------------------------
    public enum FitMode { ACTUAL_SIZE, FIT_PAGE, SHRINK_OVERSIZED, CUSTOM_SCALE }

    private final ObjectProperty<FitMode> fitMode =
            new SimpleObjectProperty<>(FitMode.FIT_PAGE);
    /** Scale percentage when fitMode == CUSTOM_SCALE (e.g. 85.0 = 85 %). */
    private final DoubleProperty  customScalePct  = new SimpleDoubleProperty(100.0);
    /** Auto-rotate pages to best fill the paper area. */
    private final BooleanProperty autoRotate      = new SimpleBooleanProperty(true);
    /** Center the page on the paper. */
    private final BooleanProperty centerOnPage    = new SimpleBooleanProperty(true);

    // -------------------------------------------------------------------------
    // Print Content
    // -------------------------------------------------------------------------
    private final BooleanProperty printAnnotations = new SimpleBooleanProperty(true);
    private final BooleanProperty printFormFields  = new SimpleBooleanProperty(true);
    private final BooleanProperty printComments    = new SimpleBooleanProperty(false);
    /** Output in black & white (grayscale) instead of colour. */
    private final BooleanProperty grayscale        = new SimpleBooleanProperty(false);

    // =========================================================================
    // Property accessors
    // =========================================================================

    public ObjectProperty<PageRangeMode> pageRangeModeProperty()     { return pageRangeMode; }
    public PageRangeMode getPageRangeMode()                            { return pageRangeMode.get(); }
    public void setPageRangeMode(PageRangeMode v)                      { pageRangeMode.set(v); }

    public StringProperty customPageRangeProperty()                    { return customPageRange; }
    public String getCustomPageRange()                                 { return customPageRange.get(); }
    public void setCustomPageRange(String v)                           { customPageRange.set(v); }

    public ObjectProperty<Printer> printerProperty()                   { return printer; }
    public Printer getPrinter()                                        { return printer.get(); }
    public void setPrinter(Printer v)                                  { printer.set(v); }

    public IntegerProperty copiesProperty()                            { return copies; }
    public int getCopies()                                             { return copies.get(); }
    public void setCopies(int v)                                       { copies.set(v); }

    public BooleanProperty collateProperty()                           { return collate; }
    public boolean isCollate()                                         { return collate.get(); }
    public void setCollate(boolean v)                                  { collate.set(v); }

    public BooleanProperty reverseOrderProperty()                      { return reverseOrder; }
    public boolean isReverseOrder()                                    { return reverseOrder.get(); }
    public void setReverseOrder(boolean v)                             { reverseOrder.set(v); }

    public ObjectProperty<OrientationMode> orientationProperty()       { return orientation; }
    public OrientationMode getOrientation()                            { return orientation.get(); }
    public void setOrientation(OrientationMode v)                      { orientation.set(v); }

    public ObjectProperty<PagesPerSheet> pagesPerSheetProperty()       { return pagesPerSheet; }
    public ObjectProperty<PageHandlingMode> pageHandlingModeProperty() { return pageHandlingMode; }
    public PageHandlingMode getPageHandlingMode()                      { return pageHandlingMode.get(); }
    public void setPageHandlingMode(PageHandlingMode v)                { pageHandlingMode.set(v); }

    public ObjectProperty<MarginMode> marginModeProperty()             { return marginMode; }
    public MarginMode getMarginMode()                                  { return marginMode.get(); }
    public void setMarginMode(MarginMode v)                            { marginMode.set(v); }

    public DoubleProperty customMarginMmProperty()                     { return customMarginMm; }
    public double getCustomMarginMm()                                  { return customMarginMm.get(); }
    public void setCustomMarginMm(double v)                            { customMarginMm.set(v); }

    public double getEffectiveMarginPoints() {
        if (marginMode.get() == MarginMode.CUSTOM) {
            return customMarginMm.get() * 72.0 / 25.4;
        }
        return marginMode.get().getPoints();
    }

    public PagesPerSheet getPagesPerSheet()                            { return pagesPerSheet.get(); }
    public void setPagesPerSheet(PagesPerSheet v)                      { pagesPerSheet.set(v); }

    public BooleanProperty nupHorizontalFirstProperty()                { return nupHorizontalFirst; }
    public boolean isNupHorizontalFirst()                              { return nupHorizontalFirst.get(); }
    public void setNupHorizontalFirst(boolean v)                       { nupHorizontalFirst.set(v); }

    public BooleanProperty nupPageBordersProperty()                    { return nupPageBorders; }
    public boolean isNupPageBorders()                                  { return nupPageBorders.get(); }
    public void setNupPageBorders(boolean v)                           { nupPageBorders.set(v); }

    public ObjectProperty<DuplexMode> duplexModeProperty()             { return duplexMode; }
    public DuplexMode getDuplexMode()                                  { return duplexMode.get(); }
    public void setDuplexMode(DuplexMode v)                            { duplexMode.set(v); }

    public BooleanProperty bookletPrintProperty()                      { return bookletPrint; }
    public boolean isBookletPrint()                                    {
        return getPageHandlingMode() == PageHandlingMode.BOOKLET || bookletPrint.get();
    }
    public void setBookletPrint(boolean v)                             {
        bookletPrint.set(v);
        if (v) setPageHandlingMode(PageHandlingMode.BOOKLET);
    }

    public ObjectProperty<BookletSubset> bookletSubsetProperty()       { return bookletSubset; }
    public BookletSubset getBookletSubset()                            { return bookletSubset.get(); }
    public void setBookletSubset(BookletSubset v)                      { bookletSubset.set(v); }

    public BooleanProperty bookletFlipOnShortEdgeProperty()            { return bookletFlipOnShortEdge; }
    public boolean isBookletFlipOnShortEdge()                          { return bookletFlipOnShortEdge.get(); }
    public void setBookletFlipOnShortEdge(boolean v)                   { bookletFlipOnShortEdge.set(v); }

    public ObjectProperty<FitMode> fitModeProperty()                   { return fitMode; }
    public FitMode getFitMode()                                        { return fitMode.get(); }
    public void setFitMode(FitMode v)                                  { fitMode.set(v); }

    public DoubleProperty customScalePctProperty()                     { return customScalePct; }
    public double getCustomScalePct()                                  { return customScalePct.get(); }
    public void setCustomScalePct(double v)                            { customScalePct.set(v); }

    public BooleanProperty autoRotateProperty()                        { return autoRotate; }
    public boolean isAutoRotate()                                      { return autoRotate.get(); }
    public void setAutoRotate(boolean v)                               { autoRotate.set(v); }

    public BooleanProperty centerOnPageProperty()                      { return centerOnPage; }
    public boolean isCenterOnPage()                                    { return centerOnPage.get(); }
    public void setCenterOnPage(boolean v)                             { centerOnPage.set(v); }

    public BooleanProperty printAnnotationsProperty()                  { return printAnnotations; }
    public boolean isPrintAnnotations()                                { return printAnnotations.get(); }
    public void setPrintAnnotations(boolean v)                         { printAnnotations.set(v); }

    public BooleanProperty printFormFieldsProperty()                   { return printFormFields; }
    public boolean isPrintFormFields()                                 { return printFormFields.get(); }
    public void setPrintFormFields(boolean v)                          { printFormFields.set(v); }

    public BooleanProperty printCommentsProperty()                     { return printComments; }
    public boolean isPrintComments()                                   { return printComments.get(); }
    public void setPrintComments(boolean v)                            { printComments.set(v); }

    public BooleanProperty grayscaleProperty()                         { return grayscale; }
    public boolean isGrayscale()                                       { return grayscale.get(); }
    public void setGrayscale(boolean v)                                { grayscale.set(v); }

    // =========================================================================
    // Helper: resolve the actual JavaFX PageOrientation
    // =========================================================================
    public PageOrientation resolveOrientation(double pageWidthPts, double pageHeightPts) {
        return switch (getOrientation()) {
            case PORTRAIT   -> PageOrientation.PORTRAIT;
            case LANDSCAPE  -> PageOrientation.LANDSCAPE;
            case AUTO       -> pageWidthPts > pageHeightPts
                               ? PageOrientation.LANDSCAPE
                               : PageOrientation.PORTRAIT;
        };
    }

    /** Returns the N-up grid dimension (e.g. 2 for TWO, 4 for FOUR, …) */
    public int nupCount() {
        if (getPageHandlingMode() != PageHandlingMode.MULTIPLE) {
            return 1;
        }
        return switch (getPagesPerSheet()) {
            case ONE     -> 1;
            case TWO     -> 2;
            case FOUR    -> 4;
            case SIX     -> 6;
            case NINE    -> 9;
            case SIXTEEN -> 16;
        };
    }

    /** Returns the number of physical sheets required for a booklet containing {@code pageCount} PDF pages. */
    public int bookletSheetCount(int pageCount) {
        return (int) Math.ceil(pageCount / 4.0);
    }
}
