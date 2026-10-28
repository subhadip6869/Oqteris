package org.oqteris.crypto.signature;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.util.Store;
import org.oqteris.crypto.trust.TrustLevel;
import org.oqteris.crypto.trust.TrustStoreManager;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Parses and cryptographically verifies PDF digital signatures according to PDF and CMS/PKCS#7 standards.
 * Strictly distinguishes mathematical integrity from certificate authority trust.
 */
public class PdfSignatureVerifier {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static List<PdfSignatureInfo> verifySignatures(File pdfFile, PDDocument document) {
        List<PdfSignatureInfo> results = new ArrayList<>();
        if (pdfFile == null || !pdfFile.exists() || document == null) {
            return results;
        }

        long fileLength = pdfFile.length();

        try {
            List<PDSignature> signatures = document.getSignatureDictionaries();
            if (signatures == null || signatures.isEmpty()) {
                return results;
            }

            byte[] fileBytes;
            try (FileInputStream in = new FileInputStream(pdfFile)) {
                fileBytes = in.readAllBytes();
            }

            for (PDSignature sig : signatures) {
                PdfSignatureInfo info = verifySingleSignature(sig, fileBytes, fileLength);
                if (info != null) {
                    results.add(info);
                }
            }
        } catch (Exception e) {
            System.err.println("[PdfSignatureVerifier] Error reading PDF signatures: " + e.getMessage());
        }

        return results;
    }

    private static PdfSignatureInfo verifySingleSignature(PDSignature sig, byte[] fileBytes, long fileLength) {
        String fieldName = sig.getName() != null ? sig.getName() : "Signature";
        String reason = sig.getReason() != null ? sig.getReason() : "";
        String location = sig.getLocation() != null ? sig.getLocation() : "";
        String filter = sig.getFilter() != null ? sig.getFilter() : "";
        String subFilter = sig.getSubFilter() != null ? sig.getSubFilter() : "";
        Date signingTime = sig.getSignDate() != null ? sig.getSignDate().getTime() : new Date();

        int[] byteRange = sig.getByteRange();
        boolean coversWholeDoc = false;
        long byteRangeLength = 0;

        if (byteRange != null && byteRange.length >= 4) {
            int start1 = byteRange[0];
            int len1 = byteRange[1];
            int start2 = byteRange[2];
            int len2 = byteRange[3];
            byteRangeLength = (long) len1 + len2;
            coversWholeDoc = (start1 == 0 && (start2 + len2) >= fileLength);
        }

        byte[] contents;
        byte[] signedContent;
        try {
            contents = sig.getContents(fileBytes);
            signedContent = sig.getSignedContent(fileBytes);
        } catch (Exception e) {
            return new PdfSignatureInfo(fieldName, "Unknown", "Unknown", signingTime, reason, location, filter, subFilter,
                    "Unknown", false, coversWholeDoc, byteRangeLength, fileLength,
                    SignatureVerificationStatus.INVALID_SIGNATURE, "Failed to read signature contents: " + e.getMessage(),
                    null, Collections.emptyList(), false, null);
        }

        if (contents == null || contents.length == 0 || signedContent == null || signedContent.length == 0) {
            return new PdfSignatureInfo(fieldName, "Unknown", "Unknown", signingTime, reason, location, filter, subFilter,
                    "Unknown", false, coversWholeDoc, byteRangeLength, fileLength,
                    SignatureVerificationStatus.INVALID_SIGNATURE, "Empty signature dictionary or signed range.",
                    null, Collections.emptyList(), false, null);
        }

        try {
            ASN1InputStream asn1In = new ASN1InputStream(contents);
            ASN1Primitive primitive = asn1In.readObject();
            ContentInfo contentInfo = ContentInfo.getInstance(primitive);
            CMSSignedData cmsSignedData = new CMSSignedData(new CMSProcessableByteArray(signedContent), contentInfo);
            SignerInformationStore signers = cmsSignedData.getSignerInfos();
            Store<X509CertificateHolder> certStore = cmsSignedData.getCertificates();
            JcaX509CertificateConverter certConverter = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);

            List<X509Certificate> allCertsInDoc = new ArrayList<>();
            for (X509CertificateHolder holder : certStore.getMatches(null)) {
                allCertsInDoc.add(certConverter.getCertificate(holder));
            }

            SignerInformation primarySigner = null;
            X509Certificate signerCert = null;

            for (SignerInformation s : signers.getSigners()) {
                Collection<X509CertificateHolder> matches = certStore.getMatches(s.getSID());
                if (!matches.isEmpty()) {
                    primarySigner = s;
                    signerCert = certConverter.getCertificate(matches.iterator().next());
                    break;
                }
            }

            if (primarySigner == null || signerCert == null) {
                return new PdfSignatureInfo(fieldName, "Signer Unknown", "Unknown", signingTime, reason, location, filter, subFilter,
                        "Unknown", false, coversWholeDoc, byteRangeLength, fileLength,
                        SignatureVerificationStatus.INVALID_SIGNATURE, "Signer certificate not found in CMS structure.",
                        null, allCertsInDoc, false, null);
            }

            // Cryptographic verification
            boolean cryptoValid = primarySigner.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build(signerCert));

