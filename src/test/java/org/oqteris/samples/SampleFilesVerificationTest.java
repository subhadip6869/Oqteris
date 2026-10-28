package org.oqteris.samples;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.oqteris.calc.io.XlsxImporter;
import org.oqteris.calc.model.Cell;
import org.oqteris.calc.model.Workbook;
import org.oqteris.calc.model.Worksheet;
import org.oqteris.crypto.signature.PdfSignatureInfo;
import org.oqteris.crypto.signature.PdfSignatureVerifier;
import org.oqteris.crypto.signature.SignatureVerificationStatus;
import org.oqteris.crypto.trust.TrustLevel;
import org.oqteris.crypto.trust.TrustStoreManager;
import org.oqteris.presentation.io.PptxImporter;
import org.oqteris.presentation.model.Presentation;
import org.oqteris.writer.io.DocxImporter;
import org.oqteris.writer.layout.LayoutEngine;
import org.oqteris.writer.layout.LayoutPage;
import org.oqteris.writer.model.WriterDocument;

import java.io.File;
import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated acceptance test verifying the generated sample documents
 * can be loaded, verified, laid out, and calculated by Oqteris engines.
 */
public class SampleFilesVerificationTest {

    @Test
    public void testVerifySampleDocx() throws Exception {
        File docxFile = new File("samples", "sample_document.docx");
        assertTrue(docxFile.exists(), "sample_document.docx must exist");

        DocxImporter importer = new DocxImporter();
        WriterDocument doc = importer.importDocument(docxFile);

        assertNotNull(doc);
        assertFalse(doc.getElements().isEmpty());

        List<LayoutPage> pages = LayoutEngine.layout(doc);
        assertFalse(pages.isEmpty(), "Document must paginate into at least 1 page");
        assertTrue(pages.get(0).getLines().size() > 5, "First page must contain laid out text lines");
    }

    @Test
    public void testVerifySampleXlsx() throws Exception {
        File xlsxFile = new File("samples", "sample_sheet.xlsx");
        assertTrue(xlsxFile.exists(), "sample_sheet.xlsx must exist");

        XlsxImporter importer = new XlsxImporter();
        Workbook wb = importer.importDocument(xlsxFile);

        assertNotNull(wb);
        assertTrue(wb.getSheetCount() >= 1);

        Worksheet sheet = wb.getSheet(0);
        assertNotNull(sheet);

        // Header check
        Cell deptHeader = sheet.getCell(2, 0);
        assertNotNull(deptHeader);
        assertEquals("Department", deptHeader.getRawValue());
        assertNotNull(deptHeader.getStyle().getFillColor(), "Header cell must retain imported background fill color");
        assertTrue(deptHeader.getStyle().isBold(), "Header cell must retain bold font style");

        // Formula check
        Cell engineeringVariance = sheet.getCell(3, 3);
        assertNotNull(engineeringVariance);
        assertTrue(engineeringVariance.getFormula() != null && engineeringVariance.getFormula().contains("-"));
    }

    @Test
    public void testVerifySamplePptx() throws Exception {
        File pptxFile = new File("samples", "sample_presentation.pptx");
        assertTrue(pptxFile.exists(), "sample_presentation.pptx must exist");

        PptxImporter importer = new PptxImporter();
        Presentation pres = importer.importDocument(pptxFile);

        assertNotNull(pres);
        assertEquals(3, pres.getSlideCount(), "Sample presentation must have 3 slides");
        assertFalse(pres.getSlide(0).getShapes().isEmpty(), "Title slide must have shapes");
    }

    @Test
    public void testVerifySampleSignedPdfAndTrustVerification() throws Exception {
        File pdfFile = new File("samples", "sample_signed.pdf");
        File certFile = new File("samples", "oqteris_demo_signer.cer");
        assertTrue(pdfFile.exists(), "sample_signed.pdf must exist");
        assertTrue(certFile.exists(), "oqteris_demo_signer.cer must exist");

        // 1. Verify initially: Mathematical validity = true, Trust = UNTRUSTED (since not yet imported in store)
        try (PDDocument readDoc = Loader.loadPDF(pdfFile)) {
            List<PdfSignatureInfo> signatures = PdfSignatureVerifier.verifySignatures(pdfFile, readDoc);

            assertEquals(1, signatures.size(), "Should have exactly 1 signature");
            PdfSignatureInfo sig = signatures.get(0);
            assertTrue(sig.isCryptographicallyValid(), () -> "Mathematical signature MUST be valid. Reason: " + sig.getStatusDetails() + " Status: " + sig.getStatus());
            assertTrue(sig.coversWholeDocument(), "Document MUST not be modified after signing");
            assertEquals(SignatureVerificationStatus.VALID_BUT_UNTRUSTED, sig.getStatus(),
                    "Certificate must be UNTRUSTED before being added to trust store");

            // 2. Import certificate into TrustStoreManager
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert;
            try (FileInputStream in = new FileInputStream(certFile)) {
                cert = (X509Certificate) cf.generateCertificate(in);
            }
            String alias = "sample_demo_signer";
            TrustStoreManager.getInstance().importCertificate(alias, cert, TrustLevel.TRUSTED, "Demo trusted certificate");

            // 3. Re-verify: Now that the cert is explicitly trusted, signature is VALID_AND_TRUSTED
            List<PdfSignatureInfo> rechecked = PdfSignatureVerifier.verifySignatures(pdfFile, readDoc);
            assertEquals(1, rechecked.size());
            PdfSignatureInfo trustedSig = rechecked.get(0);

            assertTrue(trustedSig.isCryptographicallyValid());
            assertEquals(SignatureVerificationStatus.VALID_AND_TRUSTED, trustedSig.getStatus(),
                    "Signature MUST be VALID_AND_TRUSTED after root certificate is added to trust store");

            TrustStoreManager.getInstance().removeCertificate(alias);
        }
    }
}
