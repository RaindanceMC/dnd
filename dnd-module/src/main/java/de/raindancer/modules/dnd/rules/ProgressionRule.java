package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Hero;

/**
 * Experience and levels, 1 to {@value Hero#MOST_LEVEL}. Levels 1 to 20 keep the table's thresholds, so a hero from
 * before the skill tree is the level they were; past 20 every level costs {@value #PAST_TWENTY} more.
 */
public final class ProgressionRule implements IDndRule {

    private static final long[] THRESHOLDS = {0, 300, 900, 2_700, 6_500, 14_000, 23_000, 34_000, 48_000, 64_000,
            85_000, 100_000, 120_000, 140_000, 165_000, 195_000, 225_000, 265_000, 305_000, 355_000};

    /** Experience per level past the twentieth. */
    public static final long PAST_TWENTY = 25_000;

    public long xpFor(int level) {
        int wanted = Math.max(1, Math.min(Hero.MOST_LEVEL, level));
        if (wanted <= THRESHOLDS.length) {
            return THRESHOLDS[wanted - 1];
        }
        return THRESHOLDS[THRESHOLDS.length - 1] + (wanted - THRESHOLDS.length) * PAST_TWENTY;
    }

    public int levelFor(long xp) {
        if (xp >= THRESHOLDS[THRESHOLDS.length - 1]) {
            long past = (xp - THRESHOLDS[THRESHOLDS.length - 1]) / PAST_TWENTY;
            return (int) Math.min(Hero.MOST_LEVEL, THRESHOLDS.length + past);
        }
        int level = 1;
        for (int index = 0; index < THRESHOLDS.length; index++) {
            if (xp >= THRESHOLDS[index]) {
                level = index + 1;
            }
        }
        return level;
    }

    /** How far through the current level, 0 to 1. Full at the top level. */
    public double progress(long xp) {
        int level = levelFor(xp);
        if (level >= Hero.MOST_LEVEL) {
            return 1.0;
        }
        long from = xpFor(level);
        long to = xpFor(level + 1);
        return Math.max(0.0, Math.min(1.0, (xp - from) / (double) (to - from)));
    }

    /** A hero with that much more experience; their level follows, and never goes down. */
    public Hero grown(Hero hero, long gained) {
        long xp = Math.max(0, hero.xp() + Math.max(0, gained));
        return hero.withLevel(Math.max(hero.level(), levelFor(xp)), xp);
    }

    /** A hero put at exactly that level, at the experience it starts with. */
    public Hero atLevel(Hero hero, int level) {
        int wanted = Math.max(1, Math.min(Hero.MOST_LEVEL, level));
        return hero.withLevel(wanted, xpFor(wanted));
    }

    @Override
    public String describe() {
        return "levels from experience, 1 to " + Hero.MOST_LEVEL;
    }
}
