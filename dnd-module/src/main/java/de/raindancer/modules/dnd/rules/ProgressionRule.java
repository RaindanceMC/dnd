package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Hero;

import java.util.Set;

/** Experience, levels, the proficiency bonus and when ability scores improve. */
public final class ProgressionRule implements IDndRule {

    private static final long[] THRESHOLDS = {0, 300, 900, 2_700, 6_500, 14_000, 23_000, 34_000, 48_000, 64_000,
            85_000, 100_000, 120_000, 140_000, 165_000, 195_000, 225_000, 265_000, 305_000, 355_000};

    private static final Set<Integer> IMPROVEMENT_LEVELS = Set.of(4, 8, 12, 16, 19);

    /** Points per Ability Score Improvement: two, to put on one ability or split over two. */
    public static final int POINTS_PER_IMPROVEMENT = 2;

    public int levelFor(long xp) {
        int level = 1;
        for (int index = 0; index < THRESHOLDS.length; index++) {
            if (xp >= THRESHOLDS[index]) {
                level = index + 1;
            }
        }
        return level;
    }

    public long xpFor(int level) {
        int index = Math.max(1, Math.min(THRESHOLDS.length, level)) - 1;
        return THRESHOLDS[index];
    }

    public int proficiency(int level) {
        return 2 + (Math.max(1, Math.min(20, level)) - 1) / 4;
    }

    /** Ability points earned by going from one level to a higher one. */
    public int improvementPoints(int fromLevel, int toLevel) {
        int points = 0;
        for (int level = fromLevel + 1; level <= toLevel; level++) {
            if (IMPROVEMENT_LEVELS.contains(level)) {
                points += POINTS_PER_IMPROVEMENT;
            }
        }
        return points;
    }

    public int cantripDice(int level) {
        int dice = 1;
        if (level >= 5) {
            dice++;
        }
        if (level >= 11) {
            dice++;
        }
        if (level >= 17) {
            dice++;
        }
        return dice;
    }

    /** How far through the current level, 0 to 1. Full at level 20. */
    public double progress(long xp) {
        int level = levelFor(xp);
        if (level >= 20) {
            return 1.0;
        }
        long from = xpFor(level);
        long to = xpFor(level + 1);
        return Math.max(0.0, Math.min(1.0, (xp - from) / (double) (to - from)));
    }

    /** A hero with that much more experience: its level and its ability points follow. Never goes down. */
    public Hero grown(Hero hero, long gained) {
        long xp = Math.max(0, hero.xp() + Math.max(0, gained));
        int level = Math.max(hero.level(), levelFor(xp));
        return hero.withLevel(level, xp, hero.improvements() + improvementPoints(hero.level(), level));
    }

    /** A hero put at exactly that level, at the experience it starts with; points for every level crossed upward. */
    public Hero atLevel(Hero hero, int level) {
        int wanted = Math.max(1, Math.min(20, level));
        int points = wanted > hero.level() ? improvementPoints(hero.level(), wanted) : 0;
        return hero.withLevel(wanted, xpFor(wanted), hero.improvements() + points);
    }

    @Override
    public String describe() {
        return "levels from experience, the proficiency bonus and ability score improvements";
    }
}
