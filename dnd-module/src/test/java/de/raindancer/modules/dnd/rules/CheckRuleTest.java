package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Ability;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import de.raindancer.modules.dnd.model.Roll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CheckRuleTest {

    private final CheckRule rule = new CheckRule(new ProgressionRule());

    @Test
    @DisplayName("an attack hits on meeting the armour class; a natural 20 always hits and crits, a 1 always misses")
    void attacks() {
        assertThat(rule.attack(10, 5, 15).succeeded()).isTrue();
        assertThat(rule.attack(9, 5, 15).succeeded()).isFalse();
        Roll twenty = rule.attack(20, -10, 30);
        assertThat(twenty.succeeded()).isTrue();
        assertThat(twenty.critical()).isTrue();
        assertThat(rule.attack(1, 50, 10).succeeded()).isFalse();
    }

    @Test
    @DisplayName("a saving throw has no automatic results")
    void saves() {
        assertThat(rule.save(20, -10, 30).succeeded()).isFalse();
        assertThat(rule.save(1, 20, 15).succeeded()).isTrue();
        assertThat(rule.save(1, 20, 15).critical()).isFalse();
    }

    @Test
    @DisplayName("the save DC is 8 + proficiency + casting modifier; attacks add proficiency and the modifier")
    void spellNumbers() {
        Hero wizard = Hero.fresh(UUID.randomUUID(), HeroClass.WIZARD, List.of());
        assertThat(rule.spellDc(wizard)).isEqualTo(8 + 2 + 2);
        assertThat(rule.spellAttackBonus(wizard)).isEqualTo(4);
        Hero fighter = Hero.fresh(UUID.randomUUID(), HeroClass.FIGHTER, List.of());
        assertThat(rule.castingModifier(fighter)).isZero();
    }

    @Test
    @DisplayName("a save adds proficiency only where the class has it")
    void saveBonus() {
        Hero rogue = Hero.fresh(UUID.randomUUID(), HeroClass.ROGUE, List.of()).withLevel(5, 0, 0);
        assertThat(rule.saveBonus(rogue, Ability.DEXTERITY)).isEqualTo(2 + 3);
        assertThat(rule.saveBonus(rogue, Ability.STRENGTH)).isEqualTo(-1);
    }

    @Test
    @DisplayName("a player's armour class comes from armour points, with less of their dexterity in heavier armour")
    void armourClass() {
        assertThat(rule.playerArmourClass(0, 3)).isEqualTo(13);
        assertThat(rule.playerArmourClass(7, 3)).isEqualTo(10 + 3 + 3);
        assertThat(rule.playerArmourClass(15, 3)).isEqualTo(10 + 6 + 2);
        assertThat(rule.playerArmourClass(20, 3)).isEqualTo(18);
    }

    @Test
    @DisplayName("with disadvantage the lower of two dice is kept")
    void disadvantage() {
        SplittableRandom random = new SplittableRandom(7);
        for (int go = 0; go < 200; go++) {
            int die = rule.d20(random, true);
            assertThat(die).isBetween(1, 20);
        }
        double plain = average(false);
        double worse = average(true);
        assertThat(worse).isLessThan(plain - 2);
    }

    private double average(boolean disadvantage) {
        SplittableRandom random = new SplittableRandom(42);
        double total = 0;
        for (int go = 0; go < 5_000; go++) {
            total += rule.d20(random, disadvantage);
        }
        return total / 5_000;
    }
}
