package org.originsd.oqteris.writer.io;

import org.originsd.oqteris.core.io.DocumentExporter;
import org.originsd.oqteris.core.io.DocumentImporter;
import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.io.FormatDescriptor;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.writer.model.DocumentElement;
import org.originsd.oqteris.writer.model.Paragraph;
import org.originsd.oqteris.writer.model.WriterDocument;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Importer and Exporter for plain text files (.txt).
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

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                doc.addElement(new Paragraph(line));
            }
        }

        if (doc.getElements().isEmpty()) {
            doc.addElement(new Paragraph());
        }

        doc.setModified(false);
        return doc;
    }

    @Override
    public boolean canExport(File file) {
        return file != null && file.getName().toLowerCase().endsWith(".txt");
    }

    @Override
    public void exportDocument(WriterDocument document, File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (DocumentElement el : document.getElements()) {
                if (el instanceof Paragraph p) {
                    writer.write(p.getPlainText());
                    writer.newLine();
                }
            }
        }
    }
}
