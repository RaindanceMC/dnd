package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Surge;
import de.raindancer.modules.dnd.model.WildSurgeTable;

/** Whether wild magic surges, and which entry of the table happens. */
public final class SurgeRule implements IDndRule {

    /** @param percentile a d100 roll, 1 to 100 */
    public boolean surges(int percentile, int chancePercent) {
        return chancePercent > 0 && percentile <= chancePercent;
    }

    /**
     * @param controlled Controlled Chaos: both rolls are read, and the kinder one happens
     */
    public Surge pick(int first, int second, boolean controlled) {
        Surge one = WildSurgeTable.entry(first);
        if (!controlled) {
            return one;
        }
        Surge two = WildSurgeTable.entry(second);
        return two.tone().ordinal() < one.tone().ordinal() ? two : one;
    }

    @Override
    public String describe() {
        return "whether a spell sets off a wild magic surge, and which";
    }
}
