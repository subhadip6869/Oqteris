package org.originsd.oqteris.writer;

import org.junit.jupiter.api.Test;
import org.originsd.oqteris.writer.io.DocxExporter;
import org.originsd.oqteris.writer.io.DocxImporter;
import org.originsd.oqteris.writer.model.DocumentTable;
import org.originsd.oqteris.writer.model.Paragraph;
import org.originsd.oqteris.writer.model.TextRun;
import org.originsd.oqteris.writer.model.WriterDocument;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class DocxRoundTripTest {

    @Test
    public void testDocxExportAndImport() throws IOException {
        WriterDocument doc = new WriterDocument("DocxRoundTrip");
        doc.clearElements();

        // 1. Heading
        Paragraph title = new Paragraph("Oqteris Independent Office Suite");
        title.setHeadingStyle(Paragraph.HeadingStyle.TITLE);
        doc.addElement(title);

        // 2. Formatted Paragraph
        Paragraph p = new Paragraph();
        TextRun r1 = new TextRun("This is normal text. ");
        TextRun r2 = new TextRun("This is bold text. ");
        r2.setBold(true);
        TextRun r3 = new TextRun("This is italic text.");
        r3.setItalic(true);
        p.addRun(r1);
        p.addRun(r2);
        p.addRun(r3);
        doc.addElement(p);

        // 3. Table
        DocumentTable table = new DocumentTable(2, 2);
        doc.addElement(table);

        File tempDocx = File.createTempFile("oqteris_roundtrip_", ".docx");
        tempDocx.deleteOnExit();

        DocxExporter exporter = new DocxExporter();
        exporter.exportDocument(doc, tempDocx);

        assertTrue(tempDocx.exists());
        assertTrue(tempDocx.length() > 0);

        DocxImporter importer = new DocxImporter();
        WriterDocument reloaded = importer.importDocument(tempDocx);

        assertNotNull(reloaded);
        assertEquals(3, reloaded.getElements().size());
        assertTrue(reloaded.getElements().get(0) instanceof Paragraph);
        assertTrue(reloaded.getElements().get(1) instanceof Paragraph);
        assertTrue(reloaded.getElements().get(2) instanceof DocumentTable);

        Paragraph reloadedP = (Paragraph) reloaded.getElements().get(1);
        assertTrue(reloadedP.getPlainText().contains("bold text"));
    }
}
