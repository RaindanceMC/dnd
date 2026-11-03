package de.raindancer.modules.skills;

import de.raindancer.modules.api.ModuleCommand;
import de.raindancer.modules.skills.command.AdventurerCommand;

import java.util.List;

/**
 * What this module declares at bootstrap. Paper registers commands before any module runs, so the handler holds a
 * supplier, and "registered, module not running" is a state a player can reach.
 */
public final class SkillsCommands {

    private static volatile SkillsServices services;

    private SkillsCommands() {
    }

    public static List<ModuleCommand> declared() {
        return List.of(
                ModuleCommand.of("skills", "Your skills and their trees",
                                new AdventurerCommand(SkillsCommands::require))
                        .aliased("adventurer")
                        .aliased("dnd")
                        .aliased("character")
                        .taking("[skill]")
                        .taking("admin level <player> <level>")
                        .taking("admin xp <player> <amount>")
                        .taking("admin unlearn <player>")
                        .taking("admin reset <player>")
                        .taking("admin give <player> <item> [scroll] [tier]"));
    }

    static void ready(SkillsServices live) {
        services = live;
    }

    static void stopped() {
        services = null;
    }

    public static boolean isRunning() {
        return services != null;
    }

    private static SkillsServices require() {
        SkillsServices live = services;
        if (live == null) {
            throw new IllegalStateException("the dnd module is not running");
        }
        return live;
    }
}
