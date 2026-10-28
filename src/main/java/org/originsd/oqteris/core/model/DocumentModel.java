package org.originsd.oqteris.core.model;

import org.originsd.oqteris.core.command.UndoRedoManager;

import java.io.File;

/**
 * Common abstraction representing an active document in Oqteris.
 */
public interface DocumentModel {
    /**
     * Document type (WRITER, CALC, IMPRESS, PDF).
     */
    DocumentType getType();

    /**
     * Display title of the document.
     */
    String getTitle();

    /**
     * Sets display title of the document.
     */
    void setTitle(String title);

    /**
     * Associated file path, or null if the document has not yet been saved to disk.
     */
    File getFile();

    /**
     * Sets the associated file path.
     */
    void setFile(File file);

    /**
     * Whether the document contains unsaved changes.
     */
    boolean isModified();

    /**
     * Sets dirty state flag.
     */
    void setModified(boolean modified);

    /**
     * Document metadata (author, created, keywords, etc.).
     */
    DocumentMetadata getMetadata();

    /**
     * Undo/Redo manager for this document.
     */
    UndoRedoManager getUndoRedoManager();

    /**
     * Registers a change listener.
     */
    void addDocumentListener(DocumentListener listener);

    /**
     * Removes a change listener.
     */
    void removeDocumentListener(DocumentListener listener);

    /**
     * Notifies all registered listeners of a document change.
     */
    void notifyChanged();
}
