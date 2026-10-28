package org.originsd.oqteris.writer;

import org.junit.jupiter.api.Test;
import org.originsd.oqteris.writer.layout.LayoutEngine;
import org.originsd.oqteris.writer.layout.LayoutPage;
import org.originsd.oqteris.writer.model.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WordProcessorLayoutTest {

    @Test
    public void testLineBreaking() {
        WriterDocument doc = new WriterDocument("LayoutTest");
        doc.clearElements();

        Paragraph p = new Paragraph();
        p.addRun(new TextRun("The quick brown fox jumps over the lazy dog. Antigravity and Oqteris deliver independent document processing across all platforms without relying on external office suites."));
        doc.addElement(p);

        List<LayoutPage> pages = LayoutEngine.layout(doc);
        assertFalse(pages.isEmpty());
        LayoutPage page1 = pages.get(0);
        assertTrue(page1.getLines().size() > 1, "Text should wrap across multiple lines");
    }

    @Test
    public void testPaginationAcrossMultiplePages() {
        WriterDocument doc = new WriterDocument("PaginationTest");
        doc.clearElements();

        // Add 60 paragraphs to force multiple pages
        for (int i = 1; i <= 60; i++) {
            Paragraph p = new Paragraph("Paragraph " + i + ": Testing pagination boundary calculations and margin adherence in Oqteris layout engine.");
            p.setSpaceAfter(8.0);
            doc.addElement(p);
        }

        List<LayoutPage> pages = LayoutEngine.layout(doc);
        assertTrue(pages.size() >= 2, "60 paragraphs should paginate into at least 2 pages");

        for (int i = 0; i < pages.size(); i++) {
            LayoutPage page = pages.get(i);
            assertEquals(i, page.getPageIndex());
            assertFalse(page.getLines().isEmpty());
        }
    }

    @Test
    public void testHardPageBreak() {
        WriterDocument doc = new WriterDocument("BreakTest");
        doc.clearElements();

        doc.addElement(new Paragraph("Page 1 Content"));
        doc.addElement(new PageBreakElement());
        doc.addElement(new Paragraph("Page 2 Content"));

        List<LayoutPage> pages = LayoutEngine.layout(doc);
        assertEquals(2, pages.size(), "Hard page break should create exactly 2 pages");
    }

    @Test
    public void testTableLayout() {
        WriterDocument doc = new WriterDocument("TableTest");
        doc.clearElements();

        DocumentTable table = new DocumentTable(4, 3);
        doc.addElement(table);

        List<LayoutPage> pages = LayoutEngine.layout(doc);
        assertEquals(1, pages.size());
        LayoutPage page = pages.get(0);
        assertEquals(4, page.getTableBoxes().size(), "Table with 4 rows should produce 4 row boxes");
        assertEquals(3, page.getTableBoxes().get(0).cells.size(), "Each row should contain 3 cells");
        int totalCells = page.getTableBoxes().stream().mapToInt(b -> b.cells.size()).sum();
        assertEquals(12, totalCells, "Total table cells across rows should be 12");
    }

    @Test
    public void testCustomRightTabStop() {
        WriterDocument doc = new WriterDocument("TabStopTest");
        doc.clearElements();

        Paragraph p = new Paragraph();
        p.addRun(new TextRun("Memo:\tDate:"));
        // Set a Right Tab Stop at 450.0 pt
        p.addTabStop(new TabStop(450.0, TabStop.Alignment.RIGHT));
        doc.addElement(p);

        List<LayoutPage> pages = LayoutEngine.layout(doc);
        assertFalse(pages.isEmpty());
        LayoutPage page = pages.get(0);
        assertFalse(page.getLines().isEmpty());

        org.originsd.oqteris.writer.layout.LayoutLine line = page.getLines().get(0);
        assertTrue(line.getSegments().size() >= 3, "Line must have segments for 'Memo:', tab, and 'Date:'");

        // The last segment ('Date:') should end at or very close to 450.0 pt
        org.originsd.oqteris.writer.layout.LayoutLine.LineSegment lastSeg = line.getSegments().get(line.getSegments().size() - 1);
        assertEquals("Date:", lastSeg.getText());
        double segEndX = lastSeg.getX() + lastSeg.getWidth();
        assertEquals(450.0, segEndX, 1.5, "Right tab stop must align 'Date:' flush to 450.0 pt");
    }
}
