package org.originsd.oqteris.crypto.signature;

/**
 * Categorization of PDF digital signature verification status.
 * Strictly separates cryptographic integrity from certificate authority trust.
 */
public enum SignatureVerificationStatus {
    /**
     * Mathematical signature is valid AND the certificate chains to an explicitly trusted anchor.
     */
    VALID_AND_TRUSTED("Valid & Trusted", "#28a745", "The digital signature is cryptographically valid and certified by a trusted authority."),

    /**
     * Mathematical signature is valid, BUT the certificate is self-signed or not anchored to a trusted root.
     */
    VALID_BUT_UNTRUSTED("Valid but Untrusted", "#ffc107", "The signature is cryptographically intact, but the signer's certificate is not trusted by your trust store."),

    /**
     * Mathematical signature failed or digest mismatch.
     */
    INVALID_SIGNATURE("Invalid Signature", "#dc3545", "The digital signature is invalid. Content or signature has been corrupted."),

    /**
     * Document was modified or appended after the signature was applied.
     */
    DOCUMENT_MODIFIED("Document Altered After Signing", "#dc3545", "The document has been modified after this signature was applied."),

    /**
     * Certificate has expired or is not yet valid at the time of signing.
     */
    CERTIFICATE_EXPIRED("Certificate Expired", "#dc3545", "The signing certificate was not valid at the time of signing or verification.");

    private final String displayTitle;
    private final String colorHex;
    private final String description;

    SignatureVerificationStatus(String displayTitle, String colorHex, String description) {
        this.displayTitle = displayTitle;
        this.colorHex = colorHex;
        this.description = description;
    }

    public String getDisplayTitle() {
        return displayTitle;
    }

    public String getColorHex() {
        return colorHex;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCryptographicallyValid() {
        return this == VALID_AND_TRUSTED || this == VALID_BUT_UNTRUSTED;
    }

    public boolean isTrusted() {
        return this == VALID_AND_TRUSTED;
    }
}
