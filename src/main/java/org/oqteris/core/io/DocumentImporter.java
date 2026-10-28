package org.oqteris.core.io;

import org.oqteris.core.model.DocumentModel;

import java.io.File;
import java.io.IOException;

/**
 * Interface for reading documents of type T from persistent storage.
 */
public interface DocumentImporter<T extends DocumentModel> {
    /**
     * The format descriptor handled by this importer.
     */
    FormatDescriptor getFormatDescriptor();

    /**
     * Determines whether this importer can handle the given file.
     */
    boolean canImport(File file);

    /**
     * Parses the file into an in-memory document model.
     */
    T importDocument(File file) throws IOException;
}
