package org.oqteris.core.io;

import org.oqteris.core.model.DocumentModel;

import java.io.File;
import java.io.IOException;

/**
 * Interface for writing documents of type T to persistent storage.
 */
public interface DocumentExporter<T extends DocumentModel> {
    /**
     * The format descriptor handled by this exporter.
     */
    FormatDescriptor getFormatDescriptor();

    /**
     * Determines whether this exporter can output to the specified target file format.
     */
    boolean canExport(File file);

    /**
     * Serializes the in-memory document model to disk.
     */
    void exportDocument(T document, File file) throws IOException;
}
