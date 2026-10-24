package de.raindancer.modules.dnd.command;

import io.papermc.paper.command.brigadier.BasicCommand;

/**
 * A command belonging to this module. Built at bootstrap, so it captures nothing the module builds and looks the
 * module up through a supplier when it runs.
 */
public interface IDndCommand extends BasicCommand {

    /** What this command is for, for the diagnostic that lists them. */
    String describe();
}
