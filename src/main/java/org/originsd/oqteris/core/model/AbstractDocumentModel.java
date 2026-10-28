package org.originsd.oqteris.core.model;

import org.originsd.oqteris.core.command.UndoRedoManager;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base implementation of DocumentModel providing standard state management.
 */
public abstract class AbstractDocumentModel implements DocumentModel {
    private final DocumentType type;
    private String title;
    private File file;
    private boolean modified;
    private final DocumentMetadata metadata = new DocumentMetadata();
    private final UndoRedoManager undoRedoManager = new UndoRedoManager();
    private final List<DocumentListener> listeners = new CopyOnWriteArrayList<>();

    public AbstractDocumentModel(DocumentType type, String defaultTitle) {
        this.type = type;
        this.title = defaultTitle;
        this.metadata.setTitle(defaultTitle);
        this.undoRedoManager.setOnStateChanged(() -> {
            setModified(true);
            notifyChanged();
        });
    }

    @Override
    public DocumentType getType() {
        return type;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public void setTitle(String title) {
        this.title = title != null ? title : "Untitled";
        this.metadata.setTitle(this.title);
        notifyChanged();
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public void setFile(File file) {
        this.file = file;
        if (file != null) {
            setTitle(file.getName());
        }
        notifyChanged();
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public void setModified(boolean modified) {
        if (this.modified != modified) {
            this.modified = modified;
            notifyChanged();
        }
    }

    @Override
    public DocumentMetadata getMetadata() {
        return metadata;
    }

    @Override
    public UndoRedoManager getUndoRedoManager() {
        return undoRedoManager;
    }

    @Override
    public void addDocumentListener(DocumentListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeDocumentListener(DocumentListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void notifyChanged() {
        for (DocumentListener listener : listeners) {
            listener.onDocumentChanged(this);
        }
    }
}
