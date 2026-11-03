package de.raindancer.modules.skills.model;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What a Spell Scroll does when read — and what a Wand of Magic Missiles fires. A scroll has a kind and a tier, 1 to
 * 5, which is its rarity and how strong it is.
 */
public enum Scroll {

    MAGIC_MISSILE("Magic Missile", DamageKind.FORCE, "Three darts of force at what you look at."),
    FIREBALL("Fireball", DamageKind.FIRE, "A burst of flame where you look; it burns all around."),
    LIGHTNING("Lightning", DamageKind.LIGHTNING, "A bolt from the sky onto what you look at."),
    HEALING("Healing", DamageKind.HEALING, "Heals you and every player near you."),
    MISTY_STEP("Misty Step", DamageKind.NONE, "Steps you to where you look, through the air."),
    SHIELD("Shield", DamageKind.NONE, "Golden hearts and harder skin, for a while.");

    public static final int MOST_TIER = 5;

    /** Scrolls written before 0.11.0 carry a spell: what each became, and its tier. */
    private static final Map<String, Legacy> LEGACY = legacy();

    private record Legacy(Scroll scroll, int tier) {
    }

    private final String title;
    private final DamageKind kind;
    private final String summary;

    Scroll(String title, DamageKind kind, String summary) {
        this.title = title;
        this.kind = kind;
        this.summary = summary;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String title() {
        return title;
    }

    public DamageKind kind() {
        return kind;
    }

    public String summary() {
        return summary;
    }

    public static int clampTier(int tier) {
        return Math.max(1, Math.min(MOST_TIER, tier));
    }

    /** The rarity a scroll of that tier has: 1 common … 5 legendary. */
    public static Rarity rarity(int tier) {
        return Rarity.values()[clampTier(tier) - 1];
    }

    /** The tier of a scroll of that rarity. */
    public static int tierOf(Rarity rarity) {
        return rarity.ordinal() + 1;
    }

    public static Optional<Scroll> byId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(id.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    /** What an old scroll's spell became; any spell this does not know reads as a first-tier Magic Missile. */
    public static Scroll legacyKind(String spell) {
        Legacy known = spell == null ? null : LEGACY.get(spell.strip().toLowerCase(Locale.ROOT));
        return known == null ? MAGIC_MISSILE : known.scroll();
    }

    public static int legacyTier(String spell) {
        Legacy known = spell == null ? null : LEGACY.get(spell.strip().toLowerCase(Locale.ROOT));
        return known == null ? 1 : known.tier();
    }

    private static Map<String, Legacy> legacy() {
        Map<String, Legacy> map = new java.util.HashMap<>();
        Object[][] table = {
                {MAGIC_MISSILE, 1, "fire_bolt", "ray_of_frost", "shocking_grasp", "sacred_flame", "magic_missile",
                        "guiding_bolt", "inflict_wounds"},
                {FIREBALL, 1, "burning_hands", "thunderwave"},
                {SHIELD, 1, "shield", "mage_armor", "bless", "shield_of_faith"},
                {MISTY_STEP, 1, "expeditious_retreat", "feather_fall", "jump"},
                {HEALING, 1, "cure_wounds", "healing_word"},
                {MAGIC_MISSILE, 2, "scorching_ray", "hold_person"},
                {MISTY_STEP, 2, "misty_step", "invisibility"},
                {FIREBALL, 2, "shatter"},
                {HEALING, 2, "lesser_restoration", "aid"},
                {FIREBALL, 3, "fireball", "spirit_guardians"},
                {LIGHTNING, 3, "lightning_bolt"},
                {MISTY_STEP, 3, "haste"},
                {HEALING, 3, "prayer_of_healing", "mass_healing_word"},
                {FIREBALL, 4, "ice_storm", "cone_of_cold", "flame_strike"},
                {MISTY_STEP, 4, "greater_invisibility", "dimension_door"},
                {SHIELD, 4, "death_ward"},
                {HEALING, 4, "mass_cure_wounds", "greater_restoration"},
                {LIGHTNING, 5, "chain_lightning", "disintegrate", "harm", "finger_of_death", "power_word_kill"},
                {FIREBALL, 5, "fire_storm", "sunburst", "meteor_swarm"},
                {HEALING, 5, "heal", "regenerate", "mass_heal"},
                {SHIELD, 5, "holy_aura"},
        };
        for (Object[] row : table) {
            for (int index = 2; index < row.length; index++) {
                map.put((String) row[index], new Legacy((Scroll) row[0], (Integer) row[1]));
            }
        }
        return Map.copyOf(map);
    }
}
