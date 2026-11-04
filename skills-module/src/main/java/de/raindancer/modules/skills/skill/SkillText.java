package de.raindancer.modules.skills.skill;

import java.util.Locale;

/** How a node's effect reads: the number put into its line, or the line alone for a feat. */
public final class SkillText {

    private SkillText() {
    }

    public static String describe(String text, boolean feat, double value) {
        if (feat) {
            return text;
        }
        String number = value == Math.rint(value) ? String.valueOf(Math.round(value)) : String.valueOf(value);
        return String.format(Locale.ROOT, text, number);
    }
}
