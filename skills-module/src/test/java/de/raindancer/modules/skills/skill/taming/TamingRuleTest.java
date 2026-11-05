package de.raindancer.modules.skills.skill.taming;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TamingRuleTest {

    private final TamingRule rule = new TamingRule();

    @Test
    @DisplayName("what tames whom: bones for wolves, fish for cats, seeds for parrots")
    void food() {
        assertThat(rule.tamingFood("wolf", Material.BONE)).isTrue();
        assertThat(rule.tamingFood("cat", Material.COD)).isTrue();
        assertThat(rule.tamingFood("cat", Material.BONE)).isFalse();
        assertThat(rule.tamingFood("parrot", Material.WHEAT_SEEDS)).isTrue();
        assertThat(rule.tamingFood("horse", Material.APPLE)).as("a horse is ridden tame").isFalse();
        assertThat(rule.pet("wolf")).isTrue();
        assertThat(rule.pet("horse")).isFalse();
    }

    @Test
    @DisplayName("a horse's temper rises by a share of its most; damage up and down by percentages")
    void numbers() {
        assertThat(rule.domestication(10, 100, 25)).isEqualTo(35);
        assertThat(rule.domestication(90, 100, 25)).isEqualTo(100);
        assertThat(rule.domestication(10, 100, 0)).isEqualTo(10);
        assertThat(rule.dealt(4, 50)).isCloseTo(6.0, within(1e-9));
        assertThat(rule.taken(10, 30)).isCloseTo(7.0, within(1e-9));
        assertThat(rule.taken(10, 150)).as("never healed by a blow").isZero();
    }
}
