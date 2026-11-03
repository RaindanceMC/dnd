package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.AdventurerTree;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.Perk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PerkRuleTest {

    private static final UUID ME = UUID.randomUUID();
    private final PerkRule rule = new PerkRule(AdventurerTree.shipped(), Duration.ofDays(1));

    private static Hero hero(int level, Map<String, Integer> nodes) {
        return new Hero(ME, level, 0, nodes, 0);
    }

    @Test
    @DisplayName("a skill point a level; a node's ranks cost its cost each")
    void points() {
        assertThat(rule.points(hero(1, Map.of()))).isEqualTo(1);
        assertThat(rule.left(hero(12, Map.of("toughness", 2, "dual_wielder", 1)))).isEqualTo(12 - 2 - 2);
        assertThat(rule.left(hero(1, Map.of("toughness", 3)))).as("never below zero").isZero();
    }

    @Test
    @DisplayName("refusals: unknown, needs a neighbour, level, points, maxed, the other side of a choice")
    void refusals() {
        assertThat(rule.refusal(hero(50, Map.of()), "nothing")).contains(PerkRule.Refusal.UNKNOWN);
        assertThat(rule.refusal(hero(50, Map.of()), "toughness")).isEmpty();
        assertThat(rule.refusal(hero(50, Map.of()), "big_heart")).contains(PerkRule.Refusal.NEEDS);
        assertThat(rule.refusal(hero(3, Map.of("brawler", 1, "crusher", 1)), "dual_wielder"))
                .contains(PerkRule.Refusal.LEVEL);
        assertThat(rule.refusal(hero(9, Map.of("brawler", 3, "crusher", 3, "quick_hands", 2)), "dual_wielder"))
                .as("level 9, nine points spent").contains(PerkRule.Refusal.NO_POINTS);
        assertThat(rule.refusal(hero(50, Map.of("toughness", 3)), "toughness")).contains(PerkRule.Refusal.MAXED);
        assertThat(rule.refusal(hero(50, Map.of("brawler", 1, "crusher", 1, "bloodthirst", 1, "great_weapon", 1)),
                "dual_wielder")).contains(PerkRule.Refusal.BARRED);
    }

    @Test
    @DisplayName("effects add up, to their caps; feats are had or not")
    void values() {
        Hero tough = hero(50, Map.of("toughness", 3, "big_heart", 3, "giant_blood", 3, "undying", 1));
        assertThat(rule.value(tough, Perk.HEARTS)).isEqualTo(11);
        assertThat(rule.has(tough, Perk.UNDYING)).isTrue();
        assertThat(rule.has(tough, Perk.DUAL_WIELD)).isFalse();
        Hero dodgy = hero(50, Map.of("sidestep", 3, "evasion", 3, "blur", 3, "phantom", 1));
        assertThat(rule.value(dodgy, Perk.DODGE)).isEqualTo(24);
        assertThat(rule.has(hero(20, Map.of("dual_wielder", 1)), Perk.DUAL_WIELD)).isTrue();
    }

    @Test
    @DisplayName("the tree is unlearnt at most once a day, and only when something is taken")
    void unlearn() {
        long day = Duration.ofDays(1).toMillis();
        assertThat(rule.mayUnlearn(hero(5, Map.of()), 0)).isFalse();
        Hero taken = hero(5, Map.of("toughness", 1));
        assertThat(rule.mayUnlearn(taken, 5 * day)).isTrue();
        Hero again = taken.unlearnt(5 * day).withRank("brawler", 1);
        assertThat(again.nodes()).containsOnlyKeys("brawler");
        assertThat(rule.mayUnlearn(again, 5 * day + 1)).isFalse();
        assertThat(rule.mayUnlearn(again, 6 * day)).isTrue();
        assertThat(rule.unlearnAgainAt(again)).isEqualTo(6 * day);
    }
}
