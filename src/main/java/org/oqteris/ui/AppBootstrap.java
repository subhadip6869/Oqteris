package org.oqteris.ui;

import org.oqteris.calc.model.Workbook;
import org.oqteris.calc.ui.CalcDocumentView;
import org.oqteris.core.io.DocumentImporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.model.DocumentModel;
import org.oqteris.core.model.DocumentType;
import org.oqteris.pdf.ui.PdfDocumentView;
import org.oqteris.presentation.model.Presentation;
import org.oqteris.presentation.ui.PresentationDocumentView;
import org.oqteris.writer.model.WriterDocument;
import org.oqteris.writer.ui.WriterDocumentView;

import java.io.File;
import java.util.Optional;

/**
 * Bootstraps and registers document modules with the application shell.
 */
public class AppBootstrap {

    public static void initialize(MainWindow mainWindow) {
        // Register format adapters - PDF
        org.oqteris.pdf.io.PdfImporter.register();

        // Calc adapters: XLSX, XLS, CSV, ODS
        org.oqteris.calc.io.XlsxImporter.register();
        org.oqteris.calc.io.XlsxExporter.register();
        org.oqteris.calc.io.XlsImporter.register();
        org.oqteris.calc.io.XlsExporter.register();
        org.oqteris.calc.io.CsvImporter.register();
        org.oqteris.calc.io.CsvExporter.register();
        org.oqteris.calc.io.OdsImporter.register();

        // Writer adapters: DOCX, DOC, RTF, HTML, ODT, TXT
        org.oqteris.writer.io.DocxImporter.register();
        org.oqteris.writer.io.DocxExporter.register();
        org.oqteris.writer.io.DocImporter.register();
        org.oqteris.writer.io.RtfImporter.register();
        org.oqteris.writer.io.RtfExporter.register();
        org.oqteris.writer.io.OdtImporter.register();
        org.oqteris.writer.io.OdtExporter.register();
        org.oqteris.writer.io.HtmlImporter.register();
        org.oqteris.writer.io.HtmlExporter.register();
        org.oqteris.writer.io.TxtImporter.register();

        // Presentation adapters: PPTX, PPT, ODP, PDF export
        org.oqteris.presentation.io.PptxImporter.register();
        org.oqteris.presentation.io.PptxExporter.register();
        org.oqteris.presentation.io.PptImporter.register();
        org.oqteris.presentation.io.OdpImporter.register();

        // Document creation handler
        mainWindow.setDocumentFactory(type -> {
            return createNewView(type);
        });

        // File opening handler
        mainWindow.setFileOpenHandler(file -> {
            return openDocumentView(file);
        });
    }

    public static DocumentView createNewView(DocumentType type) {
        switch (type) {
            case WRITER:
                return createWriterView(null);
            case CALC:
                return createCalcView(null);
            case IMPRESS:
                return createImpressView(null);
            case PDF:
                return null; // PDF is a viewer/verifier for existing documents
            default:
                return null;
        }
    }

    public static DocumentView openDocumentView(File file) {
        Optional<DocumentType> typeOpt = DocumentRegistry.getInstance().detectDocumentType(file);
        if (typeOpt.isEmpty()) {
            return null;
        }

        DocumentType type = typeOpt.get();
        switch (type) {
            case PDF:
                return openPdfView(file);
            case CALC:
                return openCalcView(file);
            case WRITER:
                return openWriterView(file);
            case IMPRESS:
                return openImpressView(file);
            default:
                return null;
        }
    }

    // Dynamic hooks implemented in respective modules
    public static DocumentView createWriterView(DocumentModel existingDoc) {
        if (existingDoc instanceof WriterDocument wd) {
            return new WriterDocumentView(wd);
        }
        return new WriterDocumentView();
    }

    public static DocumentView openWriterView(File file) {
        try {
            Optional<DocumentImporter<DocumentModel>> imp = DocumentRegistry.getInstance().findImporter(file);
            if (imp.isPresent()) {
                DocumentModel doc = imp.get().importDocument(file);
                doc.setFile(file);
                doc.setModified(false);
                return createWriterView(doc);
            }
        } catch (Exception e) {
            System.err.println("[AppBootstrap] Error opening writer file: " + e.getMessage());
        }
        return null;
    }

    public static DocumentView createCalcView(DocumentModel existingDoc) {
        if (existingDoc instanceof Workbook wb) {
            return new CalcDocumentView(wb);
        }
        return new CalcDocumentView();
    }

    public static DocumentView openCalcView(File file) {
        try {
            Optional<DocumentImporter<DocumentModel>> imp = DocumentRegistry.getInstance().findImporter(file);
            if (imp.isPresent()) {
                DocumentModel doc = imp.get().importDocument(file);
                doc.setFile(file);
                doc.setModified(false);
                return createCalcView(doc);
            }
        } catch (Exception e) {
            System.err.println("[AppBootstrap] Error opening calc file: " + e.getMessage());
        }
        return null;
    }

    public static DocumentView createImpressView(DocumentModel existingDoc) {
        if (existingDoc instanceof Presentation p) {
            return new PresentationDocumentView(p);
        }
        return new PresentationDocumentView();
    }

    public static DocumentView openImpressView(File file) {
        try {
            Optional<DocumentImporter<DocumentModel>> imp = DocumentRegistry.getInstance().findImporter(file);
            if (imp.isPresent()) {
                DocumentModel doc = imp.get().importDocument(file);
                doc.setFile(file);
                doc.setModified(false);
                return createImpressView(doc);
            }
        } catch (Exception e) {
            System.err.println("[AppBootstrap] Error opening presentation file: " + e.getMessage());
        }
        return null;
    }

    public static DocumentView openPdfView(File file) {
        try {
            return new PdfDocumentView(file);
        } catch (Exception e) {
            System.err.println("[AppBootstrap] PDF module error: " + e.getMessage());
            return null;
        }
    }
}
