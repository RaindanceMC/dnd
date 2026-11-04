package de.raindancer.modules.skills.skill.building;

import de.raindancer.core.ui.messages.Messages;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /skills blueprint mark|save <name>|list|paste <name> [rotate 90|180|270]|undo|delete <name>} — our own words
 * for blueprints, not a world editor's. Held here once the module is up, since the command is declared before it is.
 */
public final class BlueprintCommand {

    public static final String WORD = "blueprint";
    private static final List<String> SUBCOMMANDS = List.of("mark", "save", "list", "paste", "undo", "delete");
    private static volatile Blueprints live;

    private BlueprintCommand() {
    }

    public static void ready(Blueprints blueprints) {
        live = blueprints;
    }

    public static void stopped() {
        live = null;
    }

    /** @param args the whole command's arguments, {@code blueprint} first */
    public static void execute(Messages messages, Player player, String[] args) {
        Blueprints blueprints = live;
        if (blueprints == null) {
            messages.send(player, "dnd.still-reading");
            return;
        }
        String sub = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        String name = args.length < 3 ? null : args[2];
        switch (sub) {
            case "mark" -> blueprints.mark(player);
            case "list" -> blueprints.list(player);
            case "undo" -> blueprints.undo(player);
            case "save" -> withName(messages, player, name, blueprints::save);
            case "delete" -> withName(messages, player, name, blueprints::delete);
            case "paste" -> {
                if (name == null) {
                    messages.send(player, "dnd.blueprint.usage");
                    return;
                }
                Optional<Integer> quarters = turn(args);
                if (quarters.isEmpty()) {
                    messages.send(player, "dnd.blueprint.bad-turn");
                    return;
                }
                blueprints.paste(player, name, quarters.get());
            }
            default -> messages.send(player, "dnd.blueprint.usage");
        }
    }

    /** {@code rotate 90} after the name; none is no turn. */
    static Optional<Integer> turn(String[] args) {
        if (args.length < 4) {
            return Optional.of(0);
        }
        if (!args[3].equalsIgnoreCase("rotate") || args.length < 5) {
            return Optional.empty();
        }
        try {
            return BlueprintRule.quarters(Integer.parseInt(args[4]));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static void withName(Messages messages, Player player, String name,
                                 java.util.function.BiConsumer<Player, String> then) {
        if (name == null) {
            messages.send(player, "dnd.blueprint.usage");
            return;
        }
        then.accept(player, name);
    }

    /** @param args the whole command's arguments, {@code blueprint} first */
    public static List<String> suggest(Player player, String[] args) {
        String started = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return SUBCOMMANDS.stream().filter(word -> word.startsWith(started)).toList();
        }
        Blueprints blueprints = live;
        if (args.length == 3 && blueprints != null && player != null
                && (args[1].equalsIgnoreCase("paste") || args[1].equalsIgnoreCase("delete"))) {
            return blueprints.names(player.getUniqueId()).stream().filter(name -> name.startsWith(started)).toList();
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("paste")) {
            return "rotate".startsWith(started) ? List.of("rotate") : List.of();
        }
        if (args.length == 5 && args[1].equalsIgnoreCase("paste") && args[3].equalsIgnoreCase("rotate")) {
            return List.of("90", "180", "270").stream().filter(word -> word.startsWith(started)).toList();
        }
        return List.of();
    }
}
