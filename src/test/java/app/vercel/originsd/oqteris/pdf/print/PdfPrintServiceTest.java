package app.vercel.originsd.oqteris.pdf.print;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfPrintServiceTest {

    private PDDocument document;

    @BeforeEach
    void setUp() {
        document = new PDDocument();
        // Create 10 dummy pages
        for (int i = 0; i < 10; i++) {
            document.addPage(new PDPage(PDRectangle.A4));
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        if (document != null) {
            document.close();
        }
    }

    @Test
    void testParseCustomRange() {
        List<Integer> pages = PdfPrintService.parseCustomRange("1, 3, 5-8, 10", 10);
        assertEquals(List.of(0, 2, 4, 5, 6, 7, 9), pages);
    }

    @Test
    void testParseCustomRangeOutOfBounds() {
        List<Integer> pages = PdfPrintService.parseCustomRange("0, 1, 9-15", 10);
        // Page 0 (1-based -> index -1) is ignored, 1 -> 0, 9-15 clamped to 9 (index 8..9)
        assertEquals(List.of(0, 8, 9), pages);
    }

    @Test
    void testParseCustomRangeEmpty() {
        List<Integer> pages = PdfPrintService.parseCustomRange("", 10);
        assertTrue(pages.isEmpty());
        pages = PdfPrintService.parseCustomRange(null, 10);
        assertTrue(pages.isEmpty());
    }

    @Test
    void testPrintOptionsDefaults() {
        PrintOptions opts = new PrintOptions();
        assertEquals(PrintOptions.PageRangeMode.ALL, opts.getPageRangeMode());
        assertEquals(1, opts.getCopies());
        assertTrue(opts.isCollate());
        assertFalse(opts.isReverseOrder());
        assertEquals(PrintOptions.OrientationMode.AUTO, opts.getOrientation());
        assertEquals(PrintOptions.PagesPerSheet.ONE, opts.getPagesPerSheet());
        assertEquals(PrintOptions.DuplexMode.NONE, opts.getDuplexMode());
        assertFalse(opts.isBookletPrint());
        assertEquals(PrintOptions.FitMode.FIT_PAGE, opts.getFitMode());
        assertTrue(opts.isAutoRotate());
        assertTrue(opts.isCenterOnPage());
        assertFalse(opts.isGrayscale());
        assertTrue(opts.isPrintAnnotations());
        assertTrue(opts.isPrintFormFields());
    }

    @Test
    void testResolvePageIndicesAll() {
        PdfPrintService service = new PdfPrintService(document, null, 0);
        PrintOptions opts = new PrintOptions();
        opts.setPageRangeMode(PrintOptions.PageRangeMode.ALL);

        List<Integer> pages = service.resolvePageIndices(opts, 0);
        assertEquals(10, pages.size());
        assertEquals(0, pages.get(0));
        assertEquals(9, pages.get(9));
    }

    @Test
    void testResolvePageIndicesCurrent() {
        PdfPrintService service = new PdfPrintService(document, null, 0);
        PrintOptions opts = new PrintOptions();
        opts.setPageRangeMode(PrintOptions.PageRangeMode.CURRENT);

        List<Integer> pages = service.resolvePageIndices(opts, 4);
        assertEquals(List.of(4), pages);
    }

    @Test
    void testResolvePageIndicesCustom() {
        PdfPrintService service = new PdfPrintService(document, null, 0);
        PrintOptions opts = new PrintOptions();
        opts.setPageRangeMode(PrintOptions.PageRangeMode.CUSTOM);
        opts.setCustomPageRange("2-4, 7");

        List<Integer> pages = service.resolvePageIndices(opts, 0);
        assertEquals(List.of(1, 2, 3, 6), pages);
    }

    @Test
    void testBookletSheetCount() {
        PrintOptions opts = new PrintOptions();
        assertEquals(1, opts.bookletSheetCount(1));
        assertEquals(1, opts.bookletSheetCount(4));
        assertEquals(2, opts.bookletSheetCount(5));
        assertEquals(3, opts.bookletSheetCount(10));
    }

    @Test
    void testNupCount() {
        PrintOptions opts = new PrintOptions();
        // Default mode is SCALE, so nupCount is 1
        opts.setPagesPerSheet(PrintOptions.PagesPerSheet.FOUR);
        assertEquals(1, opts.nupCount());

        // Switch to MULTIPLE pages mode
        opts.setPageHandlingMode(PrintOptions.PageHandlingMode.MULTIPLE);

        opts.setPagesPerSheet(PrintOptions.PagesPerSheet.ONE);
        assertEquals(1, opts.nupCount());

        opts.setPagesPerSheet(PrintOptions.PagesPerSheet.TWO);
        assertEquals(2, opts.nupCount());

        opts.setPagesPerSheet(PrintOptions.PagesPerSheet.FOUR);
        assertEquals(4, opts.nupCount());

        opts.setPagesPerSheet(PrintOptions.PagesPerSheet.SIX);
        assertEquals(6, opts.nupCount());

        opts.setPagesPerSheet(PrintOptions.PagesPerSheet.NINE);
        assertEquals(9, opts.nupCount());

        opts.setPagesPerSheet(PrintOptions.PagesPerSheet.SIXTEEN);
        assertEquals(16, opts.nupCount());
    }

    @Test
    void testMarginCalculation() {
        PrintOptions opts = new PrintOptions();
        assertEquals(PrintOptions.MarginMode.NONE, opts.getMarginMode());
        assertEquals(0.0, opts.getEffectiveMarginPoints(), 0.001);

        opts.setMarginMode(PrintOptions.MarginMode.NORMAL);
        assertEquals(PrintOptions.MarginMode.NORMAL.getPoints(), opts.getEffectiveMarginPoints(), 0.001);

        opts.setMarginMode(PrintOptions.MarginMode.CUSTOM);
        opts.setCustomMarginMm(25.4); // 25.4 mm = 1 inch = 72 points
        assertEquals(72.0, opts.getEffectiveMarginPoints(), 0.001);
    }
}
