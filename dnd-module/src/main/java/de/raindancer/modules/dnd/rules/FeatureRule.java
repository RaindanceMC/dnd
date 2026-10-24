package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.Recharge;

/** How often a class feature can be used, how long it waits, and the numbers that grow with level. */
public final class FeatureRule implements IDndRule {

    /** Shown as "unlimited"; no adventurer gets near it. */
    public static final int UNLIMITED = 99;

    public int uses(Feature feature, int level) {
        return switch (feature.pool()) {
            case RAGE -> level >= 20 ? UNLIMITED : level >= 17 ? 6 : level >= 12 ? 5 : level >= 6 ? 4 : level >= 3 ? 3 : 2;
            case SECOND_WIND, ARCANE_RECOVERY, TIDES_OF_CHAOS, OVERCHANNEL -> 1;
            case ACTION_SURGE -> level >= 17 ? 2 : 1;
            case TURN_UNDEAD -> level >= 18 ? 3 : level >= 6 ? 2 : 1;
            case INDOMITABLE -> level >= 17 ? 3 : level >= 13 ? 2 : 1;
            default -> 0;
        };
    }

    /** Whether it is counted at all, rather than only waited for. */
    public boolean isCounted(Feature feature) {
        return feature.recharge() != Recharge.NONE;
    }

    public boolean isUnlimited(Feature feature, int level) {
        return uses(feature, level) >= UNLIMITED;
    }

    public int remaining(Hero hero, Feature feature) {
        if (!isCounted(feature)) {
            return UNLIMITED;
        }
        return Math.max(0, uses(feature, hero.level()) - hero.usedOf(feature));
    }

    public boolean mayUse(Hero hero, Feature feature) {
        if (!feature.has(hero)) {
            return false;
        }
        return !isCounted(feature) || isUnlimited(feature, hero.level()) || remaining(hero, feature) > 0;
    }

    public Hero spend(Hero hero, Feature feature) {
        if (!isCounted(feature) || isUnlimited(feature, hero.level())) {
            return hero;
        }
        return hero.withUsed(feature, hero.usedOf(feature) + 1);
    }

    public int cooldownSeconds(Feature feature) {
        return switch (feature) {
            case RECKLESS_ATTACK -> 10;
            case CUNNING_DASH -> 8;
            case CUNNING_HIDE -> 20;
            case CUNNING_DISENGAGE -> 6;
            case INTIMIDATING_PRESENCE -> 30;
            case BEND_LUCK -> 6;
            case DRAGON_WINGS -> 2;
            case DRACONIC_PRESENCE -> 60;
            default -> 0;
        };
    }

    /** The wait for this hero: a thief's Fast Hands halves the Cunning Actions'. */
    public int cooldownSeconds(Feature feature, Hero hero) {
        int seconds = cooldownSeconds(feature);
        boolean cunning = feature == Feature.CUNNING_DASH || feature == Feature.CUNNING_HIDE
                || feature == Feature.CUNNING_DISENGAGE;
        return cunning && Feature.FAST_HANDS.has(hero) ? seconds / 2 : seconds;
    }

    /** Sorcery points a feature costs each time. */
    public int sorceryCost(Feature feature) {
        return switch (feature) {
            case BEND_LUCK -> 2;
            case DRACONIC_PRESENCE -> 5;
            default -> 0;
        };
    }

    public int rageDamage(int level) {
        return level >= 16 ? 4 : level >= 9 ? 3 : 2;
    }

    public int sneakAttackDice(int level) {
        return (Math.max(1, level) + 1) / 2;
    }

    /** What Extra Attack adds to a fighter's melee blow, as a fraction. */
    public double extraAttackBonus(int level) {
        return level >= 20 ? 0.75 : level >= 11 ? 0.5 : level >= 5 ? 0.25 : 0.0;
    }

    /** Destroy Undead: undead worth at most this much experience die outright. 0 before level 5. */
    public int destroysUndeadUpTo(int level) {
        return level >= 17 ? 1_100 : level >= 14 ? 700 : level >= 11 ? 450 : level >= 8 ? 200 : level >= 5 ? 100 : 0;
    }

    @Override
    public String describe() {
        return "uses, waits and growing numbers of the class features";
    }
}
