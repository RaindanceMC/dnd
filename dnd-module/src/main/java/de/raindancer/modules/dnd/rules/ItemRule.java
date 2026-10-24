package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.MagicItem;
import de.raindancer.modules.dnd.model.Spell;

/** The numbers magic items work by. */
public final class ItemRule implements IDndRule {

    private static final int[] SCROLL_DC = {13, 13, 13, 15, 15, 17, 17, 18, 18, 19};
    private static final int[] SCROLL_ATTACK = {5, 5, 5, 7, 7, 9, 9, 10, 10, 11};

    public int scrollDc(int spellLevel) {
        return SCROLL_DC[Math.max(0, Math.min(9, spellLevel))];
    }

    public int scrollAttack(int spellLevel) {
        return SCROLL_ATTACK[Math.max(0, Math.min(9, spellLevel))];
    }

    /** A scroll can be read by the classes whose list holds its spell, and by a thief with Use Magic Device. */
    public boolean mayRead(Hero hero, Spell spell) {
        if (hero == null || spell == null) {
            return false;
        }
        return spell.learnedBy(hero.heroClass()) || Feature.USE_MAGIC_DEVICE.has(hero);
    }

    /** A wand's charges now, given what it held when it was last charged and when that was. */
    public int charges(int stored, long chargedAt, long now) {
        if (stored >= MagicItem.WAND_CHARGES) {
            return MagicItem.WAND_CHARGES;
        }
        long regained = Math.max(0, now - chargedAt) / MagicItem.WAND_RECHARGE_MILLIS;
        return (int) Math.min(MagicItem.WAND_CHARGES, stored + regained);
    }

    /** When the charges counted by {@link #charges} were complete, so the part-way time is not lost. */
    public long chargedAt(int stored, long chargedAt, long now) {
        long regained = Math.max(0, now - chargedAt) / MagicItem.WAND_RECHARGE_MILLIS;
        if (stored + regained >= MagicItem.WAND_CHARGES) {
            return now;
        }
        return chargedAt + regained * MagicItem.WAND_RECHARGE_MILLIS;
    }

    @Override
    public String describe() {
        return "scroll DCs, who may read a scroll, and wand charges";
    }
}
