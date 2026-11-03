package org.originsd.oqteris.ui;

import org.originsd.oqteris.core.io.DocumentRegistry;
import org.originsd.oqteris.core.model.DocumentType;
import org.originsd.oqteris.pdf.ui.PdfDocumentView;

import java.io.File;
import java.util.Optional;

/**
 * Bootstraps and registers the PDF module with the application shell.
 */
public class AppBootstrap {

    public static void initialize(MainWindow mainWindow) {
        // Register format adapter - PDF
        org.originsd.oqteris.pdf.io.PdfImporter.register();

        // Document creation handler (PDF is opened from existing files)
        mainWindow.setDocumentFactory(type -> createNewView(type));

        // File opening handler
        mainWindow.setFileOpenHandler(file -> openDocumentView(file));
    }

    public static DocumentView createNewView(DocumentType type) {
        // PDF is a reader and editor for existing documents
        return null;
    }

    public static DocumentView openDocumentView(File file) {
        if (file == null || !file.exists()) {
            return null;
        }

        Optional<DocumentType> typeOpt = DocumentRegistry.getInstance().detectDocumentType(file);
        if (typeOpt.isEmpty() || typeOpt.get() != DocumentType.PDF) {
            // Also check file extension or magic bytes directly
            if (!file.getName().toLowerCase().endsWith(".pdf")) {
                return null;
            }
        }

        return openPdfView(file);
    }

    public static DocumentView openPdfView(File file) {
        String password = null;
        boolean isRetry = false;

        while (true) {
            try {
                return (password != null) ? new PdfDocumentView(file, password) : new PdfDocumentView(file);
            } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
                final boolean retry = isRetry;
                java.util.concurrent.CompletableFuture<String> pwdFuture = new java.util.concurrent.CompletableFuture<>();

                if (javafx.application.Platform.isFxApplicationThread()) {
                    String pwd = org.originsd.oqteris.pdf.ui.PasswordPromptDialog.promptForPassword(file.getName(), retry);
                    pwdFuture.complete(pwd);
                } else {
                    javafx.application.Platform.runLater(() -> {
                        String pwd = org.originsd.oqteris.pdf.ui.PasswordPromptDialog.promptForPassword(file.getName(), retry);
                        pwdFuture.complete(pwd);
                    });
                }

                try {
                    password = pwdFuture.get();
                } catch (Exception ex) {
                    return null;
                }

                if (password == null) {
                    // User cancelled password prompt
                    throw new UserCancelledException("Opening cancelled by user");
                }
                isRetry = true;
            } catch (Exception e) {
                if (e instanceof UserCancelledException uce) {
                    throw uce;
                }
                System.err.println("[AppBootstrap] PDF module error: " + e.getMessage());
                return null;
            }
        }
    }
}
