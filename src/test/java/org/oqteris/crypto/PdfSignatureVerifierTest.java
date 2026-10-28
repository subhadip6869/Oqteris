package org.oqteris.crypto;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.oqteris.crypto.signature.PdfSignatureInfo;
import org.oqteris.crypto.signature.PdfSignatureVerifier;
import org.oqteris.crypto.signature.SignatureVerificationStatus;
import org.oqteris.crypto.trust.TrustLevel;
import org.oqteris.crypto.trust.TrustStoreManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PdfSignatureVerifierTest {
    private static KeyPair testKeyPair;
    private static X509Certificate testCert;

    @BeforeAll
    public static void setUp() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        testKeyPair = kpg.generateKeyPair();

        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 10000);
        Date notAfter = new Date(now + 365L * 86400000L);
        BigInteger serial = BigInteger.valueOf(now);
        X500Name subject = new X500Name("CN=Oqteris Test Signer, O=Oqteris Corporation, C=US");

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, testKeyPair.getPublic()
        );
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider(BouncyCastleProvider.PROVIDER_NAME).build(testKeyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        testCert = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(holder);
    }

    @Test
    public void testSignAndVerifyPdf() throws Exception {
        File baseFile = File.createTempFile("oqteris_base_", ".pdf");
        baseFile.deleteOnExit();

        File signedFile = File.createTempFile("oqteris_signed_", ".pdf");
        signedFile.deleteOnExit();

        // 1. Create base PDF
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(100, 700);
                cs.showText("Oqteris Office Suite - Digitally Signed Test Document");
                cs.endText();
            }

            doc.save(baseFile);
        }

        // 2. Load and apply digital signature incrementally
        try (PDDocument doc = Loader.loadPDF(baseFile)) {
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName("Oqteris Test Signer");
            signature.setLocation("New York, USA");
            signature.setReason("Security and Verification Test");
            signature.setSignDate(Calendar.getInstance());

            doc.addSignature(signature, new SignatureInterface() {
                @Override
                public byte[] sign(InputStream content) {
                    try {
                        byte[] data = content.readAllBytes();
                        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
                        ContentSigner cs = new JcaContentSignerBuilder("SHA256withRSA").setProvider(BouncyCastleProvider.PROVIDER_NAME).build(testKeyPair.getPrivate());
                        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                                new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build()
                        ).build(cs, testCert));
                        gen.addCertificates(new JcaCertStore(Collections.singletonList(testCert)));
                        CMSSignedData signedData = gen.generate(new CMSProcessableByteArray(data), false);
                        return signedData.getEncoded();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            try (FileOutputStream fos = new FileOutputStream(signedFile)) {
                doc.saveIncremental(fos);
            }
        }

        // 3. Verify signature on signed document when cert is NOT in trust store
        try (PDDocument readDoc = Loader.loadPDF(signedFile)) {
            List<PdfSignatureInfo> sigs = PdfSignatureVerifier.verifySignatures(signedFile, readDoc);
            assertEquals(1, sigs.size());
            PdfSignatureInfo info = sigs.get(0);

            assertTrue(info.isCryptographicallyValid(), "Mathematical cryptographic signature must be valid");
            assertTrue(info.coversWholeDocument(), "Byte range must cover whole file");
            assertEquals(SignatureVerificationStatus.VALID_BUT_UNTRUSTED, info.getStatus(),
                    "Self-signed certificate must NOT be marked trusted by default!");

            // 4. Now explicitly trust the certificate in TrustStoreManager
            String alias = "test_signer_trust_alias";
            TrustStoreManager.getInstance().importCertificate(alias, testCert, TrustLevel.TRUSTED, "Test trust");

            // Re-verify
            List<PdfSignatureInfo> recheckedSigs = PdfSignatureVerifier.verifySignatures(signedFile, readDoc);
            PdfSignatureInfo trustedInfo = recheckedSigs.get(0);

            assertEquals(SignatureVerificationStatus.VALID_AND_TRUSTED, trustedInfo.getStatus(),
                    "Once trusted in store, status must change to VALID_AND_TRUSTED");

            TrustStoreManager.getInstance().removeCertificate(alias);
        }
    }
}
