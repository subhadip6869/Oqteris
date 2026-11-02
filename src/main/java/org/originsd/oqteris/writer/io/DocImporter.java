package org.originsd.oqteris.writer.io;

import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.originsd.oqteris.core.io.DocumentImporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.io.FormatDescriptor;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.writer.model.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Importer for legacy Microsoft Word 97-2003 binary documents (.doc).
 * Supports:
 * - Default document font and size detection (Times New Roman, 12pt)
 * - Page breaks via ASCII Form Feed (\u000c / \f)
 * - Paragraph and tab-delimited table detection
 */
public class DocImporter implements DocumentImporter<WriterDocument> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "DOC",
            "Word 97-2003 Document (*.doc)",
            DocumentType.WRITER,
            List.of("doc"),
            "application/msword",
            true,
            false
    );

    static {
        DocumentRegistry.getInstance().registerImporter(new DocImporter());
    }

    public static void register() {
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canImport(File file) {
        return file != null && file.exists() && file.getName().toLowerCase().endsWith(".doc");
    }

    @Override
    public WriterDocument importDocument(File file) throws IOException {
        WriterDocument document = new WriterDocument(file.getName());
        document.setFile(file);
        document.clearElements();

        String defaultFont = "Times New Roman";
        double defaultSize = 12.0;
        document.setDefaultFontFamily(defaultFont);
        document.setDefaultFontSize(defaultSize);

        String extractedText = null;

        // 1. Try Apache POI Extractor
        try (POITextExtractor extractor = ExtractorFactory.createExtractor(file)) {
            extractedText = extractor.getText();
        } catch (Exception ex) {
            // 2. Direct POIFS fallback
            try (FileInputStream fis = new FileInputStream(file);
                 POIFSFileSystem poifs = new POIFSFileSystem(fis)) {
                if (poifs.getRoot().hasEntry("WordDocument")) {
                    try (DocumentInputStream dis = poifs.createDocumentInputStream("WordDocument")) {
                        byte[] bytes = dis.readAllBytes();
                        extractedText = extractPrintableText(bytes);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (extractedText != null && !extractedText.isBlank()) {
            // Split pages by form feed character (\u000c)
            String[] pages = extractedText.split("\u000c");
            for (int pageIdx = 0; pageIdx < pages.length; pageIdx++) {
                if (pageIdx > 0) {
                    document.addElement(new PageBreakElement());
                }

                String pageText = pages[pageIdx];
                String[] lines = pageText.split("\\r?\\n");
                List<String> tableLines = new ArrayList<>();

                for (String rawLine : lines) {
                    String line = rawLine.trim();

                    // Check if line is part of a tab-delimited table
                    if (rawLine.contains("\t") && rawLine.split("\t").length >= 2) {
                        tableLines.add(rawLine);
                        continue;
                    } else if (!tableLines.isEmpty()) {
                        document.addElement(createTableFromLines(tableLines, defaultFont, defaultSize));
                        tableLines.clear();
                    }

                    Paragraph p = new Paragraph();
                    if (!line.isEmpty()) {
                        p.addRun(new TextRun(line, defaultFont, defaultSize));
                    }
                    document.addElement(p);
                }

                if (!tableLines.isEmpty()) {
                    document.addElement(createTableFromLines(tableLines, defaultFont, defaultSize));
                    tableLines.clear();
                }
            }
        }

        if (document.getElements().isEmpty()) {
            document.addElement(new Paragraph());
        }

        document.setModified(false);
        return document;
    }

    private DocumentTable createTableFromLines(List<String> lines, String font, double size) {
        DocumentTable table = new DocumentTable();
        table.getRows().clear();

        for (String line : lines) {
            DocumentTableRow row = new DocumentTableRow();
            String[] cols = line.split("\t");
            for (String col : cols) {
                DocumentTableCell cell = new DocumentTableCell();
                cell.getParagraphs().clear();
                Paragraph p = new Paragraph();
                p.addRun(new TextRun(col.trim(), font, size));
                cell.addParagraph(p);
                row.addCell(cell);
            }
            table.addRow(row);
        }
        return table;
    }

    private String extractPrintableText(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int len = data.length;
        for (int i = 0; i < len; i++) {
            byte b = data[i];
            if (b >= 32 && b <= 126) {
                sb.append((char) b);
            } else if (b == '\n' || b == '\r') {
                sb.append('\n');
            } else if (b == '\t') {
                sb.append('\t');
            } else if (b == '\u000c') {
                sb.append('\u000c');
            }
        }
        return sb.toString();
    }
}
