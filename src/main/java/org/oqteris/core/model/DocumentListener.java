package org.oqteris.core.model;

/**
 * Listener for document state changes (e.g. modification, title updates).
 */
@FunctionalInterface
public interface DocumentListener {
    void onDocumentChanged(DocumentModel document);
}
