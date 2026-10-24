package de.raindancer.modules.dnd.store;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Optional;

/**
 * The real hotbar of a player in the casting stance, kept on the player themself.
 *
 * <p>On the player's own data rather than in memory, because the server saves that with the inventory: a crash
 * mid-stance leaves the icons in the hotbar <em>and</em> this beside them, and the next join puts the items back.
 * Kept only while the stance is up, and removed in the same breath as the items are restored.
 */
public final class StanceStash {

    static final NamespacedKey KEY = new NamespacedKey("rains_dnd", "stance_hotbar");

    public static final int HOTBAR = 9;

    public boolean holds(Player player) {
        return player.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE_ARRAY);
    }

    public void keep(Player player, ItemStack[] hotbar) {
        ItemStack[] copy = new ItemStack[HOTBAR];
        for (int slot = 0; slot < HOTBAR; slot++) {
            ItemStack item = hotbar != null && slot < hotbar.length ? hotbar[slot] : null;
            copy[slot] = item == null ? ItemStack.empty() : item.clone();
        }
        player.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE_ARRAY,
                ItemStack.serializeItemsAsBytes(copy));
    }

    /** What was kept, nine slots long; empty when nothing was, or it cannot be read. */
    public Optional<ItemStack[]> read(Player player) {
        byte[] bytes = player.getPersistentDataContainer().get(KEY, PersistentDataType.BYTE_ARRAY);
        if (bytes == null) {
            return Optional.empty();
        }
        try {
            ItemStack[] items = ItemStack.deserializeItemsFromBytes(bytes);
            return Optional.of(Arrays.copyOf(items, HOTBAR));
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    public void drop(Player player) {
        player.getPersistentDataContainer().remove(KEY);
    }
}
