package org.oqteris.pdf.io;

import org.oqteris.core.io.DocumentImporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.io.FormatDescriptor;
import org.oqteris.core.model.DocumentType;
import org.oqteris.pdf.model.PdfDocumentModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Importer adapter for reading and inspecting PDF documents.
 */
public class PdfImporter implements DocumentImporter<PdfDocumentModel> {
    private static final FormatDescriptor DESCRIPTOR = new FormatDescriptor(
            "PDF",
            "PDF Document (*.pdf)",
            DocumentType.PDF,
            List.of("pdf"),
            "application/pdf",
            true,
            false
    );

    static {
        DocumentRegistry.getInstance().registerImporter(new PdfImporter());
    }

    public static void register() {
        // Trigger class loading
    }

    @Override
    public FormatDescriptor getFormatDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean canImport(File file) {
        if (file == null || !file.exists()) return false;
        if (file.getName().toLowerCase().endsWith(".pdf")) return true;

        // Check magic bytes %PDF-
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] header = new byte[5];
            int read = in.read(header);
            if (read == 5 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F' && header[4] == '-') {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public PdfDocumentModel importDocument(File file) throws IOException {
        return new PdfDocumentModel(file);
    }
}
