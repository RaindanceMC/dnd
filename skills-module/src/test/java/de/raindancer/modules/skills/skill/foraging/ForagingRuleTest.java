package de.raindancer.modules.skills.skill.foraging;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ForagingRuleTest {

    private final ForagingRule rule = new ForagingRule();

    @Test
    @DisplayName("more berries: a share of what was picked, the rest of a berry a chance")
    void berries() {
        assertThat(rule.berries(Material.SWEET_BERRY_BUSH)).isTrue();
        assertThat(rule.berries(Material.CAVE_VINES_PLANT)).isTrue();
        assertThat(rule.berries(Material.WHEAT)).isFalse();
        assertThat(rule.moreBerries(3, 0, 0.0)).isZero();
        assertThat(rule.moreBerries(3, 100, 0.99)).isEqualTo(3);
        assertThat(rule.moreBerries(2, 50, 0.99)).isOne();
        assertThat(rule.moreBerries(3, 50, 0.4)).as("1.5: one sure, half a chance").isEqualTo(2);
        assertThat(rule.moreBerries(3, 50, 0.6)).isOne();
    }

    @Test
    @DisplayName("a rare find fits what was foraged; nothing from what has no table")
    void rareFinds() {
        assertThat(rule.rareFind("poppy", 0.0)).isPresent();
        assertThat(rule.rareFind("red_mushroom", 0.5)).isPresent();
        assertThat(rule.rareFind("sweet_berry_bush", 0.99)).isPresent();
        assertThat(rule.rareFind("sea_pickle", 0.5)).isEmpty();
        for (double roll = 0; roll < 1; roll += 0.05) {
            assertThat(rule.rareFind("dandelion", roll).orElseThrow()).isNotEqualTo(Material.DANDELION);
            assertThat(rule.rareFind("brown_mushroom", roll).orElseThrow()).isNotIn(Material.BROWN_MUSHROOM,
                    Material.RED_MUSHROOM);
        }
    }

    @Test
    @DisplayName("bone meal's spots are remembered a while, so what it grew is never doubled")
    void fertilised() {
        Fertilised spots = new Fertilised(60_000, 3);
        spots.mark("world", 1, 64, 1, 1_000);
        assertThat(spots.was("world", 1, 64, 1, 2_000)).isTrue();
        assertThat(spots.was("nether", 1, 64, 1, 2_000)).isFalse();
        assertThat(spots.was("world", 1, 65, 1, 2_000)).isFalse();
        assertThat(spots.was("world", 1, 64, 1, 62_000)).as("long ago").isFalse();
        spots.mark("world", 1, 64, 1, 1_000);
        spots.mark("world", 2, 64, 1, 1_000);
        spots.mark("world", 3, 64, 1, 1_000);
        spots.mark("world", 4, 64, 1, 1_000);
        assertThat(spots.size()).as("bounded").isLessThanOrEqualTo(3);
        assertThat(spots.was("world", 4, 64, 1, 1_000)).isTrue();
    }

    @Test
    @DisplayName("Bloom grows saplings, berry bushes and stems — never a crop; a bush prickles")
    void blooms() {
        assertThat(rule.blooms(Material.OAK_SAPLING)).isTrue();
        assertThat(rule.blooms(Material.MANGROVE_PROPAGULE)).isTrue();
        assertThat(rule.blooms(Material.SWEET_BERRY_BUSH)).isTrue();
        assertThat(rule.blooms(Material.MELON_STEM)).isTrue();
        assertThat(rule.blooms(Material.CARROTS)).as("a grown crop doubled, composted back, with bone meal saved, "
                + "would give more bone meal than it took").isFalse();
        assertThat(rule.blooms(Material.GRASS_BLOCK)).as("grass makes flowers: those are never doubled")
                .isFalse();
        assertThat(rule.prickly(Material.SWEET_BERRY_BUSH)).isTrue();
        assertThat(rule.prickly(Material.CACTUS)).isFalse();
    }
}