            String digestAlg = primarySigner.getDigestAlgOID();
            String signerDN = signerCert.getSubjectX500Principal().getName();
            String signerName = extractCommonName(signerDN);

            // Check signing time attribute
            AttributeTable signedAttrs = primarySigner.getSignedAttributes();
            if (signedAttrs != null) {
                Attribute timeAttr = signedAttrs.get(CMSAttributes.signingTime);
                if (timeAttr != null && timeAttr.getAttrValues().size() > 0) {
                    org.bouncycastle.asn1.cms.Time t = org.bouncycastle.asn1.cms.Time.getInstance(timeAttr.getAttrValues().getObjectAt(0));
                    signingTime = t.getDate();
                }
            }

            // Check RFC 3161 Timestamp Token in unauthenticated attributes
            boolean hasTsp = false;
            Date tspTime = null;
            AttributeTable unsignedAttrs = primarySigner.getUnsignedAttributes();
            if (unsignedAttrs != null) {
                Attribute tspAttr = unsignedAttrs.get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken);
                if (tspAttr != null && tspAttr.getAttrValues().size() > 0) {
                    try {
                        ASN1Sequence seq = ASN1Sequence.getInstance(tspAttr.getAttrValues().getObjectAt(0));
                        TimeStampToken token = new TimeStampToken(new CMSSignedData(seq.getEncoded()));
                        hasTsp = true;
                        tspTime = token.getTimeStampInfo().getGenTime();
                        signingTime = tspTime; // Timestamp token takes precedence for signing time
                    } catch (Exception ignored) {
                    }
                }
            }

            // Assemble certificate chain
            List<X509Certificate> chain = buildChain(signerCert, allCertsInDoc);

            // Trust validation against TrustStoreManager
            TrustStoreManager.TrustValidationResult trustResult = TrustStoreManager.getInstance().validateChain(chain, signingTime);

            // Determine final status
            SignatureVerificationStatus status;
            String statusDetails;

            if (!cryptoValid) {
                status = SignatureVerificationStatus.INVALID_SIGNATURE;
                statusDetails = "Cryptographic verification failed: message digest does not match.";
            } else if (!coversWholeDoc) {
                status = SignatureVerificationStatus.DOCUMENT_MODIFIED;
                statusDetails = "Warning: The document contains incremental changes or updates appended after this signature was applied.";
            } else if (trustResult.getTrustLevel() == TrustLevel.UNTRUSTED) {
                status = SignatureVerificationStatus.CERTIFICATE_EXPIRED;
                statusDetails = trustResult.getMessage();
            } else if (trustResult.getTrustLevel() == TrustLevel.TRUSTED) {
                status = SignatureVerificationStatus.VALID_AND_TRUSTED;
                statusDetails = trustResult.getMessage();
            } else {
                status = SignatureVerificationStatus.VALID_BUT_UNTRUSTED;
                statusDetails = "Mathematical signature is valid, but the signer's identity cannot be verified against any trusted certificate authority: " + trustResult.getMessage();
            }

            return new PdfSignatureInfo(fieldName, signerName, signerDN, signingTime, reason, location, filter, subFilter,
                    digestAlg, cryptoValid, coversWholeDoc, byteRangeLength, fileLength,
                    status, statusDetails, signerCert, chain, hasTsp, tspTime);

        } catch (Exception e) {
            return new PdfSignatureInfo(fieldName, "Error", "Error", signingTime, reason, location, filter, subFilter,
                    "Unknown", false, coversWholeDoc, byteRangeLength, fileLength,
                    SignatureVerificationStatus.INVALID_SIGNATURE, "Verification error: " + e.getMessage(),
                    null, Collections.emptyList(), false, null);
        }
    }

    private static List<X509Certificate> buildChain(X509Certificate leaf, List<X509Certificate> pool) {
        List<X509Certificate> chain = new ArrayList<>();
        chain.add(leaf);
        X509Certificate current = leaf;

        while (current != null) {
            if (TrustStoreManager.isSelfSigned(current)) {
                break;
            }
            X509Certificate issuerCert = null;
            for (X509Certificate candidate : pool) {
                if (candidate.getSubjectX500Principal().equals(current.getIssuerX500Principal())) {
                    try {
                        current.verify(candidate.getPublicKey());
                        issuerCert = candidate;
                        break;
                    } catch (Exception ignored) {
                    }
                }
            }
            if (issuerCert != null && !chain.contains(issuerCert)) {
                chain.add(issuerCert);
                current = issuerCert;
            } else {
                break;
            }
        }
        return chain;
    }

    private static String extractCommonName(String dn) {
        if (dn == null) return "Signer";
        int idx = dn.indexOf("CN=");
        if (idx == -1) return dn;
        int start = idx + 3;
        int end = dn.indexOf(',', start);
        if (end == -1) end = dn.length();
        return dn.substring(start, end).trim();
    }
}
