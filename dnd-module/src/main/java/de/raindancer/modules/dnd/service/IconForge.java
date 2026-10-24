package de.raindancer.modules.dnd.service;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.Castable;
import de.raindancer.modules.dnd.model.Dice;
import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.Metamagic;
import de.raindancer.modules.dnd.model.Spell;
import de.raindancer.modules.dnd.rules.DamageRule;
import de.raindancer.modules.dnd.rules.FeatureRule;
import de.raindancer.modules.dnd.rules.SpellSlotRule;
import de.raindancer.modules.dnd.store.IconTags;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Draws spells and features as items: for the stance's hotbar and for the spellbook. */
public final class IconForge implements IDndService {

    private static final int WRAP = 38;

    private final SpellSlotRule slots;
    private final FeatureRule features;
    private final DamageRule damage;

    public IconForge(SpellSlotRule slots, FeatureRule features, DamageRule damage) {
        this.slots = slots;
        this.features = features;
        this.damage = damage;
    }

    @Override
    public void settings(DndSettings settings) {
        // Drawing reads nothing configurable.
    }

    public static String ordinal(int level) {
        return switch (level) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            default -> level + "th";
        };
    }

    /** What a castable looks like for this hero, with what is left of it. */
    public ItemStack of(Castable castable, Hero hero, List<String> footer) {
        List<String> lore = new ArrayList<>();
        int amount = 1;
        String colour;
        if (castable instanceof Spell spell) {
            colour = spell.isCantrip() ? "<aqua>" : "<light_purple>";
            lore.add(spell.isCantrip() ? "<gray>Cantrip, cast at will"
                    : "<gray>" + ordinal(spell.level()) + "-level spell");
            if (!spell.isCantrip()) {
                int left = 0;
                for (int level = spell.level(); level <= 9; level++) {
                    left += slots.available(hero, level);
                }
                amount = Math.max(1, Math.min(64, left));
                lore.add(left > 0 ? "<gray>Slots of " + ordinal(spell.level()) + " or higher left: <white>" + left
                        : "<red>No slot high enough left. Rest to win them back.");
            }
            List<Dice> dice = damage.dice(spell, Math.max(spell.level(), 1), hero.level());
            if (!dice.isEmpty() && spell.kind() != de.raindancer.modules.dnd.model.DamageKind.NONE) {
                List<String> parts = new ArrayList<>();
                dice.forEach(one -> parts.add(one.toString()));
                String kind = spell.kind().name().toLowerCase(Locale.ROOT);
                lore.add("<dark_gray>" + String.join(" + ", parts) + " " + kind
                        + spell.save().map(ability -> ", " + ability.title() + " save").orElse(""));
            }
            if (spell.concentration()) {
                lore.add("<dark_gray>Concentration");
            }
        } else if (castable instanceof Feature feature) {
            colour = "<gold>";
            lore.add("<gray>" + feature.heroClass().title() + " feature");
            if (features.isCounted(feature)) {
                int left = features.remaining(hero, feature);
                if (features.isUnlimited(feature, hero.level())) {
                    lore.add("<gray>Uses: <white>unlimited");
                } else {
                    amount = Math.max(1, Math.min(64, left));
                    lore.add(left > 0 ? "<gray>Uses left: <white>" + left + "</white> of "
                            + features.uses(feature, hero.level())
                            : "<red>None left. " + (feature.recharge() == de.raindancer.modules.dnd.model.Recharge.SHORT_REST
                            ? "A short rest brings it back." : "A long rest brings it back."));
                }
            } else if (features.cooldownSeconds(feature) > 0) {
                lore.add("<gray>Wait afterwards: <white>" + features.cooldownSeconds(feature) + "s");
            }
        } else {
            colour = "<white>";
        }
        if (castable.bonusAction()) {
            lore.add("<dark_gray>Bonus action");
        }
        lore.add("");
        for (String line : wrap(castable.summary())) {
            lore.add("<gray>" + line);
        }
        if (!footer.isEmpty()) {
            lore.add("");
            lore.addAll(footer);
        }
        ItemStack stack = Icons.of(castable.icon(), colour + castable.title(), lore);
        stack.setAmount(amount);
        hideExtras(stack);
        return stack;
    }

    public ItemStack stanceIcon(Castable castable, Hero hero, int slot) {
        List<String> footer = new ArrayList<>();
        footer.add("<yellow>Click<gray> to cast" + (castable instanceof Feature ? " (use)" : ""));
        if (castable instanceof Spell spell && !spell.isCantrip()) {
            footer.add("<yellow>Sneak and click<gray> to cast with your highest slot");
        }
        ItemStack stack = of(castable, hero, footer);
        IconTags.stamp(stack, castable.id());
        return stack;
    }

    public ItemStack emptySlot(int slot) {
        ItemStack stack = Icons.of(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>Empty slot " + (slot + 1),
                List.of("<gray>Put a spell or feature here from", "<gray>your spellbook: click slot 9."));
        IconTags.stamp(stack, IconTags.EMPTY);
        return stack;
    }

    /** The ninth slot. A sorcerer with metamagic sees what is armed; everybody else their sheet. */
    public ItemStack sheetSlot(Hero hero, boolean metamagic, Metamagic armed) {
        ItemStack stack;
        if (metamagic) {
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Sorcery points: <white>" + hero.sorceryPoints());
            if (armed != null) {
                lore.add("<gray>Bends your next spell, for <white>" + armed.cost(1)
                        + (armed == Metamagic.TWINNED ? "</white> point per spell level" : "</white> point(s)"));
                wrap(armed.summary()).forEach(line -> lore.add("<dark_gray>" + line));
            } else {
                lore.add("<gray>Your next spell is cast as it is.");
            }
            lore.add("");
            lore.add("<yellow>Click<gray> for the next metamagic");
            lore.add("<yellow>Sneak and click<gray> for your character sheet");
            lore.add("<yellow>F<gray> leaves the stance");
            stack = Icons.of(armed == null ? Material.BREWING_STAND : armed.icon(),
                    armed == null ? "<light_purple>Metamagic: none" : "<light_purple>Metamagic: " + armed.title(),
                    lore);
        } else {
            stack = Icons.of(Material.WRITABLE_BOOK, "<gold>" + hero.heroClass().title() + ", level " + hero.level(),
                    List.of("<yellow>Click<gray> for your character sheet", "<yellow>F<gray> leaves the stance"));
        }
        hideExtras(stack);
        IconTags.stamp(stack, IconTags.SHEET);
        return stack;
    }

    private static void hideExtras(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            stack.setItemMeta(meta);
        }
    }

    static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + word.length() + 1 > WRAP) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }
}
