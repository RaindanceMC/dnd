package de.raindancer.modules.skills.skill.trading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TradingRuleTest {

    private final TradingRule rule = new TradingRule();

    @Test
    @DisplayName("a discount is a share of the base price, rounded down, and a price never drops below one")
    void discount() {
        assertThat(rule.discount(20, 20, 25)).isEqualTo(5);
        assertThat(rule.discount(20, 20, 0)).isZero();
        assertThat(rule.discount(3, 3, 40)).isEqualTo(1);
        assertThat(rule.discount(10, 2, 40)).as("down to one at the least").isEqualTo(1);
        assertThat(rule.discount(1, 1, 40)).isZero();
    }

    @Test
    @DisplayName("Silver Tongue takes off exactly what demand put on, as vanilla works it out")
    void demand() {
        assertThat(rule.demandBump(20, 4, 0.05f)).isEqualTo(4);
        assertThat(rule.demandBump(20, -3, 0.05f)).isZero();
        assertThat(rule.demandBump(1, 1, 0.2f)).isZero();
    }

    @Test
    @DisplayName("a used-up trade comes back after 20 minutes, sooner by the percent; without the node, never")
    void restock() {
        assertThat(rule.restockTicks(0)).isZero();
        assertThat(rule.restockTicks(100)).isEqualTo(12_000);
        assertThat(rule.restockTicks(200)).isEqualTo(8_000);
    }

    @Test
    @DisplayName("an extra emerald only for a trade that pays emeralds")
    void emeralds() {
        assertThat(rule.paysEmeralds("emerald")).isTrue();
        assertThat(rule.paysEmeralds("emerald_block")).isFalse();
        assertThat(rule.paysEmeralds("bread")).isFalse();
    }
}
