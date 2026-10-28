package org.oqteris.core.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Combines multiple commands into a single atomic undo/redo action.
 */
public class CompoundCommand implements Command {
    private final String description;
    private final List<Command> commands = new ArrayList<>();

    public CompoundCommand(String description) {
        this.description = description;
    }

    public void add(Command command) {
        if (command != null) {
            commands.add(command);
        }
    }

    @Override
    public void execute() {
        for (Command command : commands) {
            command.execute();
        }
    }

    @Override
    public void undo() {
        // Undo in reverse order
        List<Command> reverse = new ArrayList<>(commands);
        Collections.reverse(reverse);
        for (Command command : reverse) {
            command.undo();
        }
    }

    @Override
    public String getDescription() {
        return description;
    }
}
