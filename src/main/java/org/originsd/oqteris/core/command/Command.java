package org.originsd.oqteris.core.command;

/**
 * Command pattern interface for document actions supporting undo/redo.
 */
public interface Command {
    /**
     * Executes the command.
     */
    void execute();

    /**
     * Undoes the effect of the command.
     */
    void undo();

    /**
     * Short human-readable description for UI menus (e.g. "Undo Typing", "Redo Bold").
     */
    String getDescription();
}
