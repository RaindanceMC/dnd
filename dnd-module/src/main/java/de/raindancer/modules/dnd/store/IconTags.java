package de.raindancer.modules.dnd.store;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/** Marks the stance's icons, so one found anywhere it should not be is recognised and removed. */
public final class IconTags {

    static final NamespacedKey KEY = new NamespacedKey("rains_dnd", "icon");

    /** The ninth slot: the character sheet, or a sorcerer's metamagic. */
    public static final String SHEET = "#sheet";
    /** A loadout slot with nothing in it. */
    public static final String EMPTY = "#empty";

    private IconTags() {
    }

    public static void stamp(ItemStack stack, String id) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
    }

    public static boolean isIcon(ItemStack stack) {
        return idOf(stack).isPresent();
    }

    public static Optional<String> idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING));
    }
}
