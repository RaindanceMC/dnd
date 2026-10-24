package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Dice;
import de.raindancer.modules.dnd.model.Spell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class DamageRuleTest {

    private final DamageRule rule = new DamageRule(new ProgressionRule());

    /** A die that always shows the same face. */
    private static RandomGenerator always(int face) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return Math.min(bound - 1, face - 1);
            }
        };
    }

    @Test
    @DisplayName("cantrips scale with the character level, not the slot")
    void cantrips() {
        assertThat(rule.dice(Spell.FIRE_BOLT, 0, 1)).containsExactly(Dice.of(1, 10));
        assertThat(rule.dice(Spell.FIRE_BOLT, 0, 5)).containsExactly(Dice.of(2, 10));
        assertThat(rule.dice(Spell.FIRE_BOLT, 0, 17)).containsExactly(Dice.of(4, 10));
    }

    @Test
    @DisplayName("a higher slot adds the upcast dice per level above the spell's own")
    void upcasting() {
        assertThat(rule.dice(Spell.FIREBALL, 3, 5)).containsExactly(Dice.of(8, 6));
        assertThat(rule.dice(Spell.FIREBALL, 5, 9)).containsExactly(Dice.of(10, 6));
        assertThat(rule.dice(Spell.ICE_STORM, 4, 7)).containsExactly(Dice.of(2, 8), Dice.of(4, 6));
        assertThat(rule.dice(Spell.ICE_STORM, 6, 11)).containsExactly(Dice.of(4, 8), Dice.of(4, 6));
        assertThat(rule.dice(Spell.DISINTEGRATE, 7, 13)).containsExactly(new Dice(13, 6, 40));
    }

    @Test
    @DisplayName("darts, rays and jumps grow in number rather than size")
    void counts() {
        assertThat(rule.count(Spell.MAGIC_MISSILE, 1)).isEqualTo(3);
        assertThat(rule.count(Spell.MAGIC_MISSILE, 3)).isEqualTo(5);
        assertThat(rule.count(Spell.SCORCHING_RAY, 2)).isEqualTo(3);
        assertThat(rule.count(Spell.SCORCHING_RAY, 4)).isEqualTo(5);
        assertThat(rule.count(Spell.CHAIN_LIGHTNING, 7)).isEqualTo(5);
        assertThat(rule.count(Spell.FIREBALL, 9)).isEqualTo(1);
        assertThat(rule.dice(Spell.MAGIC_MISSILE, 4, 7)).containsExactly(new Dice(1, 4, 1));
    }

    @Test
    @DisplayName("a critical hit doubles the dice, not the bonus")
    void criticals() {
        List<Dice> dice = List.of(new Dice(2, 6, 3));
        assertThat(rule.roll(dice, false, false, always(6))).isEqualTo(15);
        assertThat(rule.roll(dice, true, false, always(6))).isEqualTo(27);
    }

    @Test
    @DisplayName("Empowered rerolls the dice that came up below half, once")
    void empowered() {
        RandomGenerator lowThenHigh = new RandomGenerator() {
            private int calls;

            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return calls++ % 2 == 0 ? 0 : bound - 1;
            }
        };
        assertThat(rule.roll(List.of(Dice.of(1, 6)), false, true, lowThenHigh)).isEqualTo(6);
    }

    @Test
    @DisplayName("a made save halves or cancels; Evasion makes half nothing and a fail half")
    void saves() {
        assertThat(rule.afterSave(20, Spell.FIREBALL, false, false)).isEqualTo(20);
        assertThat(rule.afterSave(21, Spell.FIREBALL, true, false)).isEqualTo(10);
        assertThat(rule.afterSave(50, Spell.DISINTEGRATE, true, false)).isZero();
        assertThat(rule.afterSave(20, Spell.FIREBALL, true, true)).isZero();
        assertThat(rule.afterSave(20, Spell.FIREBALL, false, true)).isEqualTo(10);
        assertThat(rule.afterSave(20, Spell.CONE_OF_COLD, false, true))
                .as("evasion is for dexterity saves only").isEqualTo(20);
    }

    @Test
    @DisplayName("healing adds the casting modifier, and Disciple of Life two plus the slot level")
    void healing() {
        assertThat(rule.healing(Spell.CURE_WOUNDS, 1, 3, false, always(8))).isEqualTo(11);
        assertThat(rule.healing(Spell.CURE_WOUNDS, 1, 3, true, always(8))).isEqualTo(14);
        assertThat(rule.healing(Spell.CURE_WOUNDS, 3, 3, false, always(8))).isEqualTo(27);
        assertThat(rule.healing(Spell.HEAL, 6, 5, false, always(1))).isEqualTo(70);
        assertThat(rule.healing(Spell.HEAL, 7, 5, false, always(1))).isEqualTo(80);
    }

    @Test
    @DisplayName("percent scaling never turns a hit into nothing")
    void scaling() {
        assertThat(rule.scaled(10, 50)).isEqualTo(5.0);
        assertThat(rule.scaled(1, 10)).isEqualTo(0.5);
        assertThat(rule.scaled(0, 100)).isZero();
    }

    @Test
    @DisplayName("rolls stay inside what the dice can show")
    void bounds() {
        SplittableRandom random = new SplittableRandom(3);
        for (int go = 0; go < 500; go++) {
            int rolled = rule.roll(List.of(Dice.of(8, 6)), false, false, random);
            assertThat(rolled).isBetween(8, 48);
        }
    }
}
