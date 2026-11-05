package de.raindancer.modules.skills.skill.enchanting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class EnchantingRuleTest {

    private final EnchantingRule rule = new EnchantingRule();

    @Test
    @DisplayName("anvil work cheaper by the percent, a level at the least, and Too Expensive stays too expensive")
    void anvil() {
        assertThat(rule.anvilCost(10, 0)).isEqualTo(10);
        assertThat(rule.anvilCost(10, 50)).isEqualTo(5);
        assertThat(rule.anvilCost(1, 50)).isEqualTo(1);
        assertThat(rule.anvilCost(39, 50)).isEqualTo(20);
        assertThat(rule.anvilCost(40, 50)).as("past what survival may pay, the game says no").isEqualTo(40);
        assertThat(rule.anvilCost(0, 50)).isZero();
    }

    @Test
    @DisplayName("Smith's Mastery: the result is no dearer to work than the dearer of what went in")
    void priorWork() {
        assertThat(rule.priorWork(3, 7)).isEqualTo(7);
        assertThat(rule.priorWork(15, 1)).isEqualTo(15);
    }

    @Test
    @DisplayName("bookshelves count up to the fifteen a table can use")
    void shelves() {
        assertThat(rule.shelves(6, 4)).isEqualTo(10);
        assertThat(rule.shelves(12, 15)).isEqualTo(15);
        assertThat(rule.shelves(0, 0)).isZero();
    }

    @Test
    @DisplayName("offers worked out as the table does: the top one twice the bookshelves at least, never below its slot")
    void offers() {
        for (int seed = 0; seed < 200; seed++) {
            int[] costs = rule.offerCosts(15, new Random(seed));
            assertThat(costs[2]).isBetween(30, 30);
            assertThat(costs[0]).isBetween(1, 30);
            assertThat(costs[1]).isBetween(2, 30);
        }
        int[] bare = rule.offerCosts(0, new Random(1));
        assertThat(bare[2]).as("a slot whose cost is below its number is shut, as the table does").isBetween(0, 8);
    }

    @Test
    @DisplayName("more orb experience, rounded; nothing taken away")
    void orbs() {
        assertThat(rule.orbs(10, 50)).isEqualTo(15);
        assertThat(rule.orbs(3, 10)).isEqualTo(3);
        assertThat(rule.orbs(0, 50)).isZero();
        assertThat(rule.orbs(-2, 50)).isEqualTo(-2);
    }
}
