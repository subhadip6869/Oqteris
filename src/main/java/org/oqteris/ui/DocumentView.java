package org.oqteris.ui;

import javafx.scene.Node;
import org.oqteris.core.model.DocumentModel;

/**
 * Interface implemented by UI editors and viewers for documents.
 */
public interface DocumentView {
    /**
     * Gets the associated document model.
     */
    DocumentModel getDocument();

    /**
     * Contextual toolbar or ribbon controls for this editor.
     */
    Node getRibbonToolbar();

    /**
     * Contextual status bar widget (e.g. word count, active cell, page count).
     */
    Node getStatusBarNode();

    /**
     * Closes resources associated with this view.
     */
    void close();

    /**
     * Executes copy action on current selection.
     */
    default void copy() {}

    /**
     * Executes cut action on current selection.
     */
    default void cut() {}

    /**
     * Executes paste action from system clipboard.
     */
    default void paste() {}

    /**
     * Selects all content in the document.
     */
    default void selectAll() {}

    /**
     * Initiates search/find within document.
     */
    default void showSearch() {}

    /**
     * Initiates print/print preview for this document.
     */
    default void print() {}

    /**
     * Gets current zoom factor.
     */
    default double getZoom() { return 1.0; }

    /**
     * Sets current zoom factor.
     */
    default void setZoom(double zoom) {}
}
