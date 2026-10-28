package org.originsd.oqteris.writer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.originsd.oqteris.writer.io.*;
import org.originsd.oqteris.writer.model.DocumentTable;
import org.originsd.oqteris.writer.model.Paragraph;
import org.originsd.oqteris.writer.model.TextRun;
import org.originsd.oqteris.writer.model.WriterDocument;

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
        h1.addRun(new TextRun("OpenDocument Standard Test"));
        doc.addElement(h1);

        Paragraph p = new Paragraph();
        p.addRun(new TextRun("Testing ODT format preservation in Oqteris."));
        doc.addElement(p);

        DocumentTable table = new DocumentTable(2, 2);
        table.getCell(0, 0).getParagraphs().get(0).addRun(new TextRun("A1"));
        table.getCell(0, 1).getParagraphs().get(0).addRun(new TextRun("B1"));
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
    }
}
