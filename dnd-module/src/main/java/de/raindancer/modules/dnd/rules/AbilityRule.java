package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.AbilityScores;
import de.raindancer.modules.dnd.model.Hero;

/**
 * Ability scores when an owner has switched them off: nobody sees or spends them, but every rule still reads a
 * modifier — so each class keeps its starting scores, and its main ability rises by itself where a player would
 * have spent the points.
 *
 * <p>What is stored never changes: the rules are shown the derived scores, and a change made in the meantime is
 * written back over the stored ones. Points spent before stay spent for when the setting is switched back on.
 */
public final class AbilityRule implements IDndRule {

    private final ProgressionRule progression;

    public AbilityRule(ProgressionRule progression) {
        this.progression = progression;
    }

    public AbilityScores simple(Hero hero) {
        AbilityScores scores = hero.heroClass().startingScores();
        int raises = progression.improvementPoints(1, hero.level());
        for (int i = 0; i < raises; i++) {
            scores = scores.raised(hero.heroClass().primary());
        }
        return scores;
    }

    /** The hero as the rules see it. */
    public Hero shown(Hero stored, boolean abilityScores) {
        return abilityScores ? stored : stored.withScores(simple(stored), stored.improvements());
    }

    /** What to store after a change made to {@link #shown}. */
    public Hero kept(Hero stored, Hero changed, boolean abilityScores) {
        return abilityScores ? changed : changed.withScores(stored.scores(), changed.improvements());
    }

    @Override
    public String describe() {
        return "ability scores when they are switched off: fixed per class, the main one rising by itself";
    }
}
