package de.raindancer.modules.dnd.store;

import de.raindancer.modules.dnd.model.MagicItem;
import de.raindancer.modules.dnd.model.Scroll;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/** What makes an item a magic one, written on the item itself: which item, a scroll's kind and tier, its charges. */
public final class ItemTags {

    static final NamespacedKey ITEM = new NamespacedKey("rains_dnd", "item");
    /** A scroll's kind; before 0.11.0, the spell on it. */
    static final NamespacedKey SPELL = new NamespacedKey("rains_dnd", "spell");
    /** A scroll's tier, 1 to 5; missing on a scroll from before 0.11.0. */
    static final NamespacedKey TIER = new NamespacedKey("rains_dnd", "tier");
    static final NamespacedKey CHARGES = new NamespacedKey("rains_dnd", "charges");
    static final NamespacedKey CHARGED_AT = new NamespacedKey("rains_dnd", "charged_at");
    /** On an arrow: the bonus of the bow that shot it. */
    static final NamespacedKey BONUS = new NamespacedKey("rains_dnd", "bonus");

    private ItemTags() {
    }

    public static Optional<MagicItem> itemOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return MagicItem.byId(stack.getItemMeta().getPersistentDataContainer().get(ITEM, PersistentDataType.STRING));
    }

    /** What a scroll sets off; an old scroll's spell is read as what it became. */
    public static Optional<Scroll> scrollOf(ItemStack stack) {
        String written = read(stack, SPELL, PersistentDataType.STRING, null);
        if (written == null) {
            return Optional.empty();
        }
        return hasTier(stack) ? Scroll.byId(written).or(() -> Optional.of(Scroll.legacyKind(written)))
                : Optional.of(Scroll.legacyKind(written));
    }

    /** A scroll's tier; an old scroll's is its spell's. */
    public static int tierOf(ItemStack stack) {
        if (hasTier(stack)) {
            return Scroll.clampTier(read(stack, TIER, PersistentDataType.INTEGER, 1));
        }
        return Scroll.legacyTier(read(stack, SPELL, PersistentDataType.STRING, null));
    }

    private static boolean hasTier(ItemStack stack) {
        return read(stack, TIER, PersistentDataType.INTEGER, null) != null;
    }

    public static int chargesOf(ItemStack stack) {
        return read(stack, CHARGES, PersistentDataType.INTEGER, 0);
    }

    public static long chargedAtOf(ItemStack stack) {
        return read(stack, CHARGED_AT, PersistentDataType.LONG, 0L);
    }

    private static <T> T read(ItemStack stack, NamespacedKey key, PersistentDataType<?, T> type, T otherwise) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
            return otherwise;
        }
        T value = stack.getItemMeta().getPersistentDataContainer().get(key, type);
        return value == null ? otherwise : value;
    }

    public static void stamp(ItemMeta meta, MagicItem item, Scroll scroll, int tier) {
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(ITEM, PersistentDataType.STRING, item.id());
        if (scroll != null) {
            data.set(SPELL, PersistentDataType.STRING, scroll.id());
            data.set(TIER, PersistentDataType.INTEGER, Scroll.clampTier(tier));
        }
    }

    public static void charges(ItemMeta meta, int charges, long chargedAt) {
        meta.getPersistentDataContainer().set(CHARGES, PersistentDataType.INTEGER, charges);
        meta.getPersistentDataContainer().set(CHARGED_AT, PersistentDataType.LONG, chargedAt);
    }

    public static void bonus(PersistentDataHolder holder, int bonus) {
        holder.getPersistentDataContainer().set(BONUS, PersistentDataType.INTEGER, bonus);
    }

    public static int bonusOf(PersistentDataHolder holder) {
        Integer bonus = holder.getPersistentDataContainer().get(BONUS, PersistentDataType.INTEGER);
        return bonus == null ? 0 : bonus;
    }
}
