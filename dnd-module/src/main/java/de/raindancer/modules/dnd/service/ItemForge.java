package de.raindancer.modules.dnd.service;

import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.MagicItem;
import de.raindancer.modules.dnd.model.Rarity;
import de.raindancer.modules.dnd.model.Scroll;
import de.raindancer.modules.dnd.store.ItemTags;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

/** Makes magic items, and keeps what a player reads on one in step with what is written on it. */
public final class ItemForge implements IDndService {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final LongSupplier clock;

    public ItemForge(LongSupplier clock) {
        this.clock = clock;
    }

    @Override
    public void settings(DndSettings settings) {
        // Items read nothing configurable.
    }

    public ItemStack make(MagicItem item) {
        return make(item, null, 0, item.material());
    }

    public ItemStack scroll(Scroll scroll, int tier) {
        return make(MagicItem.SPELL_SCROLL, scroll, tier, MagicItem.SPELL_SCROLL.material());
    }

    /**
     * @param scroll for a Spell Scroll, what is written on it, and its {@code tier}, 1 to 5
     * @param base   for a weapon, what it is: a sword, an axe, a bow
     */
    public ItemStack make(MagicItem item, Scroll scroll, int tier, Material base) {
        Material material = item.isWeapon() && base != null ? base : item.material();
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        Rarity rarity = item == MagicItem.SPELL_SCROLL && scroll != null ? Scroll.rarity(tier) : item.rarity();
        meta.displayName(line(rarity.colour() + title(item, scroll, material)));
        List<Component> lore = new ArrayList<>();
        lore.add(line("<dark_gray>" + rarity.title() + " magic item"));
        if (scroll != null) {
            lore.add(line("<gray>" + scroll.summary()));
            lore.add(line("<dark_gray>Tier " + Scroll.clampTier(tier) + " of " + Scroll.MOST_TIER));
        }
        for (String text : wrap(item.summary())) {
            lore.add(line("<gray>" + text));
        }
        if (item == MagicItem.WAND_OF_MAGIC_MISSILES) {
            lore.add(line("<dark_gray>Charges: " + MagicItem.WAND_CHARGES + " of " + MagicItem.WAND_CHARGES));
            ItemTags.charges(meta, MagicItem.WAND_CHARGES, clock.getAsLong());
        }
        meta.lore(lore);
        // The glint washes over a modelled item's own colours; a rune weapon keeps it, it is still the vanilla blade.
        meta.setEnchantmentGlintOverride(item.isWeapon());
        if (meta instanceof PotionMeta potion) {
            potion.setColor(switch (item) {
                case POTION_OF_HEROISM -> Color.fromRGB(0xF2C94C);
                case POTION_OF_SPEED -> Color.fromRGB(0x56CCF2);
                case POTION_OF_MACH_FUCK -> Color.fromRGB(0x5B1A8C);
                default -> Color.fromRGB(0xD7263D);
            });
        }
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(Color.fromRGB(0x6B4F2A));
        }
        ItemTags.stamp(meta, item, scroll, tier);
        stack.setItemMeta(meta);
        de.raindancer.modules.dnd.store.DndPack.itemModel(item, rarity, material)
                .ifPresent(path -> de.raindancer.modules.dnd.store.DndPack.dress(stack, path));
        if (item.isPotion()) {
            stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                    .addHiddenComponents(DataComponentTypes.POTION_CONTENTS).build());
        }
        return stack;
    }

    /** Writes a wand's charges onto it, in its tags and its lore. */
    public void charge(ItemStack wand, int charges, long chargedAt) {
        ItemMeta meta = wand.getItemMeta();
        if (meta == null) {
            return;
        }
        ItemTags.charges(meta, charges, chargedAt);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        if (!lore.isEmpty()) {
            lore.set(lore.size() - 1, line("<dark_gray>Charges: " + charges + " of " + MagicItem.WAND_CHARGES));
        }
        meta.lore(lore);
        wand.setItemMeta(meta);
    }

    private static String title(MagicItem item, Scroll scroll, Material material) {
        if (item == MagicItem.SPELL_SCROLL && scroll != null) {
            return "Spell Scroll of " + scroll.title();
        }
        if (item.isWeapon()) {
            String base = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            String[] words = base.split(" ");
            StringBuilder named = new StringBuilder();
            for (String word : words) {
                named.append(' ').append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            return "+" + item.amount().bonus() + named;
        }
        return item.title();
    }

    /** A sentence cut into lines of about thirty characters, for lore. */
    static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + word.length() > 30) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(line.length() == 0 ? "" : " ").append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static Component line(String miniMessage) {
        return MINI.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }
}
