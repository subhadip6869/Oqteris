package org.originsd.oqteris.writer;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.originsd.oqteris.writer.io.RtfExporter;
import org.originsd.oqteris.writer.io.RtfImporter;
import org.originsd.oqteris.writer.model.Paragraph;
import org.originsd.oqteris.writer.model.TextRun;
import org.originsd.oqteris.writer.model.WriterDocument;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class RtfRoundTripTest {

    @Test
    public void testRtfExportAndImport(@TempDir Path tempDir) throws IOException {
        WriterDocument doc = new WriterDocument("test.rtf");

        Paragraph p1 = new Paragraph();
        p1.setHeadingStyle(Paragraph.HeadingStyle.TITLE);
        p1.setAlignment(Paragraph.Alignment.CENTER);
        TextRun r1 = new TextRun("Oqteris RTF Document", "Segoe UI", 24.0, true, false, false, false, Color.rgb(37, 99, 235), null);
        p1.addRun(r1);
        doc.addElement(p1);

        Paragraph p2 = new Paragraph();
        TextRun r2 = new TextRun("This is bold and italic text.", "Segoe UI", 12.0, true, true, false, false, Color.BLACK, null);
        p2.addRun(r2);
        doc.addElement(p2);

        File rtfFile = tempDir.resolve("sample.rtf").toFile();

        RtfExporter exporter = new RtfExporter();
        assertTrue(exporter.canExport(rtfFile));
        exporter.exportDocument(doc, rtfFile);
        assertTrue(rtfFile.exists());
        assertTrue(rtfFile.length() > 0);

        RtfImporter importer = new RtfImporter();
        assertTrue(importer.canImport(rtfFile));
        WriterDocument imported = importer.importDocument(rtfFile);

        assertNotNull(imported);
        assertFalse(imported.getElements().isEmpty());
        String fullText = imported.getPlainText();
        assertTrue(fullText.contains("Oqteris RTF Document"), "Should contain title");
        assertTrue(fullText.contains("bold and italic text"), "Should contain paragraph text");
    }
}
