package org.originsd.oqteris.samples;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.originsd.oqteris.crypto.signature.PdfSignatureInfo;
import org.originsd.oqteris.crypto.signature.PdfSignatureVerifier;
import org.originsd.oqteris.crypto.signature.SignatureVerificationStatus;
import org.originsd.oqteris.crypto.trust.TrustLevel;
import org.originsd.oqteris.crypto.trust.TrustStoreManager;

import java.io.File;
import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated acceptance test verifying the sample signed PDF document
 * can be loaded, verified, and validated with the trust store.
 */
public class SampleFilesVerificationTest {

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
