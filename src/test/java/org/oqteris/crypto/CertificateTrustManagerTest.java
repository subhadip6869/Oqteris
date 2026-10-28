package org.oqteris.crypto;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.oqteris.crypto.trust.TrustLevel;
import org.oqteris.crypto.trust.TrustStoreManager;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class CertificateTrustManagerTest {
    private static KeyPair testKeyPair;
    private static X509Certificate testCertificate;

    @BeforeAll
    public static void setUp() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        // Generate RSA KeyPair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        testKeyPair = kpg.generateKeyPair();

        // Generate Self-Signed Certificate
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 10000);
        Date notAfter = new Date(now + 365L * 86400000L);
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());

        X500Name subject = new X500Name("CN=Oqteris Test Authority, O=Oqteris Security, C=US");
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, testKeyPair.getPublic()
        );

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider(BouncyCastleProvider.PROVIDER_NAME).build(testKeyPair.getPrivate());
        X509CertificateHolder holder = certBuilder.build(signer);

        testCertificate = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(holder);
    }

    @Test
    public void testTrustPolicyDistinguishesCryptographicFromTrust() throws Exception {
        TrustStoreManager manager = TrustStoreManager.getInstance();

        // 1. Initial State: Certificate is valid self-signed, but NOT in trust store
        TrustStoreManager.TrustValidationResult untrustedResult = manager.validateChain(Collections.singletonList(testCertificate), new Date());
        assertFalse(untrustedResult.isTrusted(), "Certificate must NOT be trusted before import into trust store");
        assertEquals(TrustLevel.UNKNOWN, untrustedResult.getTrustLevel());

        // 2. Import into Trust Store
        String alias = "oqteris_unit_test_cert";
        manager.importCertificate(alias, testCertificate, TrustLevel.TRUSTED, "Imported during automated test");

        // 3. Re-validate: Now explicitly trusted
        TrustStoreManager.TrustValidationResult trustedResult = manager.validateChain(Collections.singletonList(testCertificate), new Date());
        assertTrue(trustedResult.isTrusted(), "Certificate must be trusted after importing to trust store");
        assertEquals(TrustLevel.TRUSTED, trustedResult.getTrustLevel());

        // 4. Remove Certificate from Trust Store
        manager.removeCertificate(alias);

        // 5. Re-validate: No longer trusted
        TrustStoreManager.TrustValidationResult postRemoveResult = manager.validateChain(Collections.singletonList(testCertificate), new Date());
        assertFalse(postRemoveResult.isTrusted(), "Certificate must not be trusted after removal");
    }
}
