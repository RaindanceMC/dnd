package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.MagicItem;

/** The numbers magic items work by: scroll strength and wand charges. */
public final class ItemRule implements IDndRule {

    /** Damage a scroll or wand of that tier does, before its kind's own share and Spellbreaker. */
    public double power(int tier, int percent) {
        int clamped = Math.max(1, Math.min(5, tier));
        return (2.0 + 2.0 * clamped) * Math.max(0, 100 + percent) / 100.0;
    }

    /** How long one charge takes to come back, with Wandwright's percent faster. */
    public long rechargeMillis(int fasterPercent) {
        return Math.max(1_000L, MagicItem.WAND_RECHARGE_MILLIS * 100 / (100 + Math.max(0, fasterPercent)));
    }

    /** A wand's charges now, given what it held when it was last charged and when that was. */
    public int charges(int stored, long chargedAt, long now, long every) {
        if (stored >= MagicItem.WAND_CHARGES) {
            return MagicItem.WAND_CHARGES;
        }
        long regained = Math.max(0, now - chargedAt) / Math.max(1, every);
        return (int) Math.min(MagicItem.WAND_CHARGES, stored + regained);
    }

    /** When the charges counted by {@link #charges} were complete, so the part-way time is not lost. */
    public long chargedAt(int stored, long chargedAt, long now, long every) {
        long regained = Math.max(0, now - chargedAt) / Math.max(1, every);
        if (stored + regained >= MagicItem.WAND_CHARGES) {
            return now;
        }
        return chargedAt + regained * Math.max(1, every);
    }

    @Override
    public String describe() {
        return "scroll strength and wand charges";
    }
}
