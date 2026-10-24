package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureRuleTest {

    private final FeatureRule rule = new FeatureRule();

    private static Hero of(HeroClass heroClass, int level) {
        return Hero.fresh(UUID.randomUUID(), heroClass, List.of()).withLevel(level, 0, 0);
    }

    @Test
    @DisplayName("rages per long rest grow with the level and are unlimited at 20")
    void rages() {
        assertThat(rule.uses(Feature.RAGE, 1)).isEqualTo(2);
        assertThat(rule.uses(Feature.RAGE, 3)).isEqualTo(3);
        assertThat(rule.uses(Feature.RAGE, 6)).isEqualTo(4);
        assertThat(rule.uses(Feature.RAGE, 12)).isEqualTo(5);
        assertThat(rule.uses(Feature.RAGE, 17)).isEqualTo(6);
        assertThat(rule.isUnlimited(Feature.RAGE, 20)).isTrue();
    }

    @Test
    @DisplayName("Channel Divinity is one pool, 1 at 2, 2 at 6, 3 at 18")
    void channelDivinity() {
        assertThat(rule.uses(Feature.TURN_UNDEAD, 2)).isEqualTo(1);
        assertThat(rule.uses(Feature.PRESERVE_LIFE, 6)).isEqualTo(2);
        assertThat(rule.uses(Feature.TURN_UNDEAD, 18)).isEqualTo(3);
        Hero cleric = of(HeroClass.CLERIC, 6).withUsed(Feature.PRESERVE_LIFE, 1);
        assertThat(rule.remaining(cleric, Feature.TURN_UNDEAD)).isEqualTo(1);
    }

    @Test
    @DisplayName("Action Surge twice from 17, Indomitable 1, 2 and 3")
    void others() {
        assertThat(rule.uses(Feature.ACTION_SURGE, 2)).isEqualTo(1);
        assertThat(rule.uses(Feature.ACTION_SURGE, 17)).isEqualTo(2);
        assertThat(rule.uses(Feature.INDOMITABLE, 9)).isEqualTo(1);
        assertThat(rule.uses(Feature.INDOMITABLE, 13)).isEqualTo(2);
        assertThat(rule.uses(Feature.INDOMITABLE, 17)).isEqualTo(3);
        assertThat(rule.uses(Feature.SECOND_WIND, 1)).isEqualTo(1);
    }

    @Test
    @DisplayName("a feature is usable only by its class, from its level, while uses are left")
    void usable() {
        assertThat(rule.mayUse(of(HeroClass.FIGHTER, 1), Feature.SECOND_WIND)).isTrue();
        assertThat(rule.mayUse(of(HeroClass.FIGHTER, 1), Feature.ACTION_SURGE)).isFalse();
        assertThat(rule.mayUse(of(HeroClass.ROGUE, 20), Feature.SECOND_WIND)).isFalse();
        assertThat(rule.mayUse(of(HeroClass.FIGHTER, 1).withUsed(Feature.SECOND_WIND, 1), Feature.SECOND_WIND))
                .isFalse();
        assertThat(rule.mayUse(of(HeroClass.ROGUE, 2), Feature.CUNNING_DASH)).isTrue();
        assertThat(rule.mayUse(of(HeroClass.BARBARIAN, 20).withUsed(Feature.RAGE, 50), Feature.RAGE)).isTrue();
    }

    @Test
    @DisplayName("cooldown-only features have a wait, counted ones do not need one")
    void cooldowns() {
        assertThat(rule.cooldownSeconds(Feature.CUNNING_DASH)).isPositive();
        assertThat(rule.cooldownSeconds(Feature.RECKLESS_ATTACK)).isPositive();
        assertThat(rule.cooldownSeconds(Feature.RAGE)).isZero();
    }

    @Test
    @DisplayName("the numbers that grow: rage damage, sneak attack dice, extra attack, the undead destroyed")
    void scaling() {
        assertThat(rule.rageDamage(1)).isEqualTo(2);
        assertThat(rule.rageDamage(9)).isEqualTo(3);
        assertThat(rule.rageDamage(16)).isEqualTo(4);
        assertThat(rule.sneakAttackDice(1)).isEqualTo(1);
        assertThat(rule.sneakAttackDice(2)).isEqualTo(1);
        assertThat(rule.sneakAttackDice(3)).isEqualTo(2);
        assertThat(rule.sneakAttackDice(20)).isEqualTo(10);
        assertThat(rule.extraAttackBonus(4)).isZero();
        assertThat(rule.extraAttackBonus(5)).isEqualTo(0.25);
        assertThat(rule.extraAttackBonus(11)).isEqualTo(0.5);
        assertThat(rule.extraAttackBonus(20)).isEqualTo(0.75);
        assertThat(rule.destroysUndeadUpTo(4)).isZero();
        assertThat(rule.destroysUndeadUpTo(5)).isEqualTo(100);
        assertThat(rule.destroysUndeadUpTo(17)).isEqualTo(1100);
    }
}
