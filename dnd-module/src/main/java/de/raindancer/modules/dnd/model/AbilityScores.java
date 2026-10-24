package de.raindancer.modules.dnd.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Six scores, each kept between 1 and {@link #MOST}. */
public record AbilityScores(int strength, int dexterity, int constitution, int intelligence, int wisdom,
                            int charisma) {

    /** No ability is raised past this by an improvement. */
    public static final int MOST = 20;

    public AbilityScores {
        strength = clamp(strength);
        dexterity = clamp(dexterity);
        constitution = clamp(constitution);
        intelligence = clamp(intelligence);
        wisdom = clamp(wisdom);
        charisma = clamp(charisma);
    }

    private static int clamp(int score) {
        return Math.max(1, Math.min(30, score));
    }

    public static AbilityScores of(Map<Ability, Integer> scores) {
        return new AbilityScores(scores.getOrDefault(Ability.STRENGTH, 10),
                scores.getOrDefault(Ability.DEXTERITY, 10), scores.getOrDefault(Ability.CONSTITUTION, 10),
                scores.getOrDefault(Ability.INTELLIGENCE, 10), scores.getOrDefault(Ability.WISDOM, 10),
                scores.getOrDefault(Ability.CHARISMA, 10));
    }

    public int score(Ability ability) {
        return switch (ability) {
            case STRENGTH -> strength;
            case DEXTERITY -> dexterity;
            case CONSTITUTION -> constitution;
            case INTELLIGENCE -> intelligence;
            case WISDOM -> wisdom;
            case CHARISMA -> charisma;
        };
    }

    public int modifier(Ability ability) {
        return Ability.modifier(score(ability));
    }

    public boolean mayRaise(Ability ability) {
        return score(ability) < MOST;
    }

    /** One point more, never past {@link #MOST}. */
    public AbilityScores raised(Ability ability) {
        if (!mayRaise(ability)) {
            return this;
        }
        Map<Ability, Integer> next = asMap();
        next.put(ability, score(ability) + 1);
        return of(next);
    }

    public Map<Ability, Integer> asMap() {
        Map<Ability, Integer> map = new EnumMap<>(Ability.class);
        for (Ability ability : Ability.values()) {
            map.put(ability, score(ability));
        }
        return map;
    }

    /** {@code 15,14,13,12,10,8} in STR..CHA order, as stored. */
    public String encode() {
        List<String> parts = new ArrayList<>();
        for (Ability ability : Ability.values()) {
            parts.add(String.valueOf(score(ability)));
        }
        return String.join(",", parts);
    }

    /** Reads {@link #encode()}; anything unreadable is a 10. */
    public static AbilityScores decode(String text) {
        Map<Ability, Integer> map = new EnumMap<>(Ability.class);
        String[] parts = text == null ? new String[0] : text.split(",");
        Ability[] order = Ability.values();
        for (int index = 0; index < order.length; index++) {
            int score = 10;
            if (index < parts.length) {
                try {
                    score = Integer.parseInt(parts[index].trim());
                } catch (NumberFormatException unreadable) {
                    score = 10;
                }
            }
            map.put(order[index], score);
        }
        return of(map);
    }
}
