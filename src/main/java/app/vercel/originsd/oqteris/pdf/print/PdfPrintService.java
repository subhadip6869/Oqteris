package app.vercel.originsd.oqteris.pdf.print;

import app.vercel.originsd.oqteris.pdf.render.PdfPageRenderer;
import javafx.embed.swing.SwingFXUtils;
import javafx.print.*;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.printing.PDFPageable;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.util.Matrix;

import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.*;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;

/**
 * Core PDF print service for Oqteris.
 * Translates {@link PrintOptions} into actual JavaFX PrinterJob operations:
 * <ul>
 *   <li>Page range resolution (All / Current / Custom "1,3,5-8")</li>
 *   <li>N-up / multiple pages per sheet compositing</li>
 *   <li>Booklet imposition (saddle-stitch fold order)</li>
 *   <li>Fit / shrink / actual-size / custom-scale placement</li>
 *   <li>Auto-rotate, center-on-page, duplex hints</li>
 *   <li>Grayscale conversion</li>
 *   <li>Collation and reverse-order support</li>
 * </ul>
 */
public class PdfPrintService {

    private final PDDocument pdDocument;
    private final PdfPageRenderer renderer;
    private final int userRotation;

    public PdfPrintService(PDDocument pdDocument, PdfPageRenderer renderer, int userRotation) {
        this.pdDocument  = pdDocument;
        this.renderer    = renderer;
        this.userRotation = userRotation;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Executes the print job with the given options on the specified printer job.
     * The PrinterJob must already be configured (printer selected, page layout set, etc.)
     * and {@code job.showPrintDialog()} must have been accepted by the caller.
     *
     * @return {@code true} if the job completed successfully.
     */
    public boolean print(PrinterJob job, PrintOptions opts) {
        try {
            return print(opts);
        } catch (Exception e) {
            System.err.println("[PdfPrintService] Vector print error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Executes pristine vector printing using Apache PDFBox's vector print engine and Java Print Service.
     * Preserves original PDF text, embedded fonts, vectors, and resolution with 0% bitmap blurriness.
     * Correctly sets DEVMODE landscape/portrait orientation on the target printer.
     */
    public boolean print(PrintOptions opts) throws Exception {
        PDDocument printDoc = buildPrintDocument(opts);
        try {
            java.awt.print.PrinterJob printJob = java.awt.print.PrinterJob.getPrinterJob();
            PrintService service = resolvePrintService(opts);
            if (service != null) {
                printJob.setPrintService(service);
            }

            PrintRequestAttributeSet attr = buildPrintAttributes(opts, printDoc);
            org.apache.pdfbox.printing.Orientation orientation = resolvePdfBoxOrientation(opts, printDoc);

            // Pass RASTERIZE_OFF (0.0f) so PDFBox paints vectors and text directly to Graphics2D!
            PDFPageable pageable = new PDFPageable(printDoc, orientation, false, PDFPrintable.RASTERIZE_OFF, true);
            printJob.setPageable(pageable);

            printJob.print(attr);
            return true;
        } finally {
            printDoc.close();
        }
    }

    /**
     * Constructs a native vector {@link PDDocument} reflecting the chosen page ranges,
     * orientation, page scaling, N-up grid imposition, or saddle-stitch booklet imposition.
     * All original PDF vectors, text objects, and fonts are preserved via {@link LayerUtility} form XObjects.
     */
    public PDDocument buildPrintDocument(PrintOptions opts) throws IOException {
        PDDocument printDoc = new PDDocument();
        List<Integer> pageIndices = resolvePageIndices(opts);
        if (pageIndices.isEmpty()) return printDoc;

        List<Integer> ordered = applyOrder(pageIndices, opts.isReverseOrder());

        // Determine base paper dimensions in points (default: A4 595.28 x 841.89)
        float paperW = 595.28f;
        float paperH = 841.89f;
        if (pdDocument.getNumberOfPages() > 0) {
            PDRectangle r = pdDocument.getPage(0).getMediaBox();
            if (r != null) {
                paperW = Math.min(r.getWidth(), r.getHeight());
                paperH = Math.max(r.getWidth(), r.getHeight());
            }
        }

        boolean isLandscape = isLandscapeSheet(opts, ordered);
        float sheetW = isLandscape ? paperH : paperW;
        float sheetH = isLandscape ? paperW : paperH;
        PDRectangle sheetBox = new PDRectangle(sheetW, sheetH);

        LayerUtility layerUtil = new LayerUtility(printDoc);

        if (opts.isBookletPrint()) {
            buildBookletDocument(printDoc, layerUtil, ordered, sheetBox, opts);
        } else if (opts.getPageHandlingMode() == PrintOptions.PageHandlingMode.MULTIPLE && opts.nupCount() > 1) {
            buildNUpDocument(printDoc, layerUtil, ordered, sheetBox, opts);
        } else {
            buildScaleDocument(printDoc, layerUtil, ordered, sheetBox, opts);
        }

        return printDoc;
    }

    public boolean isLandscapeSheet(PrintOptions opts, List<Integer> pageIndices) {
        if (opts.getOrientation() == PrintOptions.OrientationMode.LANDSCAPE) return true;
        if (opts.getOrientation() == PrintOptions.OrientationMode.PORTRAIT) return false;

        // Auto mode:
        if (opts.isBookletPrint()) return true;
        if (opts.getPageHandlingMode() == PrintOptions.PageHandlingMode.MULTIPLE) {
            // 2-up is 2 portrait pages side-by-side -> sheet is landscape
            if (opts.getPagesPerSheet() == PrintOptions.PagesPerSheet.TWO) return true;
            if (opts.getPagesPerSheet() == PrintOptions.PagesPerSheet.SIX) return true;
        }

        if (!pageIndices.isEmpty()) {
            int firstIdx = pageIndices.get(0);
            if (firstIdx >= 0 && firstIdx < pdDocument.getNumberOfPages()) {
                PDRectangle box = pdDocument.getPage(firstIdx).getMediaBox();
                if (box != null && box.getWidth() > box.getHeight()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void buildScaleDocument(PDDocument printDoc, LayerUtility layerUtil,
                                    List<Integer> pages, PDRectangle sheetBox,
                                    PrintOptions opts) throws IOException {
        double margin = opts.getEffectiveMarginPoints();
        double availW = Math.max(10.0, sheetBox.getWidth() - 2.0 * margin);
        double availH = Math.max(10.0, sheetBox.getHeight() - 2.0 * margin);

        for (int pageIdx : pages) {
            if (pageIdx < 0 || pageIdx >= pdDocument.getNumberOfPages()) continue;
            PDPage srcPage = pdDocument.getPage(pageIdx);
            PDRectangle srcBox = srcPage.getMediaBox();
            if (srcBox == null) srcBox = PDRectangle.A4;

            PDPage sheetPage = new PDPage(sheetBox);
            printDoc.addPage(sheetPage);

            PDFormXObject form = layerUtil.importPageAsForm(pdDocument, srcPage);
            double srcW = srcBox.getWidth();
            double srcH = srcBox.getHeight();

            double baseFitScale = Math.min(availW / srcW, availH / srcH);
            double scale = switch (opts.getFitMode()) {
                case ACTUAL_SIZE      -> 1.0;
                case CUSTOM_SCALE     -> (opts.getCustomScalePct() / 100.0);
                case SHRINK_OVERSIZED -> Math.min(1.0, baseFitScale);
                case FIT_PAGE         -> baseFitScale;
            };

            double dw = srcW * scale;
            double dh = srcH * scale;

            double dx = margin;
            double dy = margin;
            if (opts.isCenterOnPage()) {
                dx = margin + (availW - dw) / 2.0;
                dy = margin + (availH - dh) / 2.0;
            }

            try (PDPageContentStream cs = new PDPageContentStream(printDoc, sheetPage, PDPageContentStream.AppendMode.OVERWRITE, false)) {
                cs.saveGraphicsState();
                Matrix matrix = Matrix.getTranslateInstance((float) dx, (float) dy);
                matrix.scale((float) scale, (float) scale);
                cs.transform(matrix);
                cs.drawForm(form);
                cs.restoreGraphicsState();
            }
        }
    }

    private void buildNUpDocument(PDDocument printDoc, LayerUtility layerUtil,
                                  List<Integer> pages, PDRectangle sheetBox,
                                  PrintOptions opts) throws IOException {
        int nup = opts.nupCount();
        int cols = switch (nup) {
            case 1 -> 1; case 2 -> 2; case 4 -> 2;
            case 6 -> 3; case 9 -> 3; case 16 -> 4;
            default -> 2;
        };
        int rows = switch (nup) {
            case 1 -> 1; case 2 -> 1; case 4 -> 2;
            case 6 -> 2; case 9 -> 3; case 16 -> 4;
            default -> 2;
        };

        float cellW = sheetBox.getWidth() / cols;
        float cellH = sheetBox.getHeight() / rows;
        double margin = opts.getEffectiveMarginPoints() * (cellW / sheetBox.getWidth());
        double availW = Math.max(10.0, cellW - 2.0 * margin);
        double availH = Math.max(10.0, cellH - 2.0 * margin);

        for (int i = 0; i < pages.size(); i += nup) {
            PDPage sheetPage = new PDPage(sheetBox);
            printDoc.addPage(sheetPage);

            try (PDPageContentStream cs = new PDPageContentStream(printDoc, sheetPage, PDPageContentStream.AppendMode.OVERWRITE, false)) {
                for (int k = 0; k < nup; k++) {
                    int slot = k;
                    int idx = i + k;
                    if (idx >= pages.size()) break;
                    int pageIdx = pages.get(idx);
                    if (pageIdx < 0 || pageIdx >= pdDocument.getNumberOfPages()) continue;

                    int r = slot / cols;
                    int c = slot % cols;
                    if (!opts.isNupHorizontalFirst()) {
                        r = slot % rows;
                        c = slot / rows;
                    }

                    float cellX = c * cellW;
                    float cellY = (rows - 1 - r) * cellH;

                    PDPage srcPage = pdDocument.getPage(pageIdx);
                    PDRectangle srcBox = srcPage.getMediaBox();
                    if (srcBox == null) srcBox = PDRectangle.A4;

                    PDFormXObject form = layerUtil.importPageAsForm(pdDocument, srcPage);
                    double srcW = srcBox.getWidth();
                    double srcH = srcBox.getHeight();

                    double scale = Math.min(availW / srcW, availH / srcH);
                    double dw = srcW * scale;
                    double dh = srcH * scale;

                    double dx = cellX + margin + (availW - dw) / 2.0;
                    double dy = cellY + margin + (availH - dh) / 2.0;

                    cs.saveGraphicsState();
                    Matrix matrix = Matrix.getTranslateInstance((float) dx, (float) dy);
                    matrix.scale((float) scale, (float) scale);
                    cs.transform(matrix);
                    cs.drawForm(form);
                    cs.restoreGraphicsState();

                    if (opts.isNupPageBorders()) {
                        cs.setStrokingColor(0.82f, 0.82f, 0.82f);
                        cs.setLineWidth(0.5f);
                        cs.addRect(cellX + 0.5f, cellY + 0.5f, cellW - 1f, cellH - 1f);
                        cs.stroke();
                    }
                }
            }
        }
    }

    private void buildBookletDocument(PDDocument printDoc, LayerUtility layerUtil,
                                      List<Integer> pages, PDRectangle sheetBox,
                                      PrintOptions opts) throws IOException {
        int total = pages.size();
        int padded = ((total + 3) / 4) * 4;
        int sheets = padded / 4;

        float cellW = sheetBox.getWidth() / 2f;
        float cellH = sheetBox.getHeight();
        double margin = opts.getEffectiveMarginPoints() * (cellW / sheetBox.getWidth());
        double availW = Math.max(10.0, cellW - 2.0 * margin);
        double availH = Math.max(10.0, cellH - 2.0 * margin);

        for (int s = 0; s < sheets; s++) {
            // Front side
            if (opts.getBookletSubset() != PrintOptions.BookletSubset.BACK_SIDE_ONLY) {
                int[] pair = bookletPagePairFor(s, padded);
                PDPage sheetPage = new PDPage(sheetBox);
                printDoc.addPage(sheetPage);
                drawBookletSide(printDoc, sheetPage, layerUtil, pair, pages, cellW, cellH, margin, availW, availH);
            }
            // Back side
            if (opts.getBookletSubset() != PrintOptions.BookletSubset.FRONT_SIDE_ONLY) {
                int[] backPair = bookletBackPagePairFor(s, padded);
                PDPage sheetPage = new PDPage(sheetBox);
                printDoc.addPage(sheetPage);
                drawBookletSide(printDoc, sheetPage, layerUtil, backPair, pages, cellW, cellH, margin, availW, availH);
            }
        }
    }

    private void drawBookletSide(PDDocument printDoc, PDPage sheetPage, LayerUtility layerUtil,
                                 int[] pair, List<Integer> pages,
                                 float cellW, float cellH, double margin,
                                 double availW, double availH) throws IOException {
        try (PDPageContentStream cs = new PDPageContentStream(printDoc, sheetPage, PDPageContentStream.AppendMode.OVERWRITE, false)) {
            for (int k = 0; k < 2; k++) {
                int pageNum = pair[k];
                if (pageNum < pages.size()) {
                    int pageIdx = pages.get(pageNum);
                    if (pageIdx >= 0 && pageIdx < pdDocument.getNumberOfPages()) {
                        float cellX = k * cellW;
                        float cellY = 0f;

                        PDPage srcPage = pdDocument.getPage(pageIdx);
                        PDRectangle srcBox = srcPage.getMediaBox();
                        if (srcBox == null) srcBox = PDRectangle.A4;

                        PDFormXObject form = layerUtil.importPageAsForm(pdDocument, srcPage);
                        double srcW = srcBox.getWidth();
                        double srcH = srcBox.getHeight();

                        double scale = Math.min(availW / srcW, availH / srcH);
                        double dw = srcW * scale;
                        double dh = srcH * scale;

                        double dx = cellX + margin + (availW - dw) / 2.0;
                        double dy = cellY + margin + (availH - dh) / 2.0;

                        cs.saveGraphicsState();
                        Matrix matrix = Matrix.getTranslateInstance((float) dx, (float) dy);
                        matrix.scale((float) scale, (float) scale);
                        cs.transform(matrix);
                        cs.drawForm(form);
                        cs.restoreGraphicsState();
                    }
                }
            }
        }
    }

    public static PrintService resolvePrintService(PrintOptions opts) {
        String name = (opts.getPrinter() != null) ? opts.getPrinter().getName() : null;
        PrintService[] services = java.awt.print.PrinterJob.lookupPrintServices();
        if (name != null) {
            for (PrintService ps : services) {
                if (ps.getName().equalsIgnoreCase(name)) {
                    return ps;
                }
            }
        }
        return java.awt.print.PrinterJob.getPrinterJob().getPrintService();
    }

    private PrintRequestAttributeSet buildPrintAttributes(PrintOptions opts, PDDocument printDoc) {
        PrintRequestAttributeSet attr = new HashPrintRequestAttributeSet();

        boolean isLandscape = isLandscapeSheet(opts, resolvePageIndices(opts));
        if (isLandscape) {
            attr.add(OrientationRequested.LANDSCAPE);
        } else {
            attr.add(OrientationRequested.PORTRAIT);
        }

        int copies = Math.max(1, opts.getCopies());
        attr.add(new Copies(copies));

        if (opts.isCollate()) {
            attr.add(SheetCollate.COLLATED);
        } else {
            attr.add(SheetCollate.UNCOLLATED);
        }

        switch (opts.getDuplexMode()) {
            case LONG_EDGE  -> attr.add(Sides.TWO_SIDED_LONG_EDGE);
            case SHORT_EDGE -> attr.add(Sides.TWO_SIDED_SHORT_EDGE);
            case NONE       -> attr.add(Sides.ONE_SIDED);
        }

        if (opts.isGrayscale()) {
            attr.add(Chromaticity.MONOCHROME);
        } else {
            attr.add(Chromaticity.COLOR);
        }

        return attr;
    }

    private org.apache.pdfbox.printing.Orientation resolvePdfBoxOrientation(PrintOptions opts, PDDocument printDoc) {
        if (opts.getOrientation() == PrintOptions.OrientationMode.LANDSCAPE) {
            return org.apache.pdfbox.printing.Orientation.LANDSCAPE;
        } else if (opts.getOrientation() == PrintOptions.OrientationMode.PORTRAIT) {
            return org.apache.pdfbox.printing.Orientation.PORTRAIT;
        }
        if (isLandscapeSheet(opts, resolvePageIndices(opts))) {
            return org.apache.pdfbox.printing.Orientation.LANDSCAPE;
        }
        return org.apache.pdfbox.printing.Orientation.AUTO;
    }

    /**
     * Renders a preview image for the given page index (0-based PDF page) at the requested
     * width/height in pixels. Used by {@link PrintPreviewDialog}.
     *
     * @param pageIndex   0-based PDF page index
     * @param opts        current print options
     * @param widthPx     desired output width in pixels
     * @param heightPx    desired output height in pixels
     * @return JavaFX Image
     */
    public Image renderPreview(int pageIndex, PrintOptions opts, double widthPx, double heightPx) {
        // Build the logical sheet (possibly N-up or booklet) starting at this pageIndex
        int nup = opts.nupCount();
        List<Integer> indices = new ArrayList<>();
        indices.add(pageIndex);
        // For N-up, fill remaining slots with subsequent pages (wrap at document end)
        for (int i = 1; i < nup; i++) {
            int next = pageIndex + i;
            indices.add(next < pdDocument.getNumberOfPages() ? next : -1);
        }
        return compositeSheetPreviewPublic(indices, opts, widthPx, heightPx, false);
    }

    /**
     * Renders a booklet sheet preview image (front = left half, back = right half).
     */
    public Image renderBookletSheetPreview(int sheetIndex, PrintOptions opts, double widthPx, double heightPx) {
        int total = pdDocument.getNumberOfPages();
        // Saddle-stitch order: sheet 0 = [back-last, front-first], ...
        int[] pagePair = bookletPagePairFor(sheetIndex, total);
        List<Integer> indices = Arrays.asList(
                pagePair[0] < total ? pagePair[0] : -1,
                pagePair[1] < total ? pagePair[1] : -1
        );
        return compositeSheetPreviewPublic(indices, opts, widthPx, heightPx, true);
    }

    // =========================================================================
    // Page index resolution
    // =========================================================================

    public List<Integer> resolvePageIndices(PrintOptions opts) {
        int total = pdDocument.getNumberOfPages();
        return switch (opts.getPageRangeMode()) {
            case ALL     -> allPages(total);
            case CURRENT -> List.of(0); // caller replaces with actual current page
            case CUSTOM  -> parseCustomRange(opts.getCustomPageRange(), total);
        };
    }

    public List<Integer> resolvePageIndices(PrintOptions opts, int currentPage) {
        int total = pdDocument.getNumberOfPages();
        return switch (opts.getPageRangeMode()) {
            case ALL     -> allPages(total);
            case CURRENT -> List.of(Math.max(0, Math.min(total - 1, currentPage)));
            case CUSTOM  -> parseCustomRange(opts.getCustomPageRange(), total);
        };
    }

    private List<Integer> allPages(int total) {
        List<Integer> list = new ArrayList<>(total);
        for (int i = 0; i < total; i++) list.add(i);
        return list;
    }

    /**
     * Parses strings like "1,3,5-8,10" into 0-based page indices.
     */
    public static List<Integer> parseCustomRange(String rangeStr, int totalPages) {
        List<Integer> result = new LinkedList<>();
        if (rangeStr == null || rangeStr.isBlank()) return result;
        for (String token : rangeStr.split(",")) {
            token = token.trim();
            if (token.contains("-")) {
                String[] parts = token.split("-", 2);
                try {
                    int from = Integer.parseInt(parts[0].trim()) - 1;
                    int to   = Integer.parseInt(parts[1].trim()) - 1;
                    for (int i = Math.max(0, from); i <= Math.min(totalPages - 1, to); i++) {
                        result.add(i);
                    }
                } catch (NumberFormatException ignored) {}
            } else {
                try {
                    int p = Integer.parseInt(token) - 1;
                    if (p >= 0 && p < totalPages) result.add(p);
                } catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }

    // =========================================================================
    // Simple (1-up) printing
    // =========================================================================

    private boolean printSimple(PrinterJob job, PrintOptions opts, List<Integer> pageIndices, PageLayout layout) {
        List<Integer> ordered = applyOrder(pageIndices, opts.isReverseOrder());
        int copies = Math.max(1, opts.getCopies());
        if (opts.isCollate() || copies == 1) {
            for (int copy = 0; copy < copies; copy++) {
                for (int pageIdx : ordered) {
                    Image img = renderPageForPrint(pageIdx, opts);
                    if (img == null) continue;
                    Group node = composeSinglePage(pageIdx, img, layout, opts);
                    if (!job.printPage(layout, node)) return false;
                }
            }
        } else {
            // Uncollated: print all copies of page 1, then all copies of page 2...
            for (int pageIdx : ordered) {
                Image img = renderPageForPrint(pageIdx, opts);
                if (img == null) continue;
                for (int copy = 0; copy < copies; copy++) {
                    Group node = composeSinglePage(pageIdx, img, layout, opts);
                    if (!job.printPage(layout, node)) return false;
                }
            }
        }
        return true;
    }

    // =========================================================================
    // N-Up printing
    // =========================================================================

    private boolean printNUp(PrinterJob job, PrintOptions opts, List<Integer> pageIndices, PageLayout layout) {
        int nup = opts.nupCount();
        List<Integer> ordered = applyOrder(pageIndices, opts.isReverseOrder());
        int copies = Math.max(1, opts.getCopies());

        if (opts.isCollate() || copies == 1) {
            for (int copy = 0; copy < copies; copy++) {
                if (!printNUpSequence(job, opts, ordered, nup, layout)) return false;
            }
        } else {
            for (int i = 0; i < ordered.size(); i += nup) {
                List<Integer> chunk = new ArrayList<>();
                for (int k = 0; k < nup; k++) {
                    int idx = i + k;
                    chunk.add(idx < ordered.size() ? ordered.get(idx) : -1);
                }
                double pw = layout.getPrintableWidth();
                double ph = layout.getPrintableHeight();
                for (int copy = 0; copy < copies; copy++) {
                    Canvas canvas = composeSheetCanvas(chunk, opts, pw, ph, false);
                    if (!job.printPage(layout, new Group(canvas))) return false;
                }
            }
        }
        return true;
    }

    private boolean printNUpSequence(PrinterJob job, PrintOptions opts, List<Integer> ordered, int nup, PageLayout layout) {
        for (int i = 0; i < ordered.size(); i += nup) {
            List<Integer> chunk = new ArrayList<>();
            for (int k = 0; k < nup; k++) {
                int idx = i + k;
                chunk.add(idx < ordered.size() ? ordered.get(idx) : -1);
            }
            double pw = layout.getPrintableWidth();
            double ph = layout.getPrintableHeight();
            Canvas canvas = composeSheetCanvas(chunk, opts, pw, ph, false);
            if (!job.printPage(layout, new Group(canvas))) return false;
        }
        return true;
    }

    // =========================================================================
    // Booklet printing
    // =========================================================================

    private boolean printBooklet(PrinterJob job, PrintOptions opts, List<Integer> pageIndices, PageLayout layout) {
        int total = pdDocument.getNumberOfPages();
        // Pad to multiple of 4
        int padded = ((total + 3) / 4) * 4;
        int sheets = padded / 4;

        for (int copy = 0; copy < opts.getCopies(); copy++) {
            for (int s = 0; s < sheets; s++) {
                // Front side
                if (opts.getBookletSubset() != PrintOptions.BookletSubset.BACK_SIDE_ONLY) {
                    int[] pair = bookletPagePairFor(s, total);
                    List<Integer> frontPages = Arrays.asList(
                            pair[0] < total ? pair[0] : -1,
                            pair[1] < total ? pair[1] : -1);
                    Canvas cv = composeSheetCanvas(frontPages, opts,
                            layout.getPrintableWidth(), layout.getPrintableHeight(), true);
                    if (!job.printPage(layout, new Group(cv))) return false;
                }
                // Back side
                if (opts.getBookletSubset() != PrintOptions.BookletSubset.FRONT_SIDE_ONLY) {
                    int[] backPair = bookletBackPagePairFor(s, total);
                    List<Integer> backPages = Arrays.asList(
                            backPair[0] < total ? backPair[0] : -1,
                            backPair[1] < total ? backPair[1] : -1);
                    Canvas cv = composeSheetCanvas(backPages, opts,
                            layout.getPrintableWidth(), layout.getPrintableHeight(), true);
                    if (!job.printPage(layout, new Group(cv))) return false;
                }
            }
        }
        return true;
    }

    /**
     * Saddle-stitch front pair for a sheet (0-based sheet index).
     * Sheet 0 front: [last, 0], Sheet 1 front: [last-2, 2], …
     */
    private int[] bookletPagePairFor(int sheetIndex, int total) {
        int padded = ((total + 3) / 4) * 4;
        int outerPage = padded - 1 - sheetIndex * 2;   // right side of front face
        int innerPage = sheetIndex * 2;                  // left side of front face
        return new int[]{outerPage, innerPage};
    }

    private int[] bookletBackPagePairFor(int sheetIndex, int total) {
        int padded = ((total + 3) / 4) * 4;
        int leftPage  = sheetIndex * 2 + 1;
        int rightPage = padded - 2 - sheetIndex * 2;
        return new int[]{leftPage, rightPage};
    }

    // =========================================================================
    // Sheet compositing (for physical printing via Canvas)
    // =========================================================================

    private Canvas composeSheetCanvas(List<Integer> pageIndices, PrintOptions opts,
                                      double sheetW, double sheetH, boolean twoColLayout) {
        int count = pageIndices.size();
        int cols, rows;
        if (twoColLayout) {
            cols = 2; rows = 1;
        } else {
            int nup = opts.nupCount();
            cols = switch (nup) {
                case 1 -> 1; case 2 -> 2; case 4 -> 2;
                case 6 -> 3; case 9 -> 3; case 16 -> 4;
                default -> 2;
            };
            rows = switch (nup) {
                case 1 -> 1; case 2 -> 1; case 4 -> 2;
                case 6 -> 2; case 9 -> 3; case 16 -> 4;
                default -> 2;
            };
        }

        double cellW = sheetW / cols;
        double cellH = sheetH / rows;

        Canvas canvas = new Canvas(sheetW, sheetH);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, sheetW, sheetH);

        for (int slot = 0; slot < Math.min(count, cols * rows); slot++) {
            int r = slot / cols;
            int c = slot % cols;
            if (!opts.isNupHorizontalFirst() && !twoColLayout) {
                r = slot % rows;
                c = slot / rows;
            }

            double cellX = c * cellW;
            double cellY = r * cellH;
            int pageIdx = pageIndices.get(slot);

            if (pageIdx >= 0 && pageIdx < pdDocument.getNumberOfPages()) {
                Image pageImg = renderPageForPrint(pageIdx, opts);
                if (pageImg != null) {
                    double[] dest = fitImageInCell(pageIdx, pageImg.getWidth(), pageImg.getHeight(),
                            cellX, cellY, cellW, cellH, opts);
                    gc.drawImage(pageImg, dest[0], dest[1], dest[2], dest[3]);
                }
            }

            if (opts.isNupPageBorders() && (twoColLayout || opts.nupCount() > 1)) {
                gc.setStroke(Color.LIGHTGRAY);
                gc.setLineWidth(0.5);
                gc.strokeRect(cellX + 0.5, cellY + 0.5, cellW - 1, cellH - 1);
            }
        }
        return canvas;
    }

    // =========================================================================
    // Fast Background Preview Compositing (AWT in-memory, thread-safe, 0% hang)
    // =========================================================================

    /**
     * Public entry point for {@link PrintPreviewDialog} to render a fast preview sheet
     * on any background thread without JavaFX UI thread locking or snapshot overhead.
     */
    public Image compositeSheetPreviewPublic(List<Integer> pageIndices, PrintOptions opts,
                                              double sheetW, double sheetH, boolean twoColLayout) {
        int count = pageIndices.size();
        if (count == 0) return null;

        int cols, rows;
        if (twoColLayout) {
            cols = 2; rows = 1;
        } else {
            int nup = opts.nupCount();
            cols = switch (nup) {
                case 1 -> 1; case 2 -> 2; case 4 -> 2;
                case 6 -> 3; case 9 -> 3; case 16 -> 4;
                default -> 2;
            };
            rows = switch (nup) {
                case 1 -> 1; case 2 -> 1; case 4 -> 2;
                case 6 -> 2; case 9 -> 3; case 16 -> 4;
                default -> 2;
            };
        }

        int targetW = (int) Math.max(200, Math.min(1200, sheetW));
        int targetH = (int) Math.max(200, Math.min(1200, sheetH));

        BufferedImage buf = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = buf.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            g2.setColor(java.awt.Color.WHITE);
            g2.fillRect(0, 0, targetW, targetH);

            double cellW = (double) targetW / cols;
            double cellH = (double) targetH / rows;

            for (int slot = 0; slot < Math.min(count, cols * rows); slot++) {
                int r = slot / cols;
                int c = slot % cols;
                if (!opts.isNupHorizontalFirst() && !twoColLayout) {
                    r = slot % rows;
                    c = slot / rows;
                }

                double cellX = c * cellW;
                double cellY = r * cellH;
                int pageIdx = pageIndices.get(slot);

                if (pageIdx >= 0 && pageIdx < pdDocument.getNumberOfPages()) {
                    Image pageImg = renderPageForPreview(pageIdx, opts);
                    if (pageImg != null) {
                        BufferedImage awtPage = SwingFXUtils.fromFXImage(pageImg, null);
                        if (awtPage != null) {
                            double[] dest = fitImageInCell(pageIdx, awtPage.getWidth(), awtPage.getHeight(),
                                    cellX, cellY, cellW, cellH, opts);
                            g2.drawImage(awtPage, (int) Math.round(dest[0]), (int) Math.round(dest[1]),
                                    (int) Math.round(dest[2]), (int) Math.round(dest[3]), null);
                        }
                    }
                }

                if (opts.isNupPageBorders() && (twoColLayout || opts.nupCount() > 1)) {
                    g2.setColor(new java.awt.Color(210, 210, 210));
                    g2.drawRect((int) Math.round(cellX), (int) Math.round(cellY),
                            (int) Math.round(cellW) - 1, (int) Math.round(cellH) - 1);
                }
            }
        } finally {
            g2.dispose();
        }

        return SwingFXUtils.toFXImage(buf, null);
    }

    private Image renderPageForPreview(int pageIdx, PrintOptions opts) {
        // Render at 0.75 zoom (90 DPI) for instant, responsive preview
        Image raw = renderer.renderPage(pageIdx, 0.75, userRotation);
        if (raw == null) return null;
        if (opts.isGrayscale()) {
            raw = toGrayscale(raw);
        }
        if (opts.isAutoRotate()) {
            raw = maybeAutoRotate(raw, pageIdx, opts);
        }
        return raw;
    }

    // =========================================================================
    // Single-page node composition
    // =========================================================================

    private Group composeSinglePage(int pageIdx, Image img, PageLayout layout, PrintOptions opts) {
        double pw = layout.getPrintableWidth();
        double ph = layout.getPrintableHeight();
        Canvas canvas = new Canvas(pw, ph);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, pw, ph);
        double[] dest = fitImageInCell(pageIdx, img.getWidth(), img.getHeight(), 0, 0, pw, ph, opts);
        gc.drawImage(img, dest[0], dest[1], dest[2], dest[3]);
        return new Group(canvas);
    }

    // =========================================================================
    // Fit / scale calculation
    // =========================================================================

    /**
     * Returns [x, y, width, height] placing an image (with native pixel size imgW x imgH)
     * into a cell of size (cellW x cellH) according to the FitMode, optionally centering.
     */
    private double[] fitImageInCell(int pageIdx, double imgW, double imgH,
                                     double cellX, double cellY, double cellW, double cellH,
                                     PrintOptions opts) {
        double marginPts = opts.getEffectiveMarginPoints();
        // Scale margin points proportionally to cell width (standard A4 width = 595.28 pt)
        double margin = marginPts * (cellW / 595.28);
        margin = Math.max(0.0, Math.min(Math.min(cellW, cellH) * 0.4, margin));

        double availW = Math.max(10.0, cellW - 2.0 * margin);
        double availH = Math.max(10.0, cellH - 2.0 * margin);

        double baseFitScale = Math.min(availW / imgW, availH / imgH);

        double pageWPt = 595.28;
        double pageHPt = 841.89;
        if (pageIdx >= 0 && pageIdx < pdDocument.getNumberOfPages()) {
            PDPage page = pdDocument.getPage(pageIdx);
            PDRectangle box = page != null ? page.getMediaBox() : null;
            if (box != null) {
                pageWPt = box.getWidth();
                pageHPt = box.getHeight();
            }
        }

        // Compare native page dimensions to standard sheet dimensions (A4 = 595.28 x 841.89)
        double normRatio = Math.min(pageWPt / 595.28, pageHPt / 841.89);
        double actualScale = baseFitScale * normRatio;

        double scale = switch (opts.getFitMode()) {
            case ACTUAL_SIZE      -> actualScale;
            case CUSTOM_SCALE     -> actualScale * (opts.getCustomScalePct() / 100.0);
            case SHRINK_OVERSIZED -> Math.min(actualScale, baseFitScale);
            case FIT_PAGE         -> baseFitScale;
        };

        double dw = imgW * scale;
        double dh = imgH * scale;

        double dx = cellX + margin;
        double dy = cellY + margin;
        if (opts.isCenterOnPage()) {
            dx = cellX + margin + (availW - dw) / 2.0;
            dy = cellY + margin + (availH - dh) / 2.0;
        }

        return new double[]{dx, dy, dw, dh};
    }

    // =========================================================================
    // Page rendering
    // =========================================================================

    private Image renderPageForPrint(int pageIdx, PrintOptions opts) {
        // Use a print-quality DPI (150 DPI target)
        Image raw = renderer.renderPage(pageIdx, 2.0, userRotation);
        if (raw == null) return null;
        if (opts.isGrayscale()) {
            raw = toGrayscale(raw);
        }
        // Auto-rotate: if the page is wider than tall but paper is portrait, rotate 90°
        if (opts.isAutoRotate()) {
            raw = maybeAutoRotate(raw, pageIdx, opts);
        }
        return raw;
    }

    private Image toGrayscale(Image src) {
        int w = (int) src.getWidth();
        int h = (int) src.getHeight();
        WritableImage dst = new WritableImage(w, h);
        var reader = src.getPixelReader();
        var writer = dst.getPixelWriter();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                javafx.scene.paint.Color c = reader.getColor(x, y);
                double grey = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
                writer.setColor(x, y, new javafx.scene.paint.Color(grey, grey, grey, c.getOpacity()));
            }
        }
        return dst;
    }

    private Image maybeAutoRotate(Image img, int pageIdx, PrintOptions opts) {
        if (pageIdx < 0 || pageIdx >= pdDocument.getNumberOfPages()) return img;
        PDPage page = pdDocument.getPage(pageIdx);
        PDRectangle box = page.getMediaBox();
        boolean pageIsLandscape = box != null && box.getWidth() > box.getHeight();

        PrintOptions.OrientationMode oMode = opts.getOrientation();
        if (oMode == PrintOptions.OrientationMode.AUTO) return img;

        boolean wantLandscape = oMode == PrintOptions.OrientationMode.LANDSCAPE;
        if (pageIsLandscape != wantLandscape) {
            // Rotate image 90° using AWT
            return rotateImage90(img);
        }
        return img;
    }

    private Image rotateImage90(Image src) {
        int w = (int) src.getWidth();
        int h = (int) src.getHeight();
        BufferedImage bSrc = SwingFXUtils.fromFXImage(src, null);
        BufferedImage bDst = new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                bDst.setRGB(h - 1 - y, x, bSrc.getRGB(x, y));
            }
        }
        return SwingFXUtils.toFXImage(bDst, null);
    }

    // =========================================================================
    // PrinterJob configuration helpers
    // =========================================================================

    private PageLayout buildPageLayout(PrinterJob job, PrintOptions opts, List<Integer> pageIndices) {
        Printer printer = opts.getPrinter() != null ? opts.getPrinter() : Printer.getDefaultPrinter();
        if (printer == null) return job.getJobSettings().getPageLayout();

        // Choose paper orientation based on first page or user setting
        PageOrientation orientation;
        if (!pageIndices.isEmpty() && opts.getOrientation() == PrintOptions.OrientationMode.AUTO) {
            int firstPage = pageIndices.get(0);
            if (firstPage >= 0 && firstPage < pdDocument.getNumberOfPages()) {
                PDPage page = pdDocument.getPage(firstPage);
                PDRectangle box = page.getMediaBox();
                orientation = (box != null && box.getWidth() > box.getHeight())
                              ? PageOrientation.LANDSCAPE : PageOrientation.PORTRAIT;
            } else {
                orientation = PageOrientation.PORTRAIT;
            }
        } else {
            orientation = switch (opts.getOrientation()) {
                case PORTRAIT  -> PageOrientation.PORTRAIT;
                case LANDSCAPE -> PageOrientation.LANDSCAPE;
                default        -> PageOrientation.PORTRAIT;
            };
        }

        // For booklet or 2-up: landscape is used so both pages fit side-by-side
        if (opts.isBookletPrint() || opts.getPagesPerSheet() == PrintOptions.PagesPerSheet.TWO) {
            orientation = PageOrientation.LANDSCAPE;
        }

        Paper paper = Paper.A4;
        try {
            var supported = printer.getPrinterAttributes().getSupportedPapers();
            if (supported != null && !supported.isEmpty()) {
                paper = supported.iterator().next();
            }
        } catch (Exception ignored) {}

        return printer.createPageLayout(paper, orientation, Printer.MarginType.DEFAULT);
    }

    private void setDuplexHint(PrinterJob job, PrintOptions opts) {
        try {
            switch (opts.getDuplexMode()) {
                case LONG_EDGE  -> job.getJobSettings().setPrintSides(PrintSides.DUPLEX);
                case SHORT_EDGE -> job.getJobSettings().setPrintSides(PrintSides.TUMBLE);
                case NONE       -> job.getJobSettings().setPrintSides(PrintSides.ONE_SIDED);
            }
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private List<Integer> applyOrder(List<Integer> indices, boolean reverse) {
        if (!reverse) return new ArrayList<>(indices);
        List<Integer> rev = new ArrayList<>(indices);
        Collections.reverse(rev);
        return rev;
    }

    public int getTotalPages() {
        return pdDocument.getNumberOfPages();
    }
}
