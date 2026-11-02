package org.originsd.oqteris.writer;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.originsd.oqteris.writer.io.RtfExporter;
import org.originsd.oqteris.writer.io.RtfImporter;
import org.originsd.oqteris.writer.model.*;

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

        // PageBreak
        doc.addElement(new PageBreakElement());

        // Styled Table
        DocumentTable table = new DocumentTable(2, 2);
        table.setAlignment(DocumentTable.Alignment.CENTER);
        table.getCell(0, 0).setBackgroundColor(Color.rgb(220, 240, 255));
        table.getCell(0, 0).setVerticalAlignment(DocumentTableCell.VAlign.CENTER);
        table.getCell(0, 0).getParagraphs().get(0).addRun(new TextRun("Cell RTF 1"));
        table.getCell(0, 1).getParagraphs().get(0).addRun(new TextRun("Cell RTF 2"));
        doc.addElement(table);

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
        assertTrue(fullText.contains("Cell RTF 1"), "Should contain table cell text");

        boolean foundBreak = imported.getElements().stream().anyMatch(el -> el instanceof PageBreakElement);
        assertTrue(foundBreak, "RTF roundtrip should preserve PageBreakElement");

        boolean foundTable = imported.getElements().stream().anyMatch(el -> el instanceof DocumentTable);
        assertTrue(foundTable, "RTF roundtrip should preserve DocumentTable");
    }
}
