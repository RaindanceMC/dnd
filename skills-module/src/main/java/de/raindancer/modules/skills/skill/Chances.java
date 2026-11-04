package de.raindancer.modules.skills.skill;

/** A tree's percentages as dice: {@code roll} is a uniform draw from [0, 1). */
public final class Chances {

    private Chances() {
    }

    public static boolean hit(double percent, double roll) {
        return percent > 0 && roll * 100 < percent;
    }

    /** How many times: every whole hundred surely, the rest by chance — 150% is once, and half the time twice. */
    public static int times(double percent, double roll) {
        if (percent <= 0) {
            return 0;
        }
        int sure = (int) (percent / 100);
        return sure + (hit(percent - sure * 100.0, roll) ? 1 : 0);
    }
}
