package org.originsd.oqteris.writer.io;

import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.originsd.oqteris.core.io.DocumentImporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.io.FormatDescriptor;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.writer.model.Paragraph;
import org.originsd.oqteris.writer.model.TextRun;
import org.originsd.oqteris.writer.model.WriterDocument;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Importer for legacy Microsoft Word 97-2003 binary documents (.doc).
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
                        // Extract ASCII/UTF-16 printable strings
                        extractedText = extractPrintableText(bytes);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (extractedText != null && !extractedText.isBlank()) {
            String[] lines = extractedText.split("\\r?\\n");
            for (String line : lines) {
                line = line.trim();
                Paragraph p = new Paragraph();
                if (!line.isEmpty()) {
                    p.addRun(new TextRun(line));
                }
                document.addElement(p);
            }
        }

        if (document.getElements().isEmpty()) {
            document.addElement(new Paragraph());
        }

        document.setModified(false);
        return document;
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
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
