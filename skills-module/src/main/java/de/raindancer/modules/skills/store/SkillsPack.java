package de.raindancer.modules.skills.store;

import de.raindancer.core.RainsCore;
import de.raindancer.core.content.pack.JarPack;
import de.raindancer.core.ui.menu.Backdrop;
import de.raindancer.core.platform.log.Log;
import de.raindancer.modules.skills.model.MagicItem;
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
 * The adventurers' looks — a model for every magic item, the skill tree's frames, lines and paintings — offered to
 * Core's resource packs. Made in {@code dnd-module/art}; {@code files.txt} is the index of what ships.
 *
 * <p>Every item keeps its vanilla material, so a player without the pack still sees something sensible.
 */
public final class SkillsPack {

    public static final String NAMESPACE = "rainsadventurers";
    static final String ROOT = "/de/raindancer/modules/skills/pack/";
    private static final String OWNER = "Adventurers";
    private static final String NAME = "looks";
    private static volatile List<String> files;
    private static final net.kyori.adventure.text.format.TextColor INK =
            net.kyori.adventure.text.format.TextColor.color(0x4A3220);
    private static final net.kyori.adventure.key.Key PARCHMENT_FONT =
            net.kyori.adventure.key.Key.key(NAMESPACE, "parchment");

    private SkillsPack() {
    }

    public static List<String> files() {
        List<String> known = files;
        if (known != null) {
            return known;
        }
        try (InputStream in = SkillsPack.class.getResourceAsStream(ROOT + "files.txt")) {
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

    /** The skill tree's frames and lines: brass rings and runed chain, painted in art/tree. */
    public static final de.raindancer.core.ui.tree.TreeLook TREE_LOOK =
            new de.raindancer.core.ui.tree.TreeLook(net.kyori.adventure.key.Key.key(NAMESPACE, "tree"));
    /** The branches' paintings, in this order from U+E000 in the font rainsadventurers:tree_scene. */
    static final List<String> SCENES = List.of("vitality", "might", "marksman", "shadow", "arcana",
            "guardian", "warden", "arms", "fury", "trickshot", "ranger", "acrobatics", "rogue", "mysticism",
            "battlemage",
            "prospector", "delver", "veinbreaker", "digger", "treasure_hunter", "archaeologist",
            "lumberjack", "forester", "feller", "anvil_work", "salvager", "forgemaster",
            "fields", "pasture", "gatherer", "herbalist", "kitchen", "table", "potency", "apparatus", "pack", "stable");
    private static final net.kyori.adventure.text.format.TextColor TREE_INK =
            net.kyori.adventure.text.format.TextColor.color(0xF1E4C6);

    /** The painting behind a skill tree branch; {@code null} — the house's — for a branch without one. */
    public static Backdrop treeBackdrop(String branch) {
        int index = SCENES.indexOf(branch);
        if (index < 0) {
            return null;
        }
        return new Backdrop(net.kyori.adventure.key.Key.key(NAMESPACE, "tree_scene"),
                "\uF808" + (char) (0xE000 + index) + "\uF8A9", TREE_INK);
    }

    /** Weapons whose rune overlays the pack has; a bow, a crossbow or a trident keeps its vanilla look. */
    private static final java.util.Set<org.bukkit.Material> RUNED = java.util.EnumSet.of(
            org.bukkit.Material.IRON_SWORD, org.bukkit.Material.DIAMOND_SWORD, org.bukkit.Material.NETHERITE_SWORD,
            org.bukkit.Material.IRON_AXE, org.bukkit.Material.DIAMOND_AXE, org.bukkit.Material.NETHERITE_AXE);

    /**
     * A magic item's look: its own model, a scroll's seal in its rarity's colour, a weapon's runes over its vanilla
     * self — or empty, for a weapon the pack has no runes for.
     */
    public static Optional<String> itemModel(MagicItem item, de.raindancer.modules.skills.model.Rarity rarity,
                                             org.bukkit.Material base) {
        if (item.isWeapon()) {
            if (base == null || !RUNED.contains(base)) {
                return Optional.empty();
            }
            return Optional.of("magic/weapon/" + base.name().toLowerCase(Locale.ROOT) + "_plus_"
                    + item.amount().bonus());
        }
        if (item == MagicItem.SPELL_SCROLL) {
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
            JarPack.offer(plugin, core.resourcePacks(), SkillsPack.class, ROOT, files(), OWNER, NAME,
                    "Models for the magic items, and the skill tree's looks");
        } catch (IOException failure) {
            Log.of("dnd").warn("The adventurers' icons and item models could not be written ({}); everything works, "
                    + "but looks like the vanilla items underneath.", failure.getMessage());
        }
    }

    public static void withdraw(RainsCore core) {
        core.resourcePacks().withdraw(OWNER.toLowerCase(Locale.ROOT) + ":" + NAME);
    }
}
