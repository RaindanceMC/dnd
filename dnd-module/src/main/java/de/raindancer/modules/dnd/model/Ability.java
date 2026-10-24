package de.raindancer.modules.dnd.model;

import java.util.Locale;
import java.util.Optional;

/** The six ability scores. */
public enum Ability {
    STRENGTH("Strength", "STR"),
    DEXTERITY("Dexterity", "DEX"),
    CONSTITUTION("Constitution", "CON"),
    INTELLIGENCE("Intelligence", "INT"),
    WISDOM("Wisdom", "WIS"),
    CHARISMA("Charisma", "CHA");

    private final String title;
    private final String shortName;

    Ability(String title, String shortName) {
        this.title = title;
        this.shortName = shortName;
    }

    public String title() {
        return title;
    }

    public String shortName() {
        return shortName;
    }

    /** {@code floor((score - 10) / 2)}: 10 and 11 are +0, 8 is -1, 20 is +5. */
    public static int modifier(int score) {
        return Math.floorDiv(score - 10, 2);
    }

    public static Optional<Ability> named(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String wanted = text.trim().toUpperCase(Locale.ROOT);
        for (Ability ability : values()) {
            if (ability.name().equals(wanted) || ability.shortName.equals(wanted)) {
                return Optional.of(ability);
            }
        }
        return Optional.empty();
    }
}
