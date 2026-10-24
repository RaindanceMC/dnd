package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Ability;
import de.raindancer.modules.dnd.model.Hero;

/**
 * Hit points. Level 1 is Minecraft's own twenty; every level after adds what the table would — the average of the hit
 * die plus Constitution — scaled down by a setting, since twenty-level adventurers would otherwise wear a wall of
 * hearts.
 */
public final class HealthRule implements IDndRule {

    public int perLevel(Hero hero) {
        int resilience = de.raindancer.modules.dnd.model.Feature.DRACONIC_RESILIENCE.has(hero) ? 1 : 0;
        return Math.max(1, hero.heroClass().hitDie() / 2 + 1 + hero.modifier(Ability.CONSTITUTION)) + resilience;
    }

    public double bonusHealth(Hero hero, int scalePercent) {
        return (hero.level() - 1) * perLevel(hero) * Math.max(0, scalePercent) / 100.0;
    }

    /** What one hit die heals on a short rest: the average roll plus Constitution. */
    public int perHitDie(Hero hero) {
        return perLevel(hero);
    }

    public int hitDiceBack(int level) {
        return Math.max(1, level / 2);
    }

    @Override
    public String describe() {
        return "maximum health from level and Constitution, and what a hit die heals";
    }
}
