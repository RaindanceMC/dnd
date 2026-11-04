package de.raindancer.modules.skills.skill.trading;

import de.raindancer.modules.skills.rules.ISkillsRule;

/** The trader's numbers: how much a discount takes off, what demand added, when a used-up trade comes back. */
public final class TradingRule implements ISkillsRule {

    /** Twenty minutes, in ticks: a Minecraft day. */
    static final long RESTOCK_TICKS = 24_000;

    /**
     * How much to take off a price: {@code percent} of its base, rounded down, never leaving less than one.
     *
     * @param adjusted what the price is now, with demand and reputation in it
     */
    public int discount(int base, int adjusted, double percent) {
        if (percent <= 0 || adjusted <= 1) {
            return 0;
        }
        return Math.max(0, Math.min((int) Math.floor(base * percent / 100), adjusted - 1));
    }

    /** What demand adds to a price — vanilla's {@code max(0, floor(base × demand × multiplier))}. */
    public int demandBump(int base, int demand, float multiplier) {
        return Math.max(0, (int) Math.floor(base * demand * multiplier));
    }

    /** How long a used-up trade waits to come back; 0 for never. */
    public long restockTicks(double sooner) {
        if (sooner <= 0) {
            return 0;
        }
        return Math.round(RESTOCK_TICKS * 100 / (100 + sooner));
    }

    public boolean paysEmeralds(String result) {
        return "emerald".equals(result);
    }

    @Override
    public String describe() {
        return "how much a trader takes off a price, and when a used-up trade comes back";
    }
}
