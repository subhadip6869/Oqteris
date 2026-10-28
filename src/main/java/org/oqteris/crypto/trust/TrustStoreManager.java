package org.oqteris.crypto.trust;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the application-level trust store and interfaces with system certificate authorities.
 * Strictly separates cryptographic verification from trust anchoring.
 */
public class TrustStoreManager {
    private static final TrustStoreManager INSTANCE = new TrustStoreManager();

    private static final String STORE_TYPE = "PKCS12";
    private static final char[] STORE_PASSWORD = "oqteris-trust-store".toCharArray();

    private Path trustStorePath;
    private KeyStore appKeyStore;
    private final Map<String, TrustedCertificateRecord> records = new ConcurrentHashMap<>();
    private final Set<X509Certificate> systemTrustAnchors = new HashSet<>();
    private boolean useSystemTrustStore = true;

    private TrustStoreManager() {
        this.trustStorePath = resolveTrustStorePath();
        initTrustStore();
        loadSystemTrustAnchors();
    }

    private static Path resolveTrustStorePath() {
        String userHome = System.getProperty("user.home", ".");
        Path primary = Paths.get(userHome, ".oqteris", "truststore.p12");
        try {
            if (primary.getParent() != null) {
                Files.createDirectories(primary.getParent());
            }
            return primary;
        } catch (Exception ex) {
            // Fallback to temporary directory if user.home is not writable
            String tmpDir = System.getProperty("java.io.tmpdir", ".");
            Path fallback = Paths.get(tmpDir, ".oqteris", "truststore.p12");
            try {
                if (fallback.getParent() != null) {
                    Files.createDirectories(fallback.getParent());
                }
                return fallback;
            } catch (Exception ignored) {
                return Paths.get("truststore.p12");
            }
        }
    }

    public static TrustStoreManager getInstance() {
        return INSTANCE;
    }

    private void initTrustStore() {
        try {
            appKeyStore = KeyStore.getInstance(STORE_TYPE);
            if (Files.exists(trustStorePath)) {
                try (InputStream in = Files.newInputStream(trustStorePath)) {
                    appKeyStore.load(in, STORE_PASSWORD);
                }
                loadRecordsFromKeyStore();
            } else {
                Files.createDirectories(trustStorePath.getParent());
                appKeyStore.load(null, STORE_PASSWORD);
                saveKeyStore();
            }
        } catch (Exception e) {
            System.err.println("[TrustStoreManager] Failed to initialize trust store: " + e.getMessage());
            try {
                appKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                appKeyStore.load(null, null);
            } catch (Exception ignored) {
            }
        }
    }

