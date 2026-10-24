package de.raindancer.modules.dnd.screen;

import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.dnd.DndServices;
import de.raindancer.modules.dnd.model.Ability;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Choosing a class: all six side by side, what each is for, and what choosing costs. */
public final class ClassMenu extends Menu implements IDndScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final DndServices services;

    public ClassMenu(DndServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    @Override
    protected Component title() {
        return MINI.deserialize("<dark_gray>Choose your class");
    }

    @Override
    public String breadcrumb() {
        return "Classes";
    }

    @Override
    protected void render() {
        Optional<Hero> hero = services.heroes().of(viewer.getUniqueId());
        boolean keeps = services.config().classChangeKeepsLevel();
        set(MenuLayout.HEADER_SUBJECT, Icons.of(Material.ENCHANTED_BOOK, "<gold>Choose your class",
                hero.isEmpty() ? "<gray>Pick one to begin at level 1."
                        : keeps ? "<gray>Changing keeps your level."
                        : "<red>Changing starts you again at level 1."));
        int column = 1;
        for (HeroClass heroClass : HeroClass.values()) {
            boolean current = hero.map(found -> found.heroClass() == heroClass).orElse(false);
            band(MenuLayout.RULES, column++, !current, icon(heroClass), "Your class now", click -> pick(heroClass));
        }
    }

    private org.bukkit.inventory.ItemStack icon(HeroClass heroClass) {
        List<String> lore = new ArrayList<>();
        heroClass.pitch().forEach(line -> lore.add("<gray>" + line));
        lore.add("");
        lore.add("<dark_gray>Hit die: <white>d" + heroClass.hitDie());
        lore.add("<dark_gray>Best at: <white>" + heroClass.primary().title());
        List<String> saves = new ArrayList<>();
        for (Ability ability : Ability.values()) {
            if (heroClass.proficientIn(ability)) {
                saves.add(ability.title());
            }
        }
        lore.add("<dark_gray>Saving throws: <white>" + String.join(", ", saves));
        lore.add("<dark_gray>Spells: <white>" + heroClass.spellcasting().map(Ability::title).map(name -> "yes, with " + name)
                .orElse("none, class features instead"));
        lore.add("");
        lore.add("<yellow>Click<gray> to become one");
        return Icons.of(heroClass.icon(), "<gold>" + heroClass.title(), lore);
    }

    private void pick(HeroClass heroClass) {
        if (!services.heroes().known(viewer.getUniqueId())) {
            services.messages().send(viewer, services.heroes().isUnreadable(viewer.getUniqueId())
                    ? "dnd.unreadable" : "dnd.still-loading");
            services.effects().play(viewer.getUniqueId(), Cues.NO);
            return;
        }
        Optional<Hero> hero = services.heroes().of(viewer.getUniqueId());
        if (hero.isEmpty()) {
            become(heroClass);
            return;
        }
        List<String> consequences = services.config().classChangeKeepsLevel()
                ? List.of("<gray>You keep level " + hero.get().level() + ".",
                "<gray>Your ability scores start again from the class's own.")
                : List.of("<red>You start again at level 1.", "<gray>Your " + hero.get().heroClass().title()
                + " levels are gone.");
        new ConfirmScreen(viewer, services.brand(), this, "<red>Become a " + heroClass.title() + "?",
                consequences, () -> become(heroClass)).open();
    }

    private void become(HeroClass heroClass) {
        services.stance().leave(viewer, true);
        services.concentration().forget(viewer);
        if (services.heroes().choose(viewer, heroClass).isEmpty()) {
            services.messages().send(viewer, "dnd.still-loading");
            return;
        }
        services.effects().play(viewer.getUniqueId(), Cues.REWARD);
        services.messages().send(viewer, "dnd.class-chosen", "class", heroClass.title());
        new SheetMenu(services, viewer, null).open();
    }

    @Override
    protected List<String> helpLines() {
        return List.of("Six classes, each with its own way of fighting.",
                "Fighters, barbarians and rogues use class features;",
                "wizards, sorcerers and clerics cast spells.",
                "Everybody levels from 1 to 20 by defeating monsters.");
    }

    @Override
    public String describe() {
        return "the six classes, side by side, to choose from";
    }
}
