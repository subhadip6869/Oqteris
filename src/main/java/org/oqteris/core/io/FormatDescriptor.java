package org.oqteris.core.io;

import org.oqteris.core.model.DocumentType;

import java.util.Collections;
import java.util.List;

/**
 * Metadata descriptor for a supported document file format.
 */
public class FormatDescriptor {
    private final String formatId;
    private final String description;
    private final DocumentType documentType;
    private final List<String> extensions;
    private final String mimeType;
    private final boolean canRead;
    private final boolean canWrite;

    public FormatDescriptor(String formatId, String description, DocumentType documentType,
                            List<String> extensions, String mimeType, boolean canRead, boolean canWrite) {
        this.formatId = formatId;
        this.description = description;
        this.documentType = documentType;
        this.extensions = Collections.unmodifiableList(extensions);
        this.mimeType = mimeType;
        this.canRead = canRead;
        this.canWrite = canWrite;
    }

    public String getFormatId() {
        return formatId;
    }

    public String getDescription() {
        return description;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public String getMimeType() {
        return mimeType;
    }

    public boolean canRead() {
        return canRead;
    }

    public boolean canWrite() {
        return canWrite;
    }

    public String getDefaultExtension() {
        return extensions.isEmpty() ? "" : extensions.get(0);
    }
}
