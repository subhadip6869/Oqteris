package org.oqteris.core.model;

/**
 * Enumeration of document types supported by the Oqteris office suite.
 */
public enum DocumentType {
    WRITER("Word Processor Document", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    CALC("Spreadsheet Workbook", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    IMPRESS("Presentation Slides", "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
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
