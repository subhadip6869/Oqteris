package org.originsd.oqteris.core.command;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Manages the undo and redo history stacks for a document.
 */
public class UndoRedoManager {
    private final int maxHistory;
    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();

    private final BooleanProperty canUndoProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty canRedoProperty = new SimpleBooleanProperty(false);
    private final StringProperty undoDescriptionProperty = new SimpleStringProperty("Undo");
    private final StringProperty redoDescriptionProperty = new SimpleStringProperty("Redo");

    private Runnable onStateChangedCallback;

    public UndoRedoManager() {
        this(100);
    }

    public UndoRedoManager(int maxHistory) {
        this.maxHistory = Math.max(10, maxHistory);
    }

    public void setOnStateChanged(Runnable callback) {
        this.onStateChangedCallback = callback;
    }

    /**
     * Executes a command and pushes it onto the undo stack, clearing the redo stack.
     */
    public void executeCommand(Command command) {
        if (command == null) return;
        command.execute();
        push(command);
    }

    /**
     * Pushes an already-executed command onto the undo stack.
     */
    public void push(Command command) {
        if (command == null) return;
        if (undoStack.size() >= maxHistory) {
            undoStack.removeLast();
        }
        undoStack.push(command);
        redoStack.clear();
        updateProperties();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public BooleanProperty canUndoProperty() {
        return canUndoProperty;
    }

    public BooleanProperty canRedoProperty() {
        return canRedoProperty;
    }

    public StringProperty undoDescriptionProperty() {
        return undoDescriptionProperty;
    }

    public StringProperty redoDescriptionProperty() {
        return redoDescriptionProperty;
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        Command command = undoStack.pop();
        command.undo();
        redoStack.push(command);
        updateProperties();
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        Command command = redoStack.pop();
        command.execute();
        undoStack.push(command);
        updateProperties();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
        updateProperties();
    }

    private void updateProperties() {
        canUndoProperty.set(!undoStack.isEmpty());
        canRedoProperty.set(!redoStack.isEmpty());
        undoDescriptionProperty.set(undoStack.isEmpty() ? "Undo" : "Undo " + undoStack.peek().getDescription());
        redoDescriptionProperty.set(redoStack.isEmpty() ? "Redo" : "Redo " + redoStack.peek().getDescription());
        if (onStateChangedCallback != null) {
            onStateChangedCallback.run();
        }
    }
}
