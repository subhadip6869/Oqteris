package org.originsd.oqteris.crypto.trust;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Parsed information representing an X.509 certificate for UI inspection.
 */
public class CertificateDetails {
    private final X509Certificate certificate;
    private final String subjectDN;
    private final String issuerDN;
    private final String serialNumber;
    private final Date notBefore;
    private final Date notAfter;
    private final String sigAlgName;
    private final String publicKeyInfo;
    private final String sha256Fingerprint;
    private final String sha1Fingerprint;
    private final boolean isCertificateAuthority;
    private final List<String> keyUsages = new ArrayList<>();

    public CertificateDetails(X509Certificate cert) {
        this.certificate = cert;
        this.subjectDN = cert.getSubjectX500Principal().getName();
        this.issuerDN = cert.getIssuerX500Principal().getName();
        this.serialNumber = cert.getSerialNumber().toString(16).toUpperCase(Locale.ROOT);
        this.notBefore = cert.getNotBefore();
        this.notAfter = cert.getNotAfter();
        this.sigAlgName = cert.getSigAlgName();

        // Key info
        if (cert.getPublicKey() instanceof RSAPublicKey rsa) {
            this.publicKeyInfo = "RSA " + rsa.getModulus().bitLength() + " bits";
        } else {
            this.publicKeyInfo = cert.getPublicKey().getAlgorithm();
        }

        // Fingerprints
        this.sha256Fingerprint = computeFingerprint(cert, "SHA-256");
        this.sha1Fingerprint = computeFingerprint(cert, "SHA-1");

        // Basic Constraints
        this.isCertificateAuthority = cert.getBasicConstraints() != -1;

        // Key usage
        boolean[] usages = cert.getKeyUsage();
        if (usages != null) {
            String[] names = {
                    "Digital Signature", "Non-Repudiation", "Key Encipherment",
                    "Data Encipherment", "Key Agreement", "Key Certificate Sign",
                    "CRL Sign", "Encipher Only", "Decipher Only"
            };
            for (int i = 0; i < Math.min(usages.length, names.length); i++) {
                if (usages[i]) {
                    keyUsages.add(names[i]);
                }
            }
        }
    }

    private static String computeFingerprint(X509Certificate cert, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(cert.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < digest.length; i++) {
                sb.append(String.format("%02X", digest[i]));
                if (i < digest.length - 1) sb.append(":");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Unavailable";
        }
    }

    public X509Certificate getCertificate() {
        return certificate;
    }

    public String getSubjectDN() {
        return subjectDN;
    }

    public String getCommonName() {
        return extractAttr(subjectDN, "CN=");
    }

    public String getIssuerCommonName() {
        return extractAttr(issuerDN, "CN=");
    }

    public String getIssuerDN() {
        return issuerDN;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public Date getNotBefore() {
        return notBefore;
    }

    public Date getNotAfter() {
        return notAfter;
    }

    public String getSigAlgName() {
        return sigAlgName;
    }

    public String getPublicKeyInfo() {
        return publicKeyInfo;
    }

    public String getSha256Fingerprint() {
        return sha256Fingerprint;
    }

    public String getSha1Fingerprint() {
        return sha1Fingerprint;
    }

    public boolean isCertificateAuthority() {
        return isCertificateAuthority;
    }

    public List<String> getKeyUsages() {
        return Collections.unmodifiableList(keyUsages);
    }

    public boolean isExpiredAt(Date date) {
        return date.after(notAfter) || date.before(notBefore);
    }

    public String getFormattedValidity() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(notBefore) + " to " + sdf.format(notAfter);
    }

    private static String extractAttr(String dn, String prefix) {
        if (dn == null) return "Unknown";
        int idx = dn.indexOf(prefix);
        if (idx == -1) return dn;
        int start = idx + prefix.length();
        int end = dn.indexOf(',', start);
        if (end == -1) end = dn.length();
        return dn.substring(start, end).trim();
    }
}
