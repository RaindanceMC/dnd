package de.raindancer.modules.dnd.command;

import de.raindancer.core.platform.command.PlayerLookup;
import de.raindancer.core.platform.command.PlayerTargets;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.dnd.DndServices;
import de.raindancer.modules.dnd.util.PermissionNodes;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code /adventurer} opens the character sheet. {@code rest}, {@code stance} and {@code spells} are there because
 * typing them is faster than two clicks; {@code admin} because it names another player.
 */
public final class AdventurerCommand implements IDndCommand {

    private static final List<String> PLAYER_WORDS = List.of("rest", "stance", "spells", "class");
    private static final List<String> ADMIN_WORDS = List.of("level", "xp", "rest", "reset", "give", "surge");

    private final Supplier<DndServices> services;

    public AdventurerCommand(Supplier<DndServices> services) {
        this.services = services;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, String @NotNull [] args) {
        DndServices live = services.get();
        CommandSender sender = source.getSender();
        String first = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (first.equals("admin")) {
            admin(live, sender, args);
            return;
        }
        if (!(sender instanceof Player player)) {
            live.messages().send(sender, "dnd.only-a-player");
            return;
        }
        if (!player.hasPermission(PermissionNodes.PLAY)) {
            live.messages().send(player, "dnd.not-allowed");
            return;
        }
        switch (first) {
            case "" -> live.screens().sheet(player);
            case "class" -> live.screens().classes(player);
            case "spells" -> live.screens().spellbook(player);
            case "rest" -> live.resting().startShort(player);
            case "stance" -> live.heroes().of(player.getUniqueId()).ifPresentOrElse(hero -> {
                if (live.stance().isIn(player.getUniqueId())) {
                    live.stance().leave(player, false);
                } else {
                    live.stance().enter(player, hero);
                }
            }, () -> live.messages().send(player, "dnd.no-class"));
            default -> live.messages().send(player, "dnd.usage");
        }
    }

    private void admin(DndServices live, CommandSender sender, String[] args) {
        if (!sender.hasPermission(PermissionNodes.ADMIN)) {
            live.messages().send(sender, "dnd.admin.not-allowed");
            return;
        }
        if (args.length < 3) {
            live.messages().send(sender, "dnd.admin.usage");
            return;
        }
        String what = args[1].toLowerCase(Locale.ROOT);
        Optional<Player> found = online(live, sender, args[2]);
        if (found.isEmpty()) {
            return;
        }
        Player target = found.get();
        switch (what) {
            case "level" -> number(live, sender, args).ifPresent(level -> Scheduling.entity(live.plugin(), target,
                    () -> live.heroes().setLevel(target, level.intValue()).ifPresentOrElse(
                            hero -> live.messages().send(sender, "dnd.admin.level-set", "player", target.getName(),
                                    "level", String.valueOf(hero.level())),
                            () -> live.messages().send(sender, "dnd.admin.no-hero", "player", target.getName()))));
            case "xp" -> number(live, sender, args).ifPresent(amount -> Scheduling.entity(live.plugin(), target, () -> {
                if (live.heroes().of(target.getUniqueId()).isEmpty()) {
                    live.messages().send(sender, "dnd.admin.no-hero", "player", target.getName());
                    return;
                }
                live.heroes().grantXp(target, amount, "staff", false);
                live.messages().send(sender, "dnd.admin.xp-given", "player", target.getName(), "xp",
                        String.valueOf(amount));
            }));
            case "rest" -> Scheduling.entity(live.plugin(), target, () -> {
                live.resting().longRest(target, true);
                live.messages().send(sender, "dnd.admin.rested", "player", target.getName());
            });
            case "reset" -> Scheduling.entity(live.plugin(), target, () -> {
                live.stance().leave(target, true);
                live.concentration().forget(target);
                live.heroes().reset(target.getUniqueId(), () -> Scheduling.entity(live.plugin(), target, () -> {
                    live.body().refresh(target, Optional.empty());
                    live.messages().send(target, "dnd.admin.you-were-reset");
                }));
                live.messages().send(sender, "dnd.admin.reset", "player", target.getName());
            });
            case "give" -> give(live, sender, target, args);
            case "surge" -> Scheduling.entity(live.plugin(), target, () -> {
                Optional<de.raindancer.modules.dnd.model.Hero> hero = live.heroes().of(target.getUniqueId());
                if (hero.isEmpty()) {
                    live.messages().send(sender, "dnd.admin.no-hero", "player", target.getName());
                    return;
                }
                de.raindancer.modules.dnd.model.Surge surge;
                if (args.length >= 4) {
                    int roll;
                    try {
                        roll = Integer.parseInt(args[3]);
                    } catch (NumberFormatException notANumber) {
                        live.messages().send(sender, "dnd.admin.not-a-number", "value", args[3]);
                        return;
                    }
                    surge = de.raindancer.modules.dnd.model.WildSurgeTable.entry(roll);
                    live.surges().happen(target, hero.get(), surge);
                } else {
                    surge = live.surges().surge(target, hero.get());
                }
                live.messages().send(sender, "dnd.admin.surged", "player", target.getName(), "number",
                        String.valueOf(surge.number()));
            });
            default -> live.messages().send(sender, "dnd.admin.usage");
        }
    }

