package de.raindancer.modules.skills.skill;

import java.util.Locale;

/** How an effect's line reads: a feat's as it is, the others with the value put in. */
public final class EffectText {

    private EffectText() {
    }

    public static String describe(String text, double value) {
        if (!text.contains("%s")) {
            return text;
        }
        String number = value == Math.rint(value) ? String.valueOf(Math.round(value)) : String.valueOf(value);
        return String.format(Locale.ROOT, text, number);
    }
}
