package org.originsd.oqteris.core.model;

/**
 * Document type supported by Oqteris PDF Reader and Editor.
 */
public enum DocumentType {
    PDF("PDF Document", "pdf", "application/pdf");

    private final String displayName;
    private final String defaultExtension;
    private final String mimeType;

    DocumentType(String displayName, String defaultExtension, String mimeType) {
        this.displayName = displayName;
        this.defaultExtension = defaultExtension;
        this.mimeType = mimeType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultExtension() {
        return defaultExtension;
    }

    public String getMimeType() {
        return mimeType;
    }
}
