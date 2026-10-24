package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.MagicItem;
import de.raindancer.modules.dnd.model.Rarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class LootRuleTest {

    private final LootRule rule = new LootRule();

    @Test
    @DisplayName("tougher monsters drop more often; bosses always; harmless ones never")
    void chance() {
        assertThat(rule.dropChance(0, 100)).isZero();
        assertThat(rule.dropChance(50, 100)).isBetween(0.02, 0.05);
        assertThat(rule.dropChance(450, 100)).isGreaterThan(rule.dropChance(50, 100));
        assertThat(rule.dropChance(5_000, 100)).isEqualTo(1.0);
        assertThat(rule.dropChance(50, 0)).isZero();
        assertThat(rule.dropChance(50, 200)).isEqualTo(rule.dropChance(50, 100) * 2);
    }

    @Test
    @DisplayName("common monsters drop mostly common things; a boss never drops anything common")
    void rarity() {
        Map<Rarity, Integer> zombie = new EnumMap<>(Rarity.class);
        Map<Rarity, Integer> dragon = new EnumMap<>(Rarity.class);
        SplittableRandom random = new SplittableRandom(9);
        for (int go = 0; go < 4_000; go++) {
            zombie.merge(rule.rarity(50, random.nextDouble()), 1, Integer::sum);
            dragon.merge(rule.rarity(20_000, random.nextDouble()), 1, Integer::sum);
        }
        assertThat(zombie.getOrDefault(Rarity.COMMON, 0)).isGreaterThan(zombie.getOrDefault(Rarity.UNCOMMON, 0));
        assertThat(zombie).doesNotContainKey(Rarity.LEGENDARY);
        assertThat(dragon).doesNotContainKey(Rarity.COMMON).containsKey(Rarity.LEGENDARY);
    }

    @Test
    @DisplayName("an item of every rarity can be found, and a scroll's spell matches its rarity")
    void items() {
        SplittableRandom random = new SplittableRandom(4);
        for (Rarity rarity : Rarity.values()) {
            for (int go = 0; go < 50; go++) {
                LootRule.Drop drop = rule.item(rarity, random);
                if (drop.item() == MagicItem.SPELL_SCROLL) {
                    assertThat(Rarity.ofSpellLevel(drop.spell().level())).isEqualTo(rarity);
                } else {
                    assertThat(drop.item().rarity()).isEqualTo(rarity);
                }
            }
        }
    }
}
