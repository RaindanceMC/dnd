package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.AbilityScores;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HealthRuleTest {

    private final HealthRule rule = new HealthRule();

    private static Hero of(HeroClass heroClass, int level) {
        return Hero.fresh(UUID.randomUUID(), heroClass, List.of()).withLevel(level, 0, 0);
    }

    @Test
    @DisplayName("level 1 is the vanilla twenty; every level after adds the average hit die and Constitution, scaled")
    void bonus() {
        assertThat(rule.bonusHealth(of(HeroClass.FIGHTER, 1), 100)).isZero();
        assertThat(rule.bonusHealth(of(HeroClass.FIGHTER, 2), 100)).isEqualTo(8.0);
        assertThat(rule.bonusHealth(of(HeroClass.FIGHTER, 20), 25)).isEqualTo(38.0);
        assertThat(rule.bonusHealth(of(HeroClass.WIZARD, 20), 25)).isEqualTo(28.5);
        assertThat(rule.bonusHealth(of(HeroClass.BARBARIAN, 20), 0)).isZero();
    }

    @Test
    @DisplayName("a level never takes health away, however bad the Constitution")
    void neverNegative() {
        Hero frail = of(HeroClass.WIZARD, 10).withScores(new AbilityScores(10, 10, 1, 10, 10, 10), 0);
        assertThat(rule.perLevel(frail)).isEqualTo(1);
        assertThat(rule.perHitDie(frail)).isEqualTo(1);
    }

    @Test
    @DisplayName("a long rest gives back half the hit dice, at least one")
    void hitDiceBack() {
        assertThat(rule.hitDiceBack(1)).isEqualTo(1);
        assertThat(rule.hitDiceBack(5)).isEqualTo(2);
        assertThat(rule.hitDiceBack(20)).isEqualTo(10);
    }
}
