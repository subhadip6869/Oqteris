package org.originsd.oqteris.writer.io;

import org.originsd.oqteris.core.io.DocumentExporter;
import org.originsd.oqteris.core.io.DocumentImporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.io.FormatDescriptor;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.writer.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Importer and Exporter for plain text files (.txt).
 * Supports:
 * - Page breaks via Form Feed (\u000c / \f)
 * - Tab-delimited table detection
 * - Monospace or system default font
 */
public class TxtImporter implements DocumentImporter<WriterDocument>, DocumentExporter<WriterDocument> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "TXT",
            "Plain Text Document (*.txt)",
            DocumentType.WRITER,
            List.of("txt"),
            "text/plain",
            true,
            true
    );

    static {
        TxtImporter adapter = new TxtImporter();
        DocumentRegistry.getInstance().registerImporter(adapter);
        DocumentRegistry.getInstance().registerExporter(adapter);
    }

    public static void register() {
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canImport(File file) {
        return file != null && file.getName().toLowerCase().endsWith(".txt");
    }

    @Override
    public WriterDocument importDocument(File file) throws IOException {
        WriterDocument doc = new WriterDocument(file.getName());
        doc.setFile(file);
        doc.clearElements();

        String defaultFont = "Consolas";
        double defaultSize = 10.5;
        doc.setDefaultFontFamily(defaultFont);
        doc.setDefaultFontSize(defaultSize);

        String fullText = Files.readString(file.toPath(), StandardCharsets.UTF_8);

        // Split on form feed (\u000c / \f) for page breaks
        String[] pages = fullText.split("[\u000c\\f]");
        for (int pIdx = 0; pIdx < pages.length; pIdx++) {
            if (pIdx > 0) {
                doc.addElement(new PageBreakElement());
            }

            String pageContent = pages[pIdx];
            String[] lines = pageContent.split("\\r?\\n");
            List<String> tableLines = new ArrayList<>();

            for (String rawLine : lines) {
                if (rawLine.contains("\t") && rawLine.split("\t").length >= 2) {
                    tableLines.add(rawLine);
                    continue;
                } else if (!tableLines.isEmpty()) {
                    doc.addElement(createTableFromLines(tableLines, defaultFont, defaultSize));
                    tableLines.clear();
                }

                Paragraph p = new Paragraph();
                if (!rawLine.isEmpty()) {
                    p.addRun(new TextRun(rawLine, defaultFont, defaultSize));
                }
                doc.addElement(p);
            }

            if (!tableLines.isEmpty()) {
                doc.addElement(createTableFromLines(tableLines, defaultFont, defaultSize));
                tableLines.clear();
            }
        }

        if (doc.getElements().isEmpty()) {
            doc.addElement(new Paragraph());
        }

        doc.setModified(false);
        return doc;
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

    @Override
    public boolean canExport(File file) {
        return file != null && file.getName().toLowerCase().endsWith(".txt");
    }

    @Override
    public void exportDocument(WriterDocument document, File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (DocumentElement el : document.getElements()) {
                if (el instanceof PageBreakElement) {
                    writer.write("\u000c");
                    writer.newLine();
                } else if (el instanceof Paragraph p) {
                    writer.write(p.getPlainText());
                    writer.newLine();
                } else if (el instanceof DocumentTable table) {
                    for (DocumentTableRow row : table.getRows()) {
                        StringBuilder rowStr = new StringBuilder();
                        for (int c = 0; c < row.getCells().size(); c++) {
                            if (c > 0) rowStr.append("\t");
                            rowStr.append(row.getCells().get(c).getPlainText());
                        }
                        writer.write(rowStr.toString());
                        writer.newLine();
                    }
                }
            }
        }
    }
}
