package de.raindancer.modules.dnd.screen;

import de.raindancer.core.ui.menu.Backdrop;
import de.raindancer.modules.dnd.store.DndPack;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.PaginatedMenu;
import de.raindancer.modules.dnd.DndServices;
import de.raindancer.modules.dnd.model.Castable;
import de.raindancer.modules.dnd.model.Hero;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Everything the hero knows, to put in one stance slot. */
public final class SpellPickerMenu extends PaginatedMenu<Castable> implements IDndScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final DndServices services;
    private final int slot;

    public SpellPickerMenu(DndServices services, Player viewer, Menu parent, int slot) {
        super(viewer, services.brand(), parent);
        this.services = services;
        this.slot = slot;
    }

    @Override
    protected Component title() {
        return MINI.deserialize("<dark_gray>Stance slot " + (slot + 1));
    }

    @Override
    public String breadcrumb() {
        return "Slot " + (slot + 1);
    }

    private Optional<Hero> hero() {
        return services.heroes().of(viewer.getUniqueId());
    }

    @Override
    protected List<Castable> entries() {
        return hero().map(found -> services.loadouts().available(found)).orElse(List.of());
    }

    @Override
    protected ItemStack icon(Castable entry) {
        Hero hero = hero().orElseThrow();
        List<String> footer = new ArrayList<>();
        int at = hero.loadout().indexOf(entry.id());
        if (at >= 0) {
            footer.add("<dark_gray>Now in slot " + (at + 1));
        }
        footer.add("<yellow>Click<gray> to put it in slot " + (slot + 1));
        return services.icons().of(entry, hero, footer);
    }

    @Override
    protected void onClick(Castable entry, InventoryClickEvent event) {
        Optional<Hero> placed = services.heroes().change(viewer,
                hero -> services.loadouts().place(hero, slot, entry.id()).orElse(hero));
        services.effects().play(viewer.getUniqueId(), placed.isPresent() ? Cues.OK : Cues.NO);
        backToWhoeverOpenedThis();
    }

    @Override
    protected List<String> helpLines() {
        return List.of("Every spell and feature you know, lowest level first.",
                "Click one to put it in this slot. If it already sits",
                "in another slot, it moves.");
    }

    @Override
    public String describe() {
        return "everything the hero knows, to choose one stance slot's content from";
    }

    @Override
    protected Backdrop backdrop() {
        return DndPack.parchment(layout().rows());
    }
}
