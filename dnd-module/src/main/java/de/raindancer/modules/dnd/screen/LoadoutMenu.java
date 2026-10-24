package de.raindancer.modules.dnd.screen;

import de.raindancer.core.ui.menu.Backdrop;
import de.raindancer.modules.dnd.store.DndPack;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.dnd.DndServices;
import de.raindancer.modules.dnd.model.Castable;
import de.raindancer.modules.dnd.model.Hero;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/** The spellbook: the eight stance slots, each opened to choose what goes in it. */
public final class LoadoutMenu extends Menu implements IDndScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final int ROW = 2;

    private final DndServices services;

    public LoadoutMenu(DndServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    @Override
    protected Component title() {
        return MINI.deserialize("<dark_gray>Spellbook");
    }

    @Override
    public String breadcrumb() {
        return "Spellbook";
    }

    @Override
    protected void render() {
        Optional<Hero> found = services.heroes().of(viewer.getUniqueId());
        if (found.isEmpty()) {
            set(MenuLayout.HEADER_SUBJECT, Icons.of(Material.BOOK, "<gray>No class yet",
                    "<yellow>Click<gray> to choose one"), click -> new ClassMenu(services, viewer, this).open());
            return;
        }
        Hero hero = found.get();
        int known = services.loadouts().available(hero).size();
        set(MenuLayout.HEADER_SUBJECT, Icons.of(Material.WRITABLE_BOOK, "<aqua>Your casting stance",
                "<gray>Slots 1 to 8 of your hotbar while in the stance.",
                "<gray>You know <white>" + known + "</white> spells and features to choose from.",
                "<dark_gray>Slot 9 is your sheet" + (services.metamagic().knows(hero) ? " and metamagic." : ".")));
        for (int slot = 0; slot < Hero.LOADOUT_SIZE; slot++) {
            int index = slot;
            Optional<Castable> in = services.loadouts().at(hero, slot);
            ItemStack icon = in.map(castable -> services.icons().of(castable, hero, List.of(
                            "<dark_gray>Stance slot " + (index + 1),
                            "<yellow>Click<gray> to put something else here",
                            "<yellow>Right click<gray> to empty it")))
                    .orElseGet(() -> Icons.of(Material.GRAY_STAINED_GLASS_PANE, "<gray>Slot " + (index + 1)
                            + " is empty", "<yellow>Click<gray> to choose what goes here"));
            cell(ROW, slot, icon, click -> {
                if (click.isRightClick()) {
                    services.heroes().change(viewer, now -> services.loadouts().cleared(now, index));
                    services.effects().play(viewer.getUniqueId(), Cues.CLICK);
                    refresh();
                    return;
                }
                new SpellPickerMenu(services, viewer, this, index).open();
            });
        }
    }

    @Override
    protected List<String> helpLines() {
        return List.of("What your casting stance holds.",
                "Click a slot to choose a spell or feature for it,",
                "right click to empty it. New ones you learn on",
                "levelling up go into empty slots by themselves.");
    }

    @Override
    public String describe() {
        return "the eight casting stance slots and what is in each";
    }

    @Override
    protected Backdrop backdrop() {
        return DndPack.parchment(layout().rows());
    }
}
