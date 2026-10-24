package de.raindancer.modules.dnd.rules;

/** Keeping a concentration spell up while being hit. */
public final class ConcentrationRule implements IDndRule {

    public int dc(double damage) {
        return Math.max(10, (int) Math.floor(Math.max(0, damage) / 2));
    }

    @Override
    public String describe() {
        return "the Constitution save that keeps a concentration spell going after a hit";
    }
}
