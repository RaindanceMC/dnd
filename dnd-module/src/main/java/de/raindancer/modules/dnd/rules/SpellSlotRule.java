package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import de.raindancer.modules.dnd.model.Spell;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The full caster's slot table, which slot a spell is cast from, and Arcane Recovery. */
public final class SpellSlotRule implements IDndRule {

    /** Slots by character level (row 0 is level 1) and slot level (column 0 is 1st). */
    private static final int[][] FULL_CASTER = {
            {2, 0, 0, 0, 0, 0, 0, 0, 0},
            {3, 0, 0, 0, 0, 0, 0, 0, 0},
            {4, 2, 0, 0, 0, 0, 0, 0, 0},
            {4, 3, 0, 0, 0, 0, 0, 0, 0},
            {4, 3, 2, 0, 0, 0, 0, 0, 0},
            {4, 3, 3, 0, 0, 0, 0, 0, 0},
            {4, 3, 3, 1, 0, 0, 0, 0, 0},
            {4, 3, 3, 2, 0, 0, 0, 0, 0},
            {4, 3, 3, 3, 1, 0, 0, 0, 0},
            {4, 3, 3, 3, 2, 0, 0, 0, 0},
            {4, 3, 3, 3, 2, 1, 0, 0, 0},
            {4, 3, 3, 3, 2, 1, 0, 0, 0},
            {4, 3, 3, 3, 2, 1, 1, 0, 0},
            {4, 3, 3, 3, 2, 1, 1, 0, 0},
            {4, 3, 3, 3, 2, 1, 1, 1, 0},
            {4, 3, 3, 3, 2, 1, 1, 1, 0},
            {4, 3, 3, 3, 2, 1, 1, 1, 1},
            {4, 3, 3, 3, 3, 1, 1, 1, 1},
            {4, 3, 3, 3, 3, 2, 1, 1, 1},
            {4, 3, 3, 3, 3, 2, 2, 1, 1}};

    /** Arcane Recovery never wins back a slot above this. */
    public static final int HIGHEST_RECOVERED = 5;

    public int slots(HeroClass heroClass, int level, int slotLevel) {
        if (!heroClass.castsSpells() || slotLevel < 1 || slotLevel > 9) {
            return 0;
        }
        return FULL_CASTER[Math.max(1, Math.min(20, level)) - 1][slotLevel - 1];
    }

    public int highestSlotLevel(HeroClass heroClass, int level) {
        for (int slotLevel = 9; slotLevel >= 1; slotLevel--) {
            if (slots(heroClass, level, slotLevel) > 0) {
                return slotLevel;
            }
        }
        return 0;
    }

    public int most(Hero hero, int slotLevel) {
        return slots(hero.heroClass(), hero.level(), slotLevel);
    }

    /** Slots of that level left, counting any made from sorcery points. */
    public int available(Hero hero, int slotLevel) {
        return Math.max(0, most(hero, slotLevel) - hero.spentSlots(slotLevel));
    }

    /**
     * Which slot a spell of that level would be cast from: the lowest one still free, or with {@code upcast} the
     * highest one still free.
     */
    public Optional<Integer> pick(Hero hero, int spellLevel, boolean upcast) {
        if (spellLevel < 1) {
            return Optional.empty();
        }
        if (upcast) {
            for (int slotLevel = 9; slotLevel >= spellLevel; slotLevel--) {
                if (available(hero, slotLevel) > 0) {
                    return Optional.of(slotLevel);
                }
            }
            return Optional.empty();
        }
        for (int slotLevel = spellLevel; slotLevel <= 9; slotLevel++) {
            if (available(hero, slotLevel) > 0) {
                return Optional.of(slotLevel);
            }
        }
        return Optional.empty();
    }

    public Hero spend(Hero hero, int slotLevel) {
        return hero.withSlotSpent(slotLevel, hero.spentSlots(slotLevel) + 1);
    }

    public boolean canLearn(Hero hero, Spell spell) {
        if (!spell.learnedBy(hero.heroClass())) {
            return false;
        }
        return spell.isCantrip() || spell.level() <= highestSlotLevel(hero.heroClass(), hero.level());
    }

    public int recoveryBudget(int level) {
        return (Math.max(1, level) + 1) / 2;
    }

    /** Wins back spent slots worth at most {@code budget} levels, the highest first. */
    public Hero recover(Hero hero, int budget) {
        int left = budget;
        List<Integer> spent = new ArrayList<>(hero.slotsSpent());
        for (int slotLevel = HIGHEST_RECOVERED; slotLevel >= 1; slotLevel--) {
            while (spent.get(slotLevel) > 0 && slotLevel <= left) {
                spent.set(slotLevel, spent.get(slotLevel) - 1);
                left -= slotLevel;
            }
        }
        return hero.withSlotsSpent(spent);
    }

    /** Whether anything at all would come back. */
    public boolean wouldRecover(Hero hero) {
        return !recover(hero, recoveryBudget(hero.level())).equals(hero);
    }

    @Override
    public String describe() {
        return "how many spell slots a caster has, which one a spell uses, and what Arcane Recovery wins back";
    }
}
