package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.MagicItem;
import de.raindancer.modules.dnd.model.Rarity;
import de.raindancer.modules.dnd.model.Spell;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** Whether a kill drops a magic item, how rare, and which. */
public final class LootRule implements IDndRule {

    /** What dropped: an item, and for a scroll the spell on it. */
    public record Drop(MagicItem item, Spell spell) {
    }

    /** Experience at which a creature counts as a boss and always drops something. */
    public static final int BOSS_XP = 1_800;

    public double dropChance(int xp, int percent) {
        if (xp <= 0 || percent <= 0) {
            return 0.0;
        }
        if (xp >= BOSS_XP) {
            return 1.0;
        }
        double base = Math.min(0.25, 0.02 + xp / 4_000.0);
        return Math.min(1.0, base * percent / 100.0);
    }

    /** @param roll uniform in 0 to 1 */
    public Rarity rarity(int xp, double roll) {
        double[] weights;
        if (xp >= BOSS_XP) {
            weights = new double[]{0, 0, 50, 35, 15};
        } else if (xp >= 450) {
            weights = new double[]{0, 40, 45, 15, 0};
        } else if (xp >= 200) {
            weights = new double[]{30, 50, 20, 0, 0};
        } else if (xp >= 100) {
            weights = new double[]{60, 35, 5, 0, 0};
        } else {
            weights = new double[]{80, 18, 2, 0, 0};
        }
        double total = 0;
        for (double weight : weights) {
            total += weight;
        }
        double left = Math.max(0, Math.min(0.999999, roll)) * total;
        Rarity[] rarities = Rarity.values();
        for (int index = 0; index < weights.length; index++) {
            if (left < weights[index]) {
                return rarities[index];
            }
            left -= weights[index];
        }
        return Rarity.COMMON;
    }

    /** Something of that rarity. A scroll is one choice among the items, and carries a spell of that rarity. */
    public Drop item(Rarity rarity, RandomGenerator random) {
        List<MagicItem> items = new ArrayList<>();
        for (MagicItem item : MagicItem.values()) {
            if (item != MagicItem.SPELL_SCROLL && item.rarity() == rarity) {
                items.add(item);
            }
        }
        List<Spell> spells = new ArrayList<>();
        for (Spell spell : Spell.values()) {
            if (Rarity.ofSpellLevel(spell.level()) == rarity) {
                spells.add(spell);
            }
        }
        int choices = items.size() + (spells.isEmpty() ? 0 : 1);
        int pick = random.nextInt(Math.max(1, choices));
        if (pick < items.size()) {
            return new Drop(items.get(pick), null);
        }
        return new Drop(MagicItem.SPELL_SCROLL, spells.get(random.nextInt(spells.size())));
    }

    @Override
    public String describe() {
        return "whether a kill drops a magic item, how rare, and which";
    }
}
