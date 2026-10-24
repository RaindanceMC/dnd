package de.raindancer.modules.dnd.screen;

import de.raindancer.core.ui.menu.Backdrop;
import de.raindancer.modules.dnd.store.DndPack;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.dnd.DndServices;
import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.Subclass;
import de.raindancer.modules.dnd.service.IconForge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Choosing a subclass where there is a choice: a sorcerer's origin. Once chosen, it stays. */
public final class SubclassMenu extends Menu implements IDndScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final DndServices services;

    public SubclassMenu(DndServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    @Override
    protected Component title() {
        return MINI.deserialize("<dark_gray>Choose your origin");
    }

    @Override
    public String breadcrumb() {
        return "Origin";
    }

    @Override
    protected void render() {
        Optional<Hero> found = services.heroes().of(viewer.getUniqueId());
        if (found.isEmpty()) {
            return;
        }
        Hero hero = found.get();
        int column = 2;
        for (Subclass subclass : Subclass.of(hero.heroClass())) {
            boolean may = services.subclasses().mayChoose(hero, subclass);
            band(MenuLayout.RULES, column, may, icon(subclass),
                    hero.subclass() == subclass ? "Your origin" : "Already chosen, or not yet your level",
                    click -> choose(subclass));
            column += 2;
        }
    }

    private ItemStack icon(Subclass subclass) {
        List<String> lore = new ArrayList<>();
        for (String line : IconForge.wrapText(subclass.summary())) {
            lore.add("<gray>" + line);
        }
        lore.add("");
        for (Feature feature : Feature.values()) {
            if (feature.subclass().filter(owner -> owner == subclass).isPresent()) {
                lore.add("<dark_gray>Level " + feature.level() + ": <white>" + feature.title());
            }
        }
        lore.add("");
        lore.add("<yellow>Click<gray> to choose it, for good");
        return de.raindancer.modules.dnd.store.DndPack.dress(Icons.of(subclass.icon(),
                "<light_purple>" + subclass.title(), lore), de.raindancer.modules.dnd.store.DndPack.subclassIcon(subclass));
    }

    private void choose(Subclass subclass) {
        new ConfirmScreen(viewer, services.brand(), this, "<light_purple>Become a " + subclass.title() + "?",
                List.of("<gray>An origin is chosen once."), () -> {
                    if (services.heroes().chooseSubclass(viewer, subclass).isEmpty()) {
                        services.effects().play(viewer.getUniqueId(), Cues.NO);
                        return;
                    }
                    services.effects().play(viewer.getUniqueId(), Cues.REWARD);
                    services.messages().send(viewer, "dnd.subclass.chosen", "subclass", subclass.title());
                    new SheetMenu(services, viewer, null).open();
                }).open();
    }

    @Override
    protected List<String> helpLines() {
        return List.of("A sorcerer's power comes from somewhere.",
                "Wild Magic: chaos that may surge with any spell.",
                "Draconic Bloodline: scales, fire and, at last, wings.");
    }

    @Override
    public String describe() {
        return "a sorcerer's choice of origin";
    }

    @Override
    protected Backdrop backdrop() {
        return DndPack.parchment(layout().rows());
    }
}
