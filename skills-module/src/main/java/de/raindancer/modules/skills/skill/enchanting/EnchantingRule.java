package de.raindancer.modules.skills.skill.enchanting;

import de.raindancer.modules.skills.rules.ISkillsRule;

import java.util.Random;

/** The enchanter's numbers: cheaper anvils, counted bookshelves, the table's offers, more orb experience. */
public final class EnchantingRule implements ISkillsRule {

    /** The most bookshelves a table uses. */
    public static final int MOST_SHELVES = 15;
    /** From here on survival is told "Too Expensive!" — a discount never lifts that. */
    static final int TOO_EXPENSIVE = 40;

    public int anvilCost(int cost, double cheaper) {
        if (cost <= 0 || cheaper <= 0 || cost >= TOO_EXPENSIVE) {
            return cost;
        }
        return (int) Math.max(1, Math.round(cost * (100 - Math.min(100, cheaper)) / 100));
    }

    /** What a worked item's prior-work penalty is, when nothing may raise it. */
    public int priorWork(int left, int right) {
        return Math.max(left, right);
    }

    public int shelves(int around, double more) {
        return Math.min(MOST_SHELVES, around + (int) Math.floor(Math.max(0, more)));
    }

    /**
     * The three offers' levels for so many bookshelves, as the enchanting table works them out; a slot whose cost is
     * below its own number is shut (0).
     */
    public int[] offerCosts(int shelves, Random random) {
        int[] costs = new int[3];
        for (int slot = 0; slot < 3; slot++) {
            int base = random.nextInt(8) + 1 + (shelves >> 1) + random.nextInt(shelves + 1);
            int cost = switch (slot) {
                case 0 -> Math.max(base / 3, 1);
                case 1 -> base * 2 / 3 + 1;
                default -> Math.max(base, shelves * 2);
            };
            costs[slot] = cost < slot + 1 ? 0 : cost;
        }
        return costs;
    }

    public int orbs(int amount, double more) {
        if (amount <= 0 || more <= 0) {
            return amount;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(amount * (100 + more) / 100));
    }

    @Override
    public String describe() {
        return "how much cheaper an enchanter's anvil is, and what their table offers";
    }
}
