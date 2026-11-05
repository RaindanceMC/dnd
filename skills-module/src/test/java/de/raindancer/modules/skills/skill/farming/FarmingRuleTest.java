package de.raindancer.modules.skills.skill.farming;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FarmingRuleTest {

    private final FarmingRule rule = new FarmingRule();

    @Test
    @DisplayName("what a grown crop gives twice is its crop, never the seeds beside it")
    void produce() {
        assertThat(rule.produce(Material.WHEAT)).contains(Material.WHEAT);
        assertThat(rule.produce(Material.CARROTS)).contains(Material.CARROT);
        assertThat(rule.produce(Material.POTATOES)).contains(Material.POTATO);
        assertThat(rule.produce(Material.BEETROOTS)).contains(Material.BEETROOT);
        assertThat(rule.produce(Material.NETHER_WART)).contains(Material.NETHER_WART);
        assertThat(rule.produce(Material.COCOA)).contains(Material.COCOA_BEANS);
        assertThat(rule.produce(Material.MELON)).contains(Material.MELON_SLICE);
        assertThat(rule.produce(Material.PUMPKIN)).contains(Material.PUMPKIN);
        assertThat(rule.produce(Material.STONE)).isEmpty();
        assertThat(rule.produce(Material.WHEAT_SEEDS)).isEmpty();
    }

    @Test
    @DisplayName("Green Thumb plants from the crop's own seeds; a melon or pumpkin is not replanted")
    void seeds() {
        assertThat(rule.seed(Material.WHEAT)).contains(Material.WHEAT_SEEDS);
        assertThat(rule.seed(Material.BEETROOTS)).contains(Material.BEETROOT_SEEDS);
        assertThat(rule.seed(Material.POTATOES)).contains(Material.POTATO);
        assertThat(rule.seed(Material.NETHER_WART)).contains(Material.NETHER_WART);
        assertThat(rule.seed(Material.COCOA)).contains(Material.COCOA_BEANS);
        assertThat(rule.seed(Material.MELON)).isEmpty();
        assertThat(rule.seed(Material.TORCHFLOWER_CROP)).isEmpty();
        assertThat(rule.reapable(Material.CARROTS)).isTrue();
        assertThat(rule.reapable(Material.MELON)).as("a block the world grew is broken one at a time").isFalse();
    }

    @Test
    @DisplayName("an animal's meat, leather, feathers and wool come twice, never what it carried")
    void animalDrops() {
        assertThat(rule.animalProduce(Material.BEEF)).isTrue();
        assertThat(rule.animalProduce(Material.COOKED_PORKCHOP)).isTrue();
        assertThat(rule.animalProduce(Material.LEATHER)).isTrue();
        assertThat(rule.animalProduce(Material.WHITE_WOOL)).isTrue();
        assertThat(rule.animalProduce(Material.FEATHER)).isTrue();
        assertThat(rule.animalProduce(Material.SADDLE)).isFalse();
        assertThat(rule.animalProduce(Material.DIAMOND_HORSE_ARMOR)).isFalse();
        assertThat(rule.animalProduce(Material.CHEST)).isFalse();
    }

    @Test
    @DisplayName("growth: a stage every pulse as often as the game's own pace, times the percentage")
    void growth() {
        assertThat(rule.stageChance(0)).isZero();
        assertThat(rule.stageChance(100)).isEqualTo(FarmingRule.BASE_STAGE_CHANCE);
        assertThat(rule.stageChance(50)).isEqualTo(FarmingRule.BASE_STAGE_CHANCE / 2);
        assertThat(rule.weaned(-24_000, 25)).isEqualTo(-18_000);
        assertThat(rule.weaned(-24_000, 0)).isEqualTo(-24_000);
        assertThat(rule.weaned(0, 50)).as("an adult stays one").isZero();
        assertThat(rule.weaned(6_000, 50)).as("a parent's wait is not a baby's").isEqualTo(6_000);
        assertThat(rule.hoe(Material.NETHERITE_HOE)).isTrue();
        assertThat(rule.hoe(Material.SHEARS)).isFalse();
    }
}
