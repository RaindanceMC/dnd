package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Ability;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.Roll;

import java.util.random.RandomGenerator;

/** d20 tests: attack rolls against an armour class, saving throws against a DC. */
public final class CheckRule implements IDndRule {

    private final ProgressionRule progression;

    public CheckRule(ProgressionRule progression) {
        this.progression = progression;
    }

    public int d20(RandomGenerator random, boolean disadvantage) {
        int first = 1 + random.nextInt(20);
        if (!disadvantage) {
            return first;
        }
        return Math.min(first, 1 + random.nextInt(20));
    }

    public Roll attack(int die, int bonus, int armourClass) {
        return new Roll(die, bonus, armourClass, true);
    }

    public Roll save(int die, int bonus, int dc) {
        return new Roll(die, bonus, dc, false);
    }

    public int castingModifier(Hero hero) {
        return hero.heroClass().spellcasting().map(hero::modifier).orElse(0);
    }

    public int spellDc(Hero hero) {
        return 8 + progression.proficiency(hero.level()) + castingModifier(hero);
    }

    public int spellAttackBonus(Hero hero) {
        return progression.proficiency(hero.level()) + castingModifier(hero);
    }

    public int saveBonus(Hero hero, Ability ability) {
        int proficient = hero.heroClass().proficientIn(ability) ? progression.proficiency(hero.level()) : 0;
        return hero.modifier(ability) + proficient;
    }

    /**
     * Ten, plus two fifths of the armour points (full diamond is 18, like plate), plus Dexterity: all of it in light
     * armour, at most +2 in medium, none in heavy.
     */
    public int playerArmourClass(int armourPoints, int dexterityModifier) {
        int points = Math.max(0, armourPoints);
        int dexterity;
        if (points <= 7) {
            dexterity = dexterityModifier;
        } else if (points <= 15) {
            dexterity = Math.min(2, dexterityModifier);
        } else {
            dexterity = 0;
        }
        return 10 + (int) Math.round(points * 0.4) + dexterity;
    }

    @Override
    public String describe() {
        return "d20 attack rolls and saving throws, spell DCs and armour classes";
    }
}
