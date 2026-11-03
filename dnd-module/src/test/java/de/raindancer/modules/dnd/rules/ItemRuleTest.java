package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.MagicItem;
import de.raindancer.modules.dnd.model.Rarity;
import de.raindancer.modules.dnd.model.Scroll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemRuleTest {

    private final ItemRule rule = new ItemRule();

    @Test
    @DisplayName("a scroll's strength grows with its tier and Spellbreaker's percent")
    void power() {
        assertThat(rule.power(1, 0)).isEqualTo(4.0);
        assertThat(rule.power(5, 0)).isEqualTo(12.0);
        assertThat(rule.power(9, 0)).as("tier 5 at most").isEqualTo(12.0);
        assertThat(rule.power(1, 50)).isEqualTo(6.0);
    }

    @Test
    @DisplayName("wand charges come back one per recharge, faster with Wandwright, the part-way time kept")
    void wand() {
        long every = rule.rechargeMillis(0);
        assertThat(every).isEqualTo(MagicItem.WAND_RECHARGE_MILLIS);
        assertThat(rule.rechargeMillis(100)).isEqualTo(MagicItem.WAND_RECHARGE_MILLIS / 2);
        assertThat(rule.charges(2, 0, every * 3 + 5, every)).isEqualTo(5);
        assertThat(rule.chargedAt(2, 0, every * 3 + 5, every)).isEqualTo(every * 3);
        assertThat(rule.charges(6, 0, every * 10, every)).isEqualTo(MagicItem.WAND_CHARGES);
    }

    @Test
    @DisplayName("old scrolls keep their strength: every spell becomes a scroll kind of a matching tier")
    void legacyScrolls() {
        assertThat(Scroll.legacyKind("fireball")).isEqualTo(Scroll.FIREBALL);
        assertThat(Scroll.legacyTier("fireball")).isEqualTo(3);
        assertThat(Scroll.legacyKind("cure_wounds")).isEqualTo(Scroll.HEALING);
        assertThat(Scroll.legacyKind("power_word_kill")).isEqualTo(Scroll.LIGHTNING);
        assertThat(Scroll.legacyTier("power_word_kill")).isEqualTo(5);
        assertThat(Scroll.legacyKind("nonsense")).isEqualTo(Scroll.MAGIC_MISSILE);
        assertThat(Scroll.rarity(1)).isEqualTo(Rarity.COMMON);
        assertThat(Scroll.rarity(5)).isEqualTo(Rarity.LEGENDARY);
        assertThat(Scroll.tierOf(Rarity.RARE)).isEqualTo(3);
    }
}
