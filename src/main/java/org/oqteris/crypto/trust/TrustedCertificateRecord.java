package org.oqteris.crypto.trust;

import java.security.cert.X509Certificate;
import java.time.Instant;

/**
 * Record of a certificate managed inside the Oqteris application trust store.
 */
public class TrustedCertificateRecord {
    private final String alias;
    private final X509Certificate certificate;
    private final CertificateDetails details;
    private TrustLevel trustLevel;
    private final Instant addedDate;
    private String notes;

    public TrustedCertificateRecord(String alias, X509Certificate certificate, TrustLevel trustLevel, Instant addedDate, String notes) {
        this.alias = alias;
        this.certificate = certificate;
        this.details = new CertificateDetails(certificate);
        this.trustLevel = trustLevel != null ? trustLevel : TrustLevel.TRUSTED;
        this.addedDate = addedDate != null ? addedDate : Instant.now();
        this.notes = notes != null ? notes : "";
    }

    public String getAlias() {
        return alias;
    }

    public X509Certificate getCertificate() {
        return certificate;
    }

    public CertificateDetails getDetails() {
        return details;
    }

    public TrustLevel getTrustLevel() {
        return trustLevel;
    }

    public void setTrustLevel(TrustLevel trustLevel) {
        this.trustLevel = trustLevel;
    }

    public Instant getAddedDate() {
        return addedDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
