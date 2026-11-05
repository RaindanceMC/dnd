package de.raindancer.modules.skills.skill.gathering;

import java.util.Locale;

/** How a gathering effect's line reads: a feat without a number, the rest with the value put in. */
public final class EffectText {

    private EffectText() {
    }

    public static String describe(String text, double value, boolean feat) {
        if (feat) {
            return text;
        }
        String number = value == Math.rint(value) ? String.valueOf(Math.round(value)) : String.valueOf(value);
        return String.format(Locale.ROOT, text, number);
    }
}
