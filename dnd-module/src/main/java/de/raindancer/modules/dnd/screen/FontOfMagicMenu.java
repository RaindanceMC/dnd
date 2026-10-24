package de.raindancer.modules.dnd.screen;

import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.dnd.DndServices;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.service.IconForge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Font of Magic: a sorcerer's points into slots, and slots into points. */
public final class FontOfMagicMenu extends Menu implements IDndScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final DndServices services;

    public FontOfMagicMenu(DndServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    @Override
    protected Component title() {
        return MINI.deserialize("<dark_gray>Font of Magic");
    }

    @Override
    public String breadcrumb() {
        return "Font of Magic";
    }

    @Override
    protected void render() {
        Optional<Hero> found = services.heroes().of(viewer.getUniqueId());
        if (found.isEmpty()) {
            return;
        }
        Hero hero = found.get();
        set(MenuLayout.HEADER_SUBJECT, Icons.of(Material.AMETHYST_CLUSTER, "<light_purple>Sorcery points",
                "<gray>Left: <white>" + hero.sorceryPoints() + "</white> of " + services.sorcery().most(hero),
                "<dark_gray>Slots made here are gone at your next long rest."));
        for (int level = 1; level <= 5; level++) {
            int slotLevel = level;
            if (services.slots().most(hero, level) == 0) {
                band(MenuLayout.RULES, level, false, Icons.of(Material.AMETHYST_SHARD,
                        "<gray>" + IconForge.ordinal(level) + "-level slots"), "Not at your level yet", click -> { });
                continue;
            }
            band(MenuLayout.RULES, level, slotIcon(hero, level), click -> {
                if (click.isRightClick()) {
                    burn(slotLevel);
                } else {
                    make(slotLevel);
                }
            });
        }
    }

    private ItemStack slotIcon(Hero hero, int level) {
        int cost = services.sorcery().slotCost(level).orElse(0);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>You have <white>" + services.slots().available(hero, level) + "</white> left");
        lore.add("");
        lore.add("<yellow>Click<gray> to make one for <white>" + cost + "</white> points");
        lore.add("<yellow>Right click<gray> to turn one into <white>" + level + "</white> point(s)");
        ItemStack icon = Icons.of(Material.AMETHYST_SHARD, "<light_purple>" + IconForge.ordinal(level)
                + "-level slots", lore);
        icon.setAmount(Math.max(1, Math.min(64, services.slots().available(hero, level))));
        return icon;
    }

    private void make(int level) {
        Hero before = services.heroes().of(viewer.getUniqueId()).orElseThrow();
        Optional<Hero> made = services.sorcery().makeSlot(before, level);
        if (made.isEmpty()) {
            services.messages().send(viewer, "dnd.font.cannot-make", "level", IconForge.ordinal(level),
                    "cost", String.valueOf(services.sorcery().slotCost(level).orElse(0)));
            services.effects().play(viewer.getUniqueId(), Cues.NO);
            return;
        }
        services.heroes().change(viewer, hero -> services.sorcery().makeSlot(hero, level).orElse(hero));
        services.effects().play(viewer.getUniqueId(), Cues.MAGIC);
        refresh();
    }

    private void burn(int level) {
        Hero before = services.heroes().of(viewer.getUniqueId()).orElseThrow();
        if (services.sorcery().burnSlot(before, level).isEmpty()) {
            services.messages().send(viewer, "dnd.font.cannot-burn", "level", IconForge.ordinal(level));
            services.effects().play(viewer.getUniqueId(), Cues.NO);
            return;
        }
        services.heroes().change(viewer, hero -> services.sorcery().burnSlot(hero, level).orElse(hero));
        services.effects().play(viewer.getUniqueId(), Cues.MAGIC);
        refresh();
    }

    @Override
    protected List<String> helpLines() {
        return List.of("Spell slots cost 2, 3, 5, 6 and 7 sorcery points",
                "for 1st to 5th level. A slot given up is worth its",
                "level in points. Both come back on a long rest.");
    }

    @Override
    public String describe() {
        return "turning sorcery points into spell slots and back";
    }
}
