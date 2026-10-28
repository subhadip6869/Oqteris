package org.oqteris.ui;

import org.oqteris.core.io.DocumentImporter;
import org.oqteris.core.io.DocumentRegistry;
import org.oqteris.core.model.DocumentModel;
import org.oqteris.core.model.DocumentType;

import java.io.File;
import java.util.Optional;

/**
 * Bootstraps and registers document modules with the application shell.
 */
public class AppBootstrap {

    public static void initialize(MainWindow mainWindow) {
        // Register format adapters
        org.oqteris.pdf.io.PdfImporter.register();
        org.oqteris.calc.io.XlsxImporter.register();
        org.oqteris.calc.io.XlsxExporter.register();
        org.oqteris.calc.io.CsvImporter.register();
        org.oqteris.calc.io.CsvExporter.register();
        org.oqteris.writer.io.DocxImporter.register();
        org.oqteris.writer.io.DocxExporter.register();
        org.oqteris.writer.io.TxtImporter.register();
        org.oqteris.writer.io.HtmlExporter.register();
        org.oqteris.presentation.io.PptxImporter.register();
        org.oqteris.presentation.io.PptxExporter.register();

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
        try {
            Class<?> clazz = Class.forName("org.oqteris.writer.ui.WriterDocumentView");
            if (existingDoc != null) {
                return (DocumentView) clazz.getConstructor(Class.forName("org.oqteris.writer.model.WriterDocument")).newInstance(existingDoc);
            } else {
                return (DocumentView) clazz.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            System.err.println("[AppBootstrap] Writer module not yet initialized: " + e.getMessage());
            return null;
        }
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
        try {
            Class<?> clazz = Class.forName("org.oqteris.calc.ui.CalcDocumentView");
            if (existingDoc != null) {
                return (DocumentView) clazz.getConstructor(Class.forName("org.oqteris.calc.model.Workbook")).newInstance(existingDoc);
            } else {
                return (DocumentView) clazz.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            System.err.println("[AppBootstrap] Calc module not yet initialized: " + e.getMessage());
            return null;
        }
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
        try {
            Class<?> clazz = Class.forName("org.oqteris.presentation.ui.PresentationDocumentView");
            if (existingDoc != null) {
                return (DocumentView) clazz.getConstructor(Class.forName("org.oqteris.presentation.model.Presentation")).newInstance(existingDoc);
            } else {
                return (DocumentView) clazz.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            System.err.println("[AppBootstrap] Presentation module not yet initialized: " + e.getMessage());
            return null;
        }
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
            Class<?> clazz = Class.forName("org.oqteris.pdf.ui.PdfDocumentView");
            return (DocumentView) clazz.getConstructor(File.class).newInstance(file);
        } catch (Exception e) {
            System.err.println("[AppBootstrap] PDF module not yet initialized: " + e.getMessage());
            return null;
        }
    }
}
