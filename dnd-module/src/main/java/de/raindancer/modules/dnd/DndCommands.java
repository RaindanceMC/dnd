package de.raindancer.modules.dnd;

import de.raindancer.modules.api.ModuleCommand;
import de.raindancer.modules.dnd.command.AdventurerCommand;

import java.util.List;

/**
 * What this module declares at bootstrap. Paper registers commands before any module runs, so the handler holds a
 * supplier, and "registered, module not running" is a state a player can reach.
 */
public final class DndCommands {

    private static volatile DndServices services;

    private DndCommands() {
    }

    public static List<ModuleCommand> declared() {
        return List.of(
                ModuleCommand.of("adventurer", "Your class, your spells and your rests",
                                new AdventurerCommand(DndCommands::require))
                        .aliased("dnd")
                        .aliased("character")
                        .taking("rest")
                        .taking("stance")
                        .taking("spells")
                        .taking("class")
                        .taking("admin level <player> <level>")
                        .taking("admin xp <player> <amount>")
                        .taking("admin rest <player>")
                        .taking("admin reset <player>"));
    }

    static void ready(DndServices live) {
        services = live;
    }

    static void stopped() {
        services = null;
    }

    public static boolean isRunning() {
        return services != null;
    }

    private static DndServices require() {
        DndServices live = services;
        if (live == null) {
            throw new IllegalStateException("the dnd module is not running");
        }
        return live;
    }
}
