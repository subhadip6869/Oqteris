package org.oqteris.pdf;

import javafx.scene.image.Image;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.oqteris.pdf.model.PdfDocumentModel;
import org.oqteris.pdf.render.PdfPageRenderer;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class PasswordProtectedPdfTest {

    @TempDir
    File tempDir;

    @Test
    public void testPasswordProtectedPdfFlow() throws IOException {
        File protectedPdf = new File(tempDir, "protected_sample.pdf");
        String userPassword = "UserPass123!";
        String ownerPassword = "OwnerSecret999#";

        // 1. Create an encrypted PDF
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                cs.newLineAtOffset(50, 700);
                cs.showText("Confidential Encrypted PDF Document");
                cs.endText();
            }

            AccessPermission permissions = new AccessPermission();
            permissions.setCanPrint(true);
            permissions.setCanExtractContent(false);

            StandardProtectionPolicy policy = new StandardProtectionPolicy(ownerPassword, userPassword, permissions);
            policy.setEncryptionKeyLength(128);
            policy.setPermissions(permissions);

            doc.protect(policy);
            doc.save(protectedPdf);
        }

        assertTrue(protectedPdf.exists() && protectedPdf.length() > 0);

        // 2. Opening without password must throw InvalidPasswordException
        assertThrows(InvalidPasswordException.class, () -> {
            new PdfDocumentModel(protectedPdf);
        }, "Opening without password must fail with InvalidPasswordException");

        // 3. Opening with wrong password must throw InvalidPasswordException
        assertThrows(InvalidPasswordException.class, () -> {
            new PdfDocumentModel(protectedPdf, "IncorrectPass");
        }, "Opening with incorrect password must fail with InvalidPasswordException");

        // 4. Opening with correct user password must succeed
        try (PdfDocumentModel userModel = new PdfDocumentModel(protectedPdf, userPassword)) {
            assertTrue(userModel.isEncrypted(), "Document must be recognized as encrypted");
            assertEquals(1, userModel.getPageCount(), "Encrypted document should have 1 page");
            assertNotNull(userModel.getPage(0), "Page 0 must be accessible");

            // Verify rendering decrypted page
            PdfPageRenderer renderer = new PdfPageRenderer(userModel.getPdDocument());
            Image rendered = renderer.renderPage(0, 1.0);
            assertNotNull(rendered, "Page 0 must render to an image");
            assertTrue(rendered.getWidth() > 100);
        }

        // 5. Opening with owner password must also succeed
        try (PdfDocumentModel ownerModel = new PdfDocumentModel(protectedPdf, ownerPassword)) {
            assertTrue(ownerModel.isEncrypted());
            assertEquals(1, ownerModel.getPageCount());
        }
    }
}
