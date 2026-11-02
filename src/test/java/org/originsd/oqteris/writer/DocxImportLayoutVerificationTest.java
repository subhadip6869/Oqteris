package org.originsd.oqteris.writer;

import org.junit.jupiter.api.Test;
import org.originsd.oqteris.writer.io.DocxImporter;
import org.originsd.oqteris.writer.layout.LayoutEngine;
import org.originsd.oqteris.writer.layout.LayoutPage;
import org.originsd.oqteris.writer.model.*;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DocxImportLayoutVerificationTest {

    @Test
    void testUploadedDocxFullFeatures() throws Exception {
        File file = new File("C:/Users/subha/.gemini/antigravity/brain/c505d0fc-3ce5-49d8-a021-aa27831663fe/.user_uploaded/media_1793628133835_d40e20a6.docx");
        if (!file.exists()) {
            file = new File("samples/sample_document.docx");
        }
        assertTrue(file.exists());

        DocxImporter importer = new DocxImporter();
        WriterDocument doc = importer.importDocument(file);

        assertNotNull(doc);
        assertFalse(doc.getElements().isEmpty());

        int pageBreakCount = 0;
        int tableCount = 0;
        int coloredCellCount = 0;

        for (DocumentElement el : doc.getElements()) {
            if (el instanceof PageBreakElement) {
                pageBreakCount++;
            } else if (el instanceof DocumentTable table) {
                tableCount++;
                assertNotNull(table.getBorderColor(), "Table border color should not be null");
                for (DocumentTableRow r : table.getRows()) {
                    for (DocumentTableCell c : r.getCells()) {
                        if (c.getBackgroundColor() != null) {
                            coloredCellCount++;
                        }
                    }
                }
            }
        }

        System.out.println("Imported PageBreaks: " + pageBreakCount);
        System.out.println("Imported Tables: " + tableCount);
        System.out.println("Colored Cells: " + coloredCellCount);

        assertTrue(pageBreakCount > 0, "Docx should contain imported page breaks");
        assertTrue(coloredCellCount > 0, "Docx should contain colored table cells");

        List<LayoutPage> pages = LayoutEngine.layout(doc);
        assertNotNull(pages);
        assertFalse(pages.isEmpty());
        System.out.println("Layout Pages: " + pages.size());
    }

    @Test
    void testTableManipulationMethods() {
        DocumentTable table = new DocumentTable(2, 2);
        assertEquals(2, table.getRows().size());
        assertEquals(2, table.getColumnCount());

        table.insertRow(1);
        assertEquals(3, table.getRows().size());

        table.removeRow(1);
        assertEquals(2, table.getRows().size());

        table.insertColumn(1);
        assertEquals(3, table.getColumnCount());
        assertEquals(3, table.getRows().get(0).getCells().size());

        table.removeColumn(1);
        assertEquals(2, table.getColumnCount());
        assertEquals(2, table.getRows().get(0).getCells().size());
    }
}
