package de.raindancer.modules.skills.skill.alchemy;

import de.raindancer.modules.skills.rules.ISkillsRule;
import de.raindancer.modules.skills.skill.Chances;

/** Alchemy's numbers: a brew's mark, how long a marked potion lasts, how fast a stand brews, where a spare goes. */
public final class AlchemyRule implements ISkillsRule {

    private static final int QUICKEST = 20;
    /** Bottomless Flask: one in three. */
    public static final double BOTTOMLESS = 1 / 3.0;

    /** What an alchemist put into a potion: {@code longer} percent longer, {@code stronger} levels stronger. */
    public record Mark(int longer, int stronger) {

        public static final Mark NONE = new Mark(0, 0);

        public boolean plain() {
            return longer <= 0 && stronger <= 0;
        }
    }

    public Mark mark(double durationPercent, double potencyPercent, double roll) {
        int longer = (int) Math.round(Math.max(0, durationPercent));
        int stronger = Chances.hit(potencyPercent, roll) ? 1 : 0;
        return longer == 0 && stronger == 0 ? Mark.NONE : new Mark(longer, stronger);
    }

    public int lasting(int ticks, int longerPercent) {
        return longerPercent <= 0 ? ticks : (int) Math.round(ticks * (1 + longerPercent / 100.0));
    }

    public int brewTime(int ticks, double percent) {
        if (percent <= 0) {
            return ticks;
        }
        return Math.min(ticks, Math.max(QUICKEST, (int) Math.round(ticks * (1 - percent / 100))));
    }

    /** The first empty bottle slot, when there is a brewed potion beside it to copy; -1 otherwise. */
    public int emptySlot(boolean[] bottles) {
        if (fullSlot(bottles) < 0) {
            return -1;
        }
        for (int slot = 0; slot < bottles.length; slot++) {
            if (!bottles[slot]) {
                return slot;
            }
        }
        return -1;
    }

    public int fullSlot(boolean[] bottles) {
        for (int slot = 0; slot < bottles.length; slot++) {
            if (bottles[slot]) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public String describe() {
        return "Alchemy: a brew's mark, how long a marked potion lasts, how fast a stand brews";
    }
}
