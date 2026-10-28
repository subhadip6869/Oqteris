package org.oqteris.crypto.trust;

/**
 * Trust policy levels assigned to certificates in Oqteris.
 */
public enum TrustLevel {
    /**
     * Explicitly trusted root or intermediate certificate authority.
     */
    TRUSTED("Trusted Authority", "#28a745"),

    /**
     * Explicitly untrusted or revoked certificate.
     */
    UNTRUSTED("Untrusted", "#dc3545"),

    /**
     * Unknown certificate requiring user confirmation / prompt.
     */
    UNKNOWN("Unknown / Prompt", "#ffc107");

    private final String label;
    private final String colorHex;

    TrustLevel(String label, String colorHex) {
        this.label = label;
        this.colorHex = colorHex;
    }

    public String getLabel() {
        return label;
    }

    public String getColorHex() {
        return colorHex;
    }
}