    private static void give(DndServices live, CommandSender sender, Player target, String[] args) {
        if (args.length < 4) {
            live.messages().send(sender, "dnd.admin.usage");
            return;
        }
        Optional<de.raindancer.modules.dnd.model.MagicItem> item = de.raindancer.modules.dnd.model.MagicItem
                .byId(args[3]);
        if (item.isEmpty()) {
            live.messages().send(sender, "dnd.admin.unknown-item", "value", args[3]);
            return;
        }
        de.raindancer.modules.dnd.model.Spell spell = null;
        if (item.get() == de.raindancer.modules.dnd.model.MagicItem.SPELL_SCROLL) {
            Optional<de.raindancer.modules.dnd.model.Spell> named = args.length >= 5
                    ? de.raindancer.modules.dnd.model.Spell.byId(args[4]) : Optional.empty();
            if (named.isEmpty()) {
                live.messages().send(sender, "dnd.admin.unknown-spell", "value", args.length >= 5 ? args[4] : "");
                return;
            }
            spell = named.get();
        }
        org.bukkit.inventory.ItemStack stack = live.itemForge().make(item.get(), spell);
        Scheduling.entity(live.plugin(), target, () -> target.getInventory().addItem(stack).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left)));
        live.messages().send(sender, "dnd.admin.given", "player", target.getName(), "item", item.get().title());
    }

    private static Optional<Long> number(DndServices live, CommandSender sender, String[] args) {
        if (args.length < 4) {
            live.messages().send(sender, "dnd.admin.usage");
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(args[3]));
        } catch (NumberFormatException notANumber) {
            live.messages().send(sender, "dnd.admin.not-a-number", "value", args[3]);
            return Optional.empty();
        }
    }

    private static Optional<Player> online(DndServices live, CommandSender sender, String name) {
        PlayerLookup found = PlayerTargets.lookup(live.server(), sender, name);
        if (found.isEmpty() || found.online().isEmpty()) {
            live.messages().send(sender, "dnd.admin.not-online", "player", name);
            return Optional.empty();
        }
        if (found.online().size() > 1) {
            live.messages().send(sender, "dnd.admin.one-at-a-time");
            return Optional.empty();
        }
        return Optional.of(found.online().getFirst());
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, String @NotNull [] args) {
        boolean admin = source.getSender().hasPermission(PermissionNodes.ADMIN);
        String started = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length <= 1) {
            List<String> words = new java.util.ArrayList<>(PLAYER_WORDS);
            if (admin) {
                words.add("admin");
            }
            return words.stream().filter(word -> word.startsWith(started)).toList();
        }
        if (!admin || !args[0].equalsIgnoreCase("admin")) {
            return List.of();
        }
        if (args.length == 2) {
            return ADMIN_WORDS.stream().filter(word -> word.startsWith(started)).toList();
        }
        if (args.length == 3) {
            return PlayerTargets.suggest(services.get().server(), source.getSender(), args[2], who -> true);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("give")) {
            return java.util.Arrays.stream(de.raindancer.modules.dnd.model.MagicItem.values())
                    .map(de.raindancer.modules.dnd.model.MagicItem::id).filter(id -> id.startsWith(started)).toList();
        }
        if (args.length == 5 && args[1].equalsIgnoreCase("give")) {
            return java.util.Arrays.stream(de.raindancer.modules.dnd.model.Spell.values())
                    .map(de.raindancer.modules.dnd.model.Spell::id).filter(id -> id.startsWith(started)).toList();
        }
        return List.of();
    }

    @Override
    public String describe() {
        return "the character sheet, resting, the casting stance, and staff tools for adventurers";
    }
}
