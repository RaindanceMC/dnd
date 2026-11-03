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
    @DisplayName("an item of every rarity can be found, and a scroll's tier matches its rarity")
    void items() {
        SplittableRandom random = new SplittableRandom(4);
        for (Rarity rarity : Rarity.values()) {
            for (int go = 0; go < 50; go++) {
                LootRule.Drop drop = rule.item(rarity, random);
                if (drop.item() == MagicItem.SPELL_SCROLL) {
                    assertThat(drop.scroll()).isNotNull();
                    assertThat(de.raindancer.modules.dnd.model.Scroll.rarity(drop.tier())).isEqualTo(rarity);
                } else {
                    assertThat(drop.item().rarity()).isEqualTo(rarity);
                }
            }
        }
    }

    @Test
    @DisplayName("the Potion of Mach Fuck is only ever brewed, never found")
    void machIsBrewed() {
        SplittableRandom random = new SplittableRandom(1);
        for (int go = 0; go < 2_000; go++) {
            assertThat(rule.item(MagicItem.POTION_OF_MACH_FUCK.rarity(), random).item())
                    .isNotEqualTo(MagicItem.POTION_OF_MACH_FUCK);
        }
        assertThat(MagicItem.POTION_OF_MACH_FUCK.lootable()).isFalse();
        assertThat(MagicItem.POTION_OF_SPEED.lootable()).isTrue();
        assertThat(MagicItem.POTION_OF_MACH_FUCK.isPotion()).isTrue();
    }

    @Test
    @DisplayName("Treasure Sense and its kin raise the drop chance by their percent, never past certain")
    void luck() {
        assertThat(rule.withLuck(0.1, 50)).isCloseTo(0.15, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(rule.withLuck(0.8, 100)).isEqualTo(1.0);
        assertThat(rule.withLuck(0.1, -20)).isEqualTo(0.1);
    }

    @Test
    @DisplayName("at most a few drops an hour per player: a mob farm does not rain magic items")
    void hourlyCap() {
        long hour = 3_600_000L;
        java.util.List<Long> drops = java.util.List.of(0L, 10L, 20L, 30L);
        assertThat(rule.mayDrop(drops, 40)).isFalse();
        assertThat(rule.mayDrop(drops.subList(0, 3), 40)).isTrue();
        assertThat(rule.mayDrop(drops, hour + 1)).as("the first left the hour").isTrue();
        assertThat(LootRule.DROPS_PER_HOUR).isEqualTo(4);
    }
}
