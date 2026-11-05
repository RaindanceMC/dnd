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

    private static FightRule.Hit blow(FightRule.Weapon weapon, boolean sneaking, boolean riposte, int streak,
                                      double armour, boolean marked) {
        return new FightRule.Hit(FightRule.Kind.MELEE, false, false, false, false, false, false, 2, 0, weapon,
                sneaking, false, marked, riposte, streak, armour);
    }

    @Test
    @DisplayName("weapon schools, sneaking, a riposte and a run of kills add to melee blows")
    void meleeExtras() {
        Map<Perk, Double> tree = Map.of(Perk.SWORD_DAMAGE, 20.0, Perk.AXE_DAMAGE, 30.0, Perk.SNEAK_ATTACK, 40.0,
                Perk.RIPOSTE, 50.0, Perk.MOMENTUM, 4.0);
        assertThat(rule.dealt(tree, blow(FightRule.Weapon.SWORD, false, false, 0, 0, false), 10, 50)).isEqualTo(12.0);
        assertThat(rule.dealt(tree, blow(FightRule.Weapon.AXE, false, false, 0, 0, false), 10, 50)).isEqualTo(13.0);
        assertThat(rule.dealt(tree, blow(FightRule.Weapon.NONE, true, true, 0, 0, false), 10, 50)).isEqualTo(19.0);
        assertThat(rule.dealt(tree, blow(FightRule.Weapon.NONE, false, false, 3, 0, false), 10, 50))
                .isCloseTo(11.2, within(1e-9));
        assertThat(rule.dealt(tree, blow(FightRule.Weapon.NONE, false, false, 99, 0, false), 10, 50))
                .as("five kills in a row at most").isCloseTo(12.0, within(1e-9));
        assertThat(FightRule.Weapon.of("DIAMOND_SWORD")).isEqualTo(FightRule.Weapon.SWORD);
        assertThat(FightRule.Weapon.of("NETHERITE_AXE")).isEqualTo(FightRule.Weapon.AXE);
        assertThat(FightRule.Weapon.of("PICKAXE")).isEqualTo(FightRule.Weapon.OTHER);
        assertThat(FightRule.Weapon.of("AIR")).isEqualTo(FightRule.Weapon.NONE);
    }

    @Test
    @DisplayName("Sunder: a percent for each point of the foe's armour; Hunter's Mark: 15% on a marked foe")
    void sunderAndMark() {
        Map<Perk, Double> both = Map.of(Perk.SUNDER, 1.0, Perk.HUNTERS_MARK, 1.0);
        assertThat(rule.dealt(both, blow(FightRule.Weapon.NONE, false, false, 0, 20, false), 10, 50)).isEqualTo(12.0);
        assertThat(rule.dealt(Map.of(), blow(FightRule.Weapon.NONE, false, false, 0, 20, false), 10, 50))
                .isEqualTo(10.0);
        assertThat(rule.dealt(both, blow(FightRule.Weapon.NONE, false, false, 0, 0, true), 10, 50)).isEqualTo(11.5);
        var markedArrow = new FightRule.Hit(FightRule.Kind.RANGED, false, false, false, false, false, false, 10, 0,
                FightRule.Weapon.NONE, false, false, true, false, 0, 0);
        assertThat(rule.dealt(both, markedArrow, 10, 50)).isEqualTo(11.5);
    }

    @Test
    @DisplayName("arrows: a hit on the head, Point Blank from six blocks or nearer")
    void arrowExtras() {
        Map<Perk, Double> tree = Map.of(Perk.HEADSHOT, 50.0, Perk.CLOSE_SHOT, 20.0, Perk.SNEAK_ATTACK, 99.0);
        var head = new FightRule.Hit(FightRule.Kind.RANGED, false, false, false, false, false, false, 10, 0,
                FightRule.Weapon.NONE, true, true, false, false, 0, 0);
        var near = new FightRule.Hit(FightRule.Kind.RANGED, false, false, false, false, false, false, 6, 0);
        assertThat(rule.dealt(tree, head, 10, 50)).isEqualTo(15.0);
        assertThat(rule.dealt(tree, near, 10, 50)).isEqualTo(12.0);
        assertThat(FightRule.headshot(1.6, 0, 1.8)).isTrue();
        assertThat(FightRule.headshot(1.2, 0, 1.8)).isFalse();
    }

    @Test
    @DisplayName("wards soften their own kind of hurt only, on top of the rest; falls have their own")
    void wards() {
        Map<Perk, Double> tree = Map.of(Perk.FIRE_WARD, 50.0, Perk.BLAST_WARD, 40.0, Perk.ARROW_WARD, 30.0,
                Perk.MAGIC_WARD, 20.0, Perk.DAMAGE_TAKEN, 10.0, Perk.FALL_DAMAGE, 60.0);
        assertThat(rule.taken(tree, 10, false, 50, false, FightRule.Ward.FIRE)).isCloseTo(4.0, within(1e-9));
        assertThat(rule.taken(tree, 10, false, 50, false, FightRule.Ward.BLAST)).isCloseTo(5.0, within(1e-9));
        assertThat(rule.taken(tree, 10, true, 50, false, FightRule.Ward.MISSILE)).isCloseTo(8.0, within(1e-9));
        assertThat(rule.taken(tree, 10, false, 50, false, FightRule.Ward.MAGIC)).isCloseTo(7.0, within(1e-9));
        assertThat(rule.taken(tree, 10, false, 50, false, FightRule.Ward.NONE)).isCloseTo(9.0, within(1e-9));
        assertThat(rule.fall(tree, 10)).isCloseTo(4.0, within(1e-9));
        assertThat(rule.fall(Map.of(), 10)).isEqualTo(10.0);
    }

    @Test
    @DisplayName("Thorns hurts the striker back by its share; between players by the players' share of it")
    void thorns() {
        Map<Perk, Double> tree = Map.of(Perk.THORNS, 20.0);
        assertThat(rule.thorns(tree, 10, false, 50)).isEqualTo(2.0);
        assertThat(rule.thorns(tree, 10, true, 50)).isEqualTo(1.0);
        assertThat(rule.thorns(Map.of(), 10, false, 50)).isZero();
    }

    @Test
    @DisplayName("Mana Shield pays its share of a hurt with experience, two points a half-heart, as far as they go")
    void manaShield() {
        Map<Perk, Double> tree = Map.of(Perk.MANA_SHIELD, 30.0);
        var rich = rule.manaShield(tree, 10, false, 50, 1_000);
        assertThat(rich.points()).isEqualTo(6);
        assertThat(rich.damage()).isCloseTo(7.0, within(1e-9));
        var poor = rule.manaShield(tree, 10, false, 50, 2);
        assertThat(poor.points()).isEqualTo(2);
        assertThat(poor.damage()).isCloseTo(9.0, within(1e-9));
        var none = rule.manaShield(Map.of(), 10, false, 50, 1_000);
        assertThat(none.points()).isZero();
        assertThat(none.damage()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("Executioner finishes a monster left below a fifth of its health — never a player or a boss")
    void execute() {
        Map<Perk, Double> tree = Map.of(Perk.EXECUTE, 1.0);
        assertThat(rule.finishes(tree, false, false, 3.9, 20)).isTrue();
        assertThat(rule.finishes(tree, false, false, 4.1, 20)).isFalse();
        assertThat(rule.finishes(tree, false, false, 0, 20)).as("dead anyway").isFalse();
        assertThat(rule.finishes(tree, true, false, 1, 20)).isFalse();
        assertThat(rule.finishes(tree, false, true, 1, 20)).isFalse();
        assertThat(rule.finishes(Map.of(), false, false, 1, 20)).isFalse();
    }

    @Test
    @DisplayName("chances on a hit count the players' share; effects last longer or shorter by their percent")
    void chancesAndDurations() {
        Map<Perk, Double> tree = Map.of(Perk.BLEED, 20.0, Perk.POTION_DURATION, 50.0, Perk.EFFECT_SHED, 40.0);
        assertThat(rule.chance(tree, Perk.BLEED, false, 50)).isEqualTo(0.2);
        assertThat(rule.chance(tree, Perk.BLEED, true, 50)).isEqualTo(0.1);
        assertThat(rule.longer(tree, 200)).isEqualTo(300);
        assertThat(rule.shorter(tree, 200)).isEqualTo(120);
        assertThat(rule.shorter(Map.of(Perk.EFFECT_SHED, 100.0), 200)).as("never gone at once").isEqualTo(1);
    }

    @Test
    @DisplayName("Spellstrike adds its force to critical blows only — a player's share of it against a player")
    void spellstrike() {
        Map<Perk, Double> tree = Map.of(Perk.SPELLSTRIKE, 1.0);
        assertThat(rule.force(tree, melee(true, false, false, false, false, false), 4, 50)).isEqualTo(4.0);
        assertThat(rule.force(tree, melee(true, false, false, true, false, false), 4, 50)).isEqualTo(2.0);
        assertThat(rule.force(tree, melee(false, false, false, false, false, false), 4, 50)).isZero();
        assertThat(rule.force(Map.of(), melee(true, false, false, false, false, false), 4, 50)).isZero();
    }
}
