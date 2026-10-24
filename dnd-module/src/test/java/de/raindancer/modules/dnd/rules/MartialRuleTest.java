package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MartialRuleTest {

    private final MartialRule rule = new MartialRule(new FeatureRule());

    private static Hero of(HeroClass heroClass, int level) {
        return Hero.fresh(UUID.randomUUID(), heroClass, List.of()).withLevel(level, 0, 0);
    }

    @Test
    @DisplayName("sneak attack needs sneaking, being unseen, a distracted foe, or striking from behind")
    void sneakAttack() {
        assertThat(rule.sneakAttackApplies(true, false, false, 180)).isTrue();
        assertThat(rule.sneakAttackApplies(false, true, false, 180)).isTrue();
        assertThat(rule.sneakAttackApplies(false, false, true, 180)).isTrue();
        assertThat(rule.sneakAttackApplies(false, false, false, 30)).isTrue();
        assertThat(rule.sneakAttackApplies(false, false, false, 120)).isFalse();
    }

    @Test
    @DisplayName("a melee blow gains the rage bonus, then Extra Attack, then Reckless Attack")
    void melee() {
        assertThat(rule.melee(of(HeroClass.BARBARIAN, 1), 5, true, false)).isEqualTo(7.0);
        assertThat(rule.melee(of(HeroClass.BARBARIAN, 1), 5, false, true)).isCloseTo(6.25, within(0.001));
        assertThat(rule.melee(of(HeroClass.FIGHTER, 5), 4, false, false)).isEqualTo(5.0);
        assertThat(rule.melee(of(HeroClass.WIZARD, 20), 4, true, true)).as("only barbarians rage")
                .isCloseTo(5.0, within(0.001));
    }

    @Test
    @DisplayName("what lands is halved by rage when physical, softened by Shield, halved by a dodge")
    void incoming() {
        assertThat(rule.incoming(10, true, true, false, false, false)).isEqualTo(5.0);
        assertThat(rule.incoming(10, true, false, false, false, false)).as("rage does not stop magic").isEqualTo(10.0);
        assertThat(rule.incoming(10, false, true, true, false, false)).isEqualTo(12.5);
        assertThat(rule.incoming(10, false, true, false, true, false)).isEqualTo(6.0);
        assertThat(rule.incoming(10, false, true, false, false, true)).isEqualTo(5.0);
    }

    @Test
    @DisplayName("armour: Defense needs a chestplate, Unarmored Defense needs none")
    void armour() {
        assertThat(rule.armourBonus(of(HeroClass.FIGHTER, 1), true)).isEqualTo(2.0);
        assertThat(rule.armourBonus(of(HeroClass.FIGHTER, 1), false)).isZero();
        assertThat(rule.armourBonus(of(HeroClass.BARBARIAN, 1), false)).isEqualTo(6.0);
        assertThat(rule.armourBonus(of(HeroClass.BARBARIAN, 1), true)).isZero();
        assertThat(rule.armourBonus(of(HeroClass.WIZARD, 1), false)).isZero();
    }

    @Test
    @DisplayName("barbarians run faster from level 5")
    void speed() {
        assertThat(rule.speedBonus(of(HeroClass.BARBARIAN, 4))).isZero();
        assertThat(rule.speedBonus(of(HeroClass.BARBARIAN, 5))).isEqualTo(0.1);
        assertThat(rule.speedBonus(of(HeroClass.ROGUE, 20))).isZero();
    }
}
