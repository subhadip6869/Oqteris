package org.originsd.oqteris.writer;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.originsd.oqteris.writer.io.*;
import org.originsd.oqteris.writer.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class OdtAndHtmlRoundTripTest {

    @Test
    public void testOdtExportAndImport(@TempDir Path tempDir) throws IOException {
        WriterDocument doc = new WriterDocument("sample.odt");

        Paragraph h1 = new Paragraph();
        h1.setHeadingStyle(Paragraph.HeadingStyle.HEADING_1);
        h1.addRun(new TextRun("OpenDocument Standard Test", "Arial", 16.0, true, false, false, false, Color.rgb(20, 50, 100), null));
        doc.addElement(h1);

        Paragraph p = new Paragraph();
        p.addRun(new TextRun("Testing ODT format preservation in Oqteris."));
        doc.addElement(p);

        // Add a PageBreak
        doc.addElement(new PageBreakElement());

        // Add a styled Table with cell shading
        DocumentTable table = new DocumentTable(2, 2);
        table.setAlignment(DocumentTable.Alignment.CENTER);
        table.getCell(0, 0).setBackgroundColor(Color.rgb(220, 230, 245));
        table.getCell(0, 0).setVerticalAlignment(DocumentTableCell.VAlign.CENTER);
        table.getCell(0, 0).getParagraphs().get(0).addRun(new TextRun("A1 Header", "Arial", 11.0, true, false, false, false, Color.BLACK, null));
        table.getCell(0, 1).getParagraphs().get(0).addRun(new TextRun("B1 Header"));
        table.getCell(1, 0).getParagraphs().get(0).addRun(new TextRun("Data A2"));
        table.getCell(1, 1).getParagraphs().get(0).addRun(new TextRun("Data B2"));
        doc.addElement(table);

        File odtFile = tempDir.resolve("sample.odt").toFile();

        OdtExporter exporter = new OdtExporter();
        assertTrue(exporter.canExport(odtFile));
        exporter.exportDocument(doc, odtFile);
        assertTrue(odtFile.exists());
        assertTrue(odtFile.length() > 0);

        OdtImporter importer = new OdtImporter();
        assertTrue(importer.canImport(odtFile));
        WriterDocument imported = importer.importDocument(odtFile);

        assertNotNull(imported);
        assertTrue(imported.getPlainText().contains("OpenDocument Standard Test"));
        assertTrue(imported.getPlainText().contains("Testing ODT format preservation"));
        assertTrue(imported.getPlainText().contains("A1 Header"));

        // Verify PageBreakElement preserved
        boolean foundBreak = imported.getElements().stream().anyMatch(el -> el instanceof PageBreakElement);
        assertTrue(foundBreak, "ODT roundtrip should preserve PageBreakElement");

        // Verify Table preserved
        boolean foundTable = imported.getElements().stream().anyMatch(el -> el instanceof DocumentTable);
        assertTrue(foundTable, "ODT roundtrip should preserve DocumentTable");

        DocumentTable importedTable = (DocumentTable) imported.getElements().stream()
                .filter(el -> el instanceof DocumentTable)
                .findFirst().orElseThrow();
        assertEquals(2, importedTable.getRows().size());
        assertNotNull(importedTable.getCell(0, 0).getBackgroundColor(), "Cell background color should be preserved");
    }

    @Test
    public void testHtmlExportAndImport(@TempDir Path tempDir) throws IOException {
        WriterDocument doc = new WriterDocument("web.html");

        Paragraph h = new Paragraph();
        h.setHeadingStyle(Paragraph.HeadingStyle.TITLE);
        h.addRun(new TextRun("HTML Exported Document"));
        doc.addElement(h);

        Paragraph p = new Paragraph();
        p.addRun(new TextRun("Hello world from HTML!"));
        doc.addElement(p);

        // Add PageBreak
        doc.addElement(new PageBreakElement());

        // Add Table
        DocumentTable table = new DocumentTable(2, 2);
        table.setAlignment(DocumentTable.Alignment.CENTER);
        table.getCell(0, 0).setBackgroundColor(Color.rgb(240, 240, 240));
        table.getCell(0, 0).getParagraphs().get(0).addRun(new TextRun("HTML Cell A1"));
        doc.addElement(table);

        File htmlFile = tempDir.resolve("web.html").toFile();

        HtmlExporter exporter = new HtmlExporter();
        assertTrue(exporter.canExport(htmlFile));
        exporter.exportDocument(doc, htmlFile);
        assertTrue(htmlFile.exists());

        HtmlImporter importer = new HtmlImporter();
        assertTrue(importer.canImport(htmlFile));
        WriterDocument imported = importer.importDocument(htmlFile);

        assertNotNull(imported);
        assertTrue(imported.getPlainText().contains("HTML Exported Document"));
        assertTrue(imported.getPlainText().contains("Hello world from HTML!"));

        // Verify PageBreakElement preserved
        boolean foundBreak = imported.getElements().stream().anyMatch(el -> el instanceof PageBreakElement);
        assertTrue(foundBreak, "HTML roundtrip should preserve PageBreakElement");

        // Verify Table preserved
        boolean foundTable = imported.getElements().stream().anyMatch(el -> el instanceof DocumentTable);
        assertTrue(foundTable, "HTML roundtrip should preserve DocumentTable");
    }

    @Test
    public void testTxtExportAndImportWithPageBreaks(@TempDir Path tempDir) throws IOException {
        WriterDocument doc = new WriterDocument("sample.txt");

        Paragraph p1 = new Paragraph("Page One Line One");
        doc.addElement(p1);

        doc.addElement(new PageBreakElement());

        Paragraph p2 = new Paragraph("Page Two Line One");
        doc.addElement(p2);

        File txtFile = tempDir.resolve("sample.txt").toFile();

        TxtImporter adapter = new TxtImporter();
        assertTrue(adapter.canExport(txtFile));
        adapter.exportDocument(doc, txtFile);
        assertTrue(txtFile.exists());

        assertTrue(adapter.canImport(txtFile));
        WriterDocument imported = adapter.importDocument(txtFile);

        assertNotNull(imported);
        assertTrue(imported.getPlainText().contains("Page One Line One"));
        assertTrue(imported.getPlainText().contains("Page Two Line One"));

        boolean foundBreak = imported.getElements().stream().anyMatch(el -> el instanceof PageBreakElement);
        assertTrue(foundBreak, "TXT roundtrip should preserve PageBreakElement across form feed");
    }
}
