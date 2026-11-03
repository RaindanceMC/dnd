package de.raindancer.modules.skills.rules;

import de.raindancer.modules.skills.model.Perk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FightRuleTest {

    private final FightRule rule = new FightRule();

    private static FightRule.Hit melee(boolean critical, boolean behind, boolean undead, boolean vsPlayer,
                                       boolean offHandEmpty, boolean belowHalf) {
        return new FightRule.Hit(FightRule.Kind.MELEE, critical, behind, undead, vsPlayer, offHandEmpty, belowHalf, 2, 0);
    }

    @Test
    @DisplayName("melee: every bonus that applies adds its percent; a +N weapon adds N first")
    void melee() {
        Map<Perk, Double> tree = Map.of(Perk.MELEE_DAMAGE, 10.0, Perk.CRIT_DAMAGE, 20.0, Perk.BACKSTAB, 30.0,
                Perk.UNDEAD_DAMAGE, 40.0, Perk.TWO_HANDED, 25.0, Perk.RAGE, 15.0, Perk.RANGED_DAMAGE, 99.0);
        assertThat(rule.dealt(tree, melee(false, false, false, false, false, false), 10, 50)).isEqualTo(11.0);
        assertThat(rule.dealt(tree, melee(true, true, true, false, true, true), 10, 50))
                .isCloseTo(10 * (1 + 1.40), within(1e-9));
        var plusTwo = new FightRule.Hit(FightRule.Kind.MELEE, false, false, false, false, false, false, 2, 2);
        assertThat(rule.dealt(tree, plusTwo, 10, 50)).isCloseTo(12 * 1.1, within(1e-9));
    }

    @Test
    @DisplayName("against a player only the owner's share of the bonus counts")
    void pvpShare() {
        Map<Perk, Double> tree = Map.of(Perk.MELEE_DAMAGE, 40.0, Perk.DODGE, 20.0, Perk.DAMAGE_TAKEN, 20.0,
                Perk.LIFESTEAL, 10.0);
        assertThat(rule.dealt(tree, melee(false, false, false, true, false, false), 10, 50)).isEqualTo(12.0);
        assertThat(rule.dealt(tree, melee(false, false, false, true, false, false), 10, 0)).isEqualTo(10.0);
        assertThat(rule.dodgeChance(tree, true, 50)).isEqualTo(0.1);
        assertThat(rule.dodgeChance(tree, false, 50)).isEqualTo(0.2);
        assertThat(rule.taken(tree, 10, true, 50, false)).isEqualTo(9.0);
        assertThat(rule.healed(tree, 10, true, 50)).isEqualTo(0.5);
    }

    @Test
    @DisplayName("arrows: ranged bonus, Long Shot from twenty blocks; melee bonuses do not apply")
    void ranged() {
        Map<Perk, Double> tree = Map.of(Perk.RANGED_DAMAGE, 10.0, Perk.LONG_SHOT, 40.0, Perk.MELEE_DAMAGE, 99.0);
        var close = new FightRule.Hit(FightRule.Kind.RANGED, true, true, false, false, true, true, 19.9, 0);
        var far = new FightRule.Hit(FightRule.Kind.RANGED, false, false, false, false, false, false, 20, 0);
        assertThat(rule.dealt(tree, close, 10, 50)).isEqualTo(11.0);
        assertThat(rule.dealt(tree, far, 10, 50)).isEqualTo(15.0);
    }

    @Test
    @DisplayName("sweeping blows get the sweep bonus on top of the melee one")
    void sweep() {
        Map<Perk, Double> tree = Map.of(Perk.MELEE_DAMAGE, 10.0, Perk.SWEEP_DAMAGE, 40.0);
        var sweep = new FightRule.Hit(FightRule.Kind.SWEEP, false, false, false, false, false, false, 2, 0);
        assertThat(rule.dealt(tree, sweep, 10, 50)).isEqualTo(15.0);
    }

    @Test
    @DisplayName("hurts taken: the tree's share and a Ring of Protection's tenth, never below nothing")
    void taken() {
        assertThat(rule.taken(Map.of(Perk.DAMAGE_TAKEN, 20.0), 10, false, 50, true)).isCloseTo(7.0, within(1e-9));
        assertThat(rule.taken(Map.of(), 10, false, 50, false)).isEqualTo(10.0);
    }

    @Test
    @DisplayName("Dual Wielder's off hand strikes for half; behind means more than ninety degrees off the facing")
    void dualAndBehind() {
        assertThat(rule.offHand(7)).isEqualTo(3.5);
        assertThat(FightRule.behind(0, 1, 0, -1)).as("struck from behind").isTrue();
        assertThat(FightRule.behind(0, 1, 0, 1)).as("struck from the front").isFalse();
        assertThat(FightRule.behind(0, 1, 1, 0)).as("from the side is not behind").isFalse();
    }
}
