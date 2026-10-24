package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Ability;
import de.raindancer.modules.dnd.model.Dice;
import de.raindancer.modules.dnd.model.Spell;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** What a spell rolls, and what is left of it after a saving throw. */
public final class DamageRule implements IDndRule {

    private static final double MINIMUM = 0.5;

    private final ProgressionRule progression;

    public DamageRule(ProgressionRule progression) {
        this.progression = progression;
    }

    /** Darts, rays and jumps grow in number with the slot; everything else keeps its count. */
    public int count(Spell spell, int slotLevel) {
        int above = Math.max(0, slotLevel - spell.level());
        return switch (spell) {
            case MAGIC_MISSILE, SCORCHING_RAY, CHAIN_LIGHTNING -> spell.count() + above;
            default -> spell.count();
        };
    }

    /** The dice one hit rolls: the spell's own plus its upcast, and the second kind of damage if it has one. */
    public List<Dice> dice(Spell spell, int slotLevel, int characterLevel) {
        List<Dice> dice = new ArrayList<>();
        Dice main = spell.damage();
        if (spell.isCantrip()) {
            main = main.times(progression.cantripDice(characterLevel));
        } else {
            int above = Math.max(0, slotLevel - spell.level());
            Dice upcast = spell.upcast();
            if (!upcast.isNone() && above > 0) {
                main = new Dice(main.count() + upcast.count() * above, main.sides(),
                        main.bonus() + upcast.bonus() * above);
            }
        }
        if (!main.isNone()) {
            dice.add(main);
        }
        if (!spell.extra().isNone()) {
            dice.add(spell.extra());
        }
        return dice;
    }

    /**
     * How the dice are rolled.
     *
     * @param doubled   a critical hit: every die twice
     * @param rerollLow Empowered Spell: any die below half rolled again, once
     * @param bombard   Spell Bombardment: the first die to show its highest is rolled again and added
     * @param highest   every die shows its highest — Overchannel, Supreme Healing
     */
    public record Rolling(boolean doubled, boolean rerollLow, boolean bombard, boolean highest) {

        public static final Rolling PLAIN = new Rolling(false, false, false, false);

        public Rolling critical(boolean on) {
            return new Rolling(on, rerollLow, bombard, highest);
        }

        public Rolling empowered(boolean on) {
            return new Rolling(doubled, on, bombard, highest);
        }

        public Rolling bombarding() {
            return bombarding(true);
        }

        public Rolling bombarding(boolean on) {
            return new Rolling(doubled, rerollLow, on, highest);
        }

        public Rolling maxed() {
            return maxed(true);
        }

        public Rolling maxed(boolean on) {
            return new Rolling(doubled, rerollLow, bombard, on);
        }
    }

    public int roll(List<Dice> dice, boolean critical, boolean empowered, RandomGenerator random) {
        return roll(dice, Rolling.PLAIN.critical(critical).empowered(empowered), random);
    }

    public int roll(List<Dice> dice, Rolling how, RandomGenerator random) {
        int total = 0;
        boolean bombarded = false;
        for (Dice one : dice) {
            int count = how.doubled() ? one.count() * 2 : one.count();
            for (int die = 0; die < count; die++) {
                int face = how.highest() ? one.sides() : face(one.sides(), random);
                if (!how.highest() && how.rerollLow() && face < (one.sides() + 1) / 2.0) {
                    face = face(one.sides(), random);
                }
                if (how.bombard() && !bombarded && one.sides() > 0 && face == one.sides()) {
                    bombarded = true;
                    face += face(one.sides(), random);
                }
                total += face;
            }
            total += one.bonus();
        }
        return Math.max(0, total);
    }

    private static int face(int sides, RandomGenerator random) {
        return sides <= 0 ? 0 : 1 + random.nextInt(sides);
    }

    /**
     * What is left once the target has rolled its save.
     *
     * @param evasion the target is a rogue with Evasion: on a Dexterity save, success is nothing and failure half
     */
    public int afterSave(int damage, Spell spell, boolean saved, boolean evasion) {
        boolean dexterity = spell.save().filter(ability -> ability == Ability.DEXTERITY).isPresent();
        if (evasion && dexterity && spell.onSave() == Spell.OnSave.HALF) {
            return saved ? 0 : damage / 2;
        }
        if (!saved) {
            return damage;
        }
        return spell.onSave() == Spell.OnSave.HALF ? damage / 2 : 0;
    }

    /**
     * Healing from a spell. The dice-and-modifier spells add the caster's modifier; the fixed ones do not.
     *
     * @param discipleOfLife a Life cleric's healing spells heal two more plus the slot level
     */
    public int healing(Spell spell, int slotLevel, int castingModifier, boolean discipleOfLife,
                       RandomGenerator random) {
        return healing(spell, slotLevel, castingModifier, discipleOfLife, false, random);
    }

    /** @param supreme Supreme Healing: the dice roll their highest */
    public int healing(Spell spell, int slotLevel, int castingModifier, boolean discipleOfLife, boolean supreme,
                       RandomGenerator random) {
        List<Dice> dice = dice(spell, slotLevel, 1);
        int healed = roll(dice, Rolling.PLAIN.maxed(supreme), random);
        if (spell.damage().count() > 0 && spell.damage().bonus() == 0) {
            healed += castingModifier;
        }
        if (discipleOfLife && slotLevel >= 1) {
            healed += 2 + slotLevel;
        }
        return Math.max(0, healed);
    }

    /** Scaled by a percentage setting, but a hit that landed always does at least half a heart. */
    public double scaled(int amount, int percent) {
        if (amount <= 0 || percent <= 0) {
            return 0.0;
        }
        return Math.max(MINIMUM, amount * percent / 100.0);
    }

    @Override
    public String describe() {
        return "the dice a spell rolls, criticals, Empowered rerolls, and what a saving throw leaves";
    }
}