    private void loadRecordsFromKeyStore() {
        try {
            Enumeration<String> aliases = appKeyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = appKeyStore.getCertificate(alias);
                if (cert instanceof X509Certificate x509) {
                    records.put(alias, new TrustedCertificateRecord(alias, x509, TrustLevel.TRUSTED, Instant.now(), ""));
                }
            }
        } catch (Exception e) {
            System.err.println("[TrustStoreManager] Error loading records: " + e.getMessage());
        }
    }

    private synchronized void saveKeyStore() {
        try {
            if (trustStorePath.getParent() != null) {
                Files.createDirectories(trustStorePath.getParent());
            }
            try (OutputStream out = Files.newOutputStream(trustStorePath)) {
                appKeyStore.store(out, STORE_PASSWORD);
            }
        } catch (Exception e) {
            // Try fallback path if first attempt failed
            try {
                Path altPath = Paths.get(System.getProperty("java.io.tmpdir", "."), ".oqteris", "truststore.p12");
                if (altPath.getParent() != null) Files.createDirectories(altPath.getParent());
                try (OutputStream out = Files.newOutputStream(altPath)) {
                    appKeyStore.store(out, STORE_PASSWORD);
                    this.trustStorePath = altPath;
                }
            } catch (Exception ex2) {
                System.err.println("[TrustStoreManager] Failed to persist trust store: " + e.getMessage());
            }
        }
    }

    private void loadSystemTrustAnchors() {
        try {
            // 1. Try JRE default cacerts
            String javaHome = System.getProperty("java.home");
            Path cacertsPath = Paths.get(javaHome, "lib", "security", "cacerts");
            if (Files.exists(cacertsPath)) {
                KeyStore cacerts = KeyStore.getInstance(KeyStore.getDefaultType());
                try (InputStream in = Files.newInputStream(cacertsPath)) {
                    cacerts.load(in, "changeit".toCharArray());
                    Enumeration<String> aliases = cacerts.aliases();
                    while (aliases.hasMoreElements()) {
                        Certificate c = cacerts.getCertificate(aliases.nextElement());
                        if (c instanceof X509Certificate x509) {
                            systemTrustAnchors.add(x509);
                        }
                    }
                }
            }

            // 2. On Windows, check Windows-ROOT
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                try {
                    KeyStore winRoot = KeyStore.getInstance("Windows-ROOT");
                    winRoot.load(null, null);
                    Enumeration<String> winAliases = winRoot.aliases();
                    while (winAliases.hasMoreElements()) {
                        Certificate c = winRoot.getCertificate(winAliases.nextElement());
                        if (c instanceof X509Certificate x509) {
                            systemTrustAnchors.add(x509);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            System.err.println("[TrustStoreManager] Could not load system roots: " + e.getMessage());
        }
    }

    public boolean isUseSystemTrustStore() {
        return useSystemTrustStore;
    }

    public void setUseSystemTrustStore(boolean useSystemTrustStore) {
        this.useSystemTrustStore = useSystemTrustStore;
    }

    public List<TrustedCertificateRecord> getCustomRecords() {
        return new ArrayList<>(records.values());
    }

    public int getSystemAnchorCount() {
        return systemTrustAnchors.size();
    }

    public synchronized void importCertificate(String alias, X509Certificate cert, TrustLevel level, String notes) throws KeyStoreException {
        if (alias == null || alias.isBlank()) {
            alias = "cert_" + System.currentTimeMillis();
        }
        appKeyStore.setCertificateEntry(alias, cert);
        records.put(alias, new TrustedCertificateRecord(alias, cert, level, Instant.now(), notes));
        saveKeyStore();
    }

    public synchronized void removeCertificate(String alias) throws KeyStoreException {
        if (alias != null && appKeyStore.containsAlias(alias)) {
            appKeyStore.deleteEntry(alias);
            records.remove(alias);
            saveKeyStore();
        }
    }

    /**
     * Finds if any trusted anchor matches or signs the candidate certificate.
     */
    public Optional<X509Certificate> findTrustAnchor(X509Certificate candidate) {
        if (candidate == null) return Optional.empty();

        // 1. Check custom records in application trust store
        for (TrustedCertificateRecord record : records.values()) {
            if (record.getTrustLevel() == TrustLevel.TRUSTED) {
                if (isSameCertificate(candidate, record.getCertificate())) {
                    return Optional.of(record.getCertificate());
                }
                if (candidate.getIssuerX500Principal().equals(record.getCertificate().getSubjectX500Principal())) {
                    try {
                        candidate.verify(record.getCertificate().getPublicKey());
                        return Optional.of(record.getCertificate());
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // 2. Check system roots if enabled
        if (useSystemTrustStore) {
            for (X509Certificate root : systemTrustAnchors) {
                if (isSameCertificate(candidate, root)) {
                    return Optional.of(root);
                }
                if (candidate.getIssuerX500Principal().equals(root.getSubjectX500Principal())) {
                    try {
                        candidate.verify(root.getPublicKey());
                        return Optional.of(root);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Evaluates trust status for a certificate chain.
     */
    public TrustValidationResult validateChain(List<X509Certificate> chain, Date validationDate) {
        if (chain == null || chain.isEmpty()) {
            return new TrustValidationResult(false, TrustLevel.UNTRUSTED, "Certificate chain is empty", null);
        }

        Date checkDate = validationDate != null ? validationDate : new Date();
        X509Certificate leaf = chain.get(0);

        // Check explicit untrusted blacklist in application store
        for (TrustedCertificateRecord record : records.values()) {
            if (record.getTrustLevel() == TrustLevel.UNTRUSTED) {
                if (isSameCertificate(leaf, record.getCertificate())) {
                    return new TrustValidationResult(false, TrustLevel.UNTRUSTED, "Certificate is explicitly marked as untrusted by user policy", leaf);
                }
            }
        }

        // Check expiration
        for (X509Certificate cert : chain) {
            try {
                cert.checkValidity(checkDate);
            } catch (CertificateExpiredException e) {
                return new TrustValidationResult(false, TrustLevel.UNTRUSTED, "Certificate has expired: " + cert.getSubjectX500Principal().getName(), cert);
            } catch (CertificateNotYetValidException e) {
                return new TrustValidationResult(false, TrustLevel.UNTRUSTED, "Certificate is not yet valid: " + cert.getSubjectX500Principal().getName(), cert);
            }
        }

        // Check if any certificate in chain (from root down to leaf signer) connects to an explicitly trusted anchor
        for (int i = chain.size() - 1; i >= 0; i--) {
            X509Certificate cert = chain.get(i);
            Optional<X509Certificate> anchor = findTrustAnchor(cert);
            if (anchor.isPresent()) {
                String desc = (i == 0)
                        ? "Signer certificate is explicitly trusted in Oqteris trust store"
                        : "Chain verified to trusted authority: " + anchor.get().getSubjectX500Principal().getName();
                return new TrustValidationResult(true, TrustLevel.TRUSTED, desc, anchor.get());
            }
        }

        X509Certificate topCert = chain.get(chain.size() - 1);
        return new TrustValidationResult(false, TrustLevel.UNKNOWN, "No trusted root anchor found for issuer: " + topCert.getIssuerX500Principal().getName(), topCert);
    }

    private static boolean isSameCertificate(X509Certificate a, X509Certificate b) {
        if (a == null || b == null) return false;
        try {
            return Arrays.equals(a.getEncoded(), b.getEncoded());
        } catch (Exception e) {
            return a.getSerialNumber().equals(b.getSerialNumber()) &&
                    a.getIssuerX500Principal().equals(b.getIssuerX500Principal());
        }
    }

    public static boolean isSelfSigned(X509Certificate cert) {
        if (!cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) {
            return false;
        }
        try {
            cert.verify(cert.getPublicKey());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static class TrustValidationResult {
        private final boolean trusted;
        private final TrustLevel trustLevel;
        private final String message;
        private final X509Certificate anchor;

        public TrustValidationResult(boolean trusted, TrustLevel trustLevel, String message, X509Certificate anchor) {
            this.trusted = trusted;
            this.trustLevel = trustLevel;
            this.message = message;
            this.anchor = anchor;
        }

        public boolean isTrusted() {
            return trusted;
        }

        public TrustLevel getTrustLevel() {
            return trustLevel;
        }

        public String getMessage() {
            return message;
        }

        public X509Certificate getAnchor() {
            return anchor;
        }
    }
}
