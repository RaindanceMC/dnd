package de.raindancer.modules.dnd.store;

import de.raindancer.core.RainsCore;
import de.raindancer.core.content.pack.JarPack;
import de.raindancer.core.ui.menu.Backdrop;
import de.raindancer.core.platform.log.Log;
import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.HeroClass;
import de.raindancer.modules.dnd.model.MagicItem;
import de.raindancer.modules.dnd.model.Metamagic;
import de.raindancer.modules.dnd.model.Spell;
import de.raindancer.modules.dnd.model.Subclass;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The adventurers' looks — an icon for every spell, feature, class and subclass, and a model for every magic item —
 * offered to Core's resource packs. Made in {@code dnd-module/art}; {@code files.txt} is the index of what ships.
 *
 * <p>Every item keeps its vanilla material, so a player without the pack still sees something sensible.
 */
public final class DndPack {

    public static final String NAMESPACE = "rainsadventurers";
    static final String ROOT = "/de/raindancer/modules/dnd/pack/";
    public static final String EMPTY = "ui/empty";
    public static final String SHEET = "ui/sheet";
    private static final String OWNER = "Adventurers";
    private static final String NAME = "looks";
    private static volatile List<String> files;
    private static final net.kyori.adventure.text.format.TextColor INK =
            net.kyori.adventure.text.format.TextColor.color(0x4A3220);
    private static final net.kyori.adventure.key.Key PARCHMENT_FONT =
            net.kyori.adventure.key.Key.key(NAMESPACE, "parchment");
    /** The character sheet's own page; its slots are where {@code art/gui/sheet.py} painted them. */
    public static final Backdrop SHEET_BACKDROP =
            new Backdrop(net.kyori.adventure.key.Key.key(NAMESPACE, "sheet"), "\uF808\uE000\uF8A9", INK,
                    Backdrop.TITLE_FONT);

    private DndPack() {
    }

    public static List<String> files() {
        List<String> known = files;
        if (known != null) {
            return known;
        }
        try (InputStream in = DndPack.class.getResourceAsStream(ROOT + "files.txt")) {
            if (in == null) {
                return List.of();
            }
            known = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                    .map(String::trim).filter(line -> !line.isEmpty()).toList();
        } catch (IOException unreadable) {
            return List.of();
        }
        files = known;
        return known;
    }

    /** Every other adventurers' menu: a parchment page, in the sheet's hand. */
    public static Backdrop parchment(int rows) {
        return new Backdrop(PARCHMENT_FONT, "\uF808" + (char) (0xE000 + Math.max(1, Math.min(6, rows))) + "\uF8A9",
                INK, Backdrop.TITLE_FONT);
    }

    public static String iconOf(Spell spell) {
        return "spell/" + spell.id();
    }

    public static String iconOf(Feature feature) {
        return "feature/" + feature.id();
    }

    public static String classIcon(HeroClass heroClass) {
        return "class/" + heroClass.id();
    }

    public static String subclassIcon(Subclass subclass) {
        return "subclass/" + subclass.id();
    }

    public static String metamagicIcon(Metamagic metamagic) {
        return "metamagic/" + (metamagic == null ? "none" : metamagic.name().toLowerCase(Locale.ROOT));
    }

    /** Weapons whose rune overlays the pack has; a bow, a crossbow or a trident keeps its vanilla look. */
    private static final java.util.Set<org.bukkit.Material> RUNED = java.util.EnumSet.of(
            org.bukkit.Material.IRON_SWORD, org.bukkit.Material.DIAMOND_SWORD, org.bukkit.Material.NETHERITE_SWORD,
            org.bukkit.Material.IRON_AXE, org.bukkit.Material.DIAMOND_AXE, org.bukkit.Material.NETHERITE_AXE);

    /**
     * A magic item's look: its own model, a scroll's seal in its rarity's colour, a weapon's runes over its vanilla
     * self — or empty, for a weapon the pack has no runes for.
     */
    public static Optional<String> itemModel(MagicItem item, Spell spell, org.bukkit.Material base) {
        if (item.isWeapon()) {
            if (base == null || !RUNED.contains(base)) {
                return Optional.empty();
            }
            return Optional.of("magic/weapon/" + base.name().toLowerCase(Locale.ROOT) + "_plus_"
                    + item.amount().bonus());
        }
        if (item == MagicItem.SPELL_SCROLL) {
            de.raindancer.modules.dnd.model.Rarity rarity = spell == null
                    ? de.raindancer.modules.dnd.model.Rarity.COMMON
                    : de.raindancer.modules.dnd.model.Rarity.ofSpellLevel(spell.level());
            return Optional.of("magic/spell_scroll_" + rarity.name().toLowerCase(Locale.ROOT));
        }
        return Optional.of("magic/" + item.id());
    }

    /** Points the stack at one of this pack's item definitions. */
    public static ItemStack dress(ItemStack stack, String path) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey(NAMESPACE, path));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static void contribute(Plugin plugin, RainsCore core) {
        try {
            JarPack.offer(plugin, core.resourcePacks(), DndPack.class, ROOT, files(), OWNER, NAME,
                    "Icons for spells, features and classes, and models for the magic items");
        } catch (IOException failure) {
            Log.of("dnd").warn("The adventurers' icons and item models could not be written ({}); everything works, "
                    + "but looks like the vanilla items underneath.", failure.getMessage());
        }
    }

    public static void withdraw(RainsCore core) {
        core.resourcePacks().withdraw(OWNER.toLowerCase(Locale.ROOT) + ":" + NAME);
    }
}
