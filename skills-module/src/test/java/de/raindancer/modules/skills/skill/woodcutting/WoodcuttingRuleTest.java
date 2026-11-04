package de.raindancer.modules.skills.skill.woodcutting;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WoodcuttingRuleTest {

    private final WoodcuttingRule rule = new WoodcuttingRule();

    @Test
    @DisplayName("logs and stems are what Core counts as chopping; stripped logs and planks are not")
    void logs() {
        assertThat(rule.log(Material.OAK_LOG)).isTrue();
        assertThat(rule.log(Material.CRIMSON_STEM)).isTrue();
        assertThat(rule.log(Material.STRIPPED_OAK_LOG)).isFalse();
        assertThat(rule.log(Material.OAK_PLANKS)).isFalse();
        assertThat(rule.leaves(Material.BIRCH_LEAVES)).isTrue();
        assertThat(rule.leaves(Material.OAK_LOG)).isFalse();
    }

    @Test
    @DisplayName("each tree's sapling, from its leaves or its log")
    void saplings() {
        assertThat(rule.saplingOf(Material.OAK_LEAVES)).contains(Material.OAK_SAPLING);
        assertThat(rule.saplingOf(Material.SPRUCE_LOG)).contains(Material.SPRUCE_SAPLING);
        assertThat(rule.saplingOf(Material.MANGROVE_LEAVES)).contains(Material.MANGROVE_PROPAGULE);
        assertThat(rule.saplingOf(Material.MANGROVE_LOG)).contains(Material.MANGROVE_PROPAGULE);
        assertThat(rule.saplingOf(Material.AZALEA_LEAVES)).contains(Material.AZALEA);
        assertThat(rule.saplingOf(Material.FLOWERING_AZALEA_LEAVES)).contains(Material.FLOWERING_AZALEA);
        assertThat(rule.saplingOf(Material.CRIMSON_STEM)).contains(Material.CRIMSON_FUNGUS);
        assertThat(rule.saplingOf(Material.STONE)).isEmpty();
    }

    @Test
    @DisplayName("apples only from oak and dark oak leaves, as the game drops them")
    void apples() {
        assertThat(rule.dropsApples(Material.OAK_LEAVES)).isTrue();
        assertThat(rule.dropsApples(Material.DARK_OAK_LEAVES)).isTrue();
        assertThat(rule.dropsApples(Material.BIRCH_LEAVES)).isFalse();
    }

    @Test
    @DisplayName("a sapling is planted on soil, a fungus on its nylium")
    void plantable() {
        assertThat(rule.plantable(Material.OAK_SAPLING, Material.GRASS_BLOCK)).isTrue();
        assertThat(rule.plantable(Material.OAK_SAPLING, Material.STONE)).isFalse();
        assertThat(rule.plantable(Material.CRIMSON_FUNGUS, Material.CRIMSON_NYLIUM)).isTrue();
        assertThat(rule.plantable(Material.CRIMSON_FUNGUS, Material.DIRT)).isFalse();
        assertThat(rule.plantable(Material.MANGROVE_PROPAGULE, Material.MUD)).isTrue();
    }

    @Test
    @DisplayName("a felled tree is one kind of log; the canopy reaches a few blocks around it")
    void sameTree() {
        assertThat(rule.sameTree(Material.OAK_LOG, Material.OAK_LOG)).isTrue();
        assertThat(rule.sameTree(Material.OAK_LOG, Material.BIRCH_LOG)).isFalse();
        assertThat(rule.sameTree(Material.OAK_LOG, Material.OAK_PLANKS)).isFalse();
        assertThat(WoodcuttingRule.CANOPY_REACH).isBetween(3, 6);
    }

    @Test
    @DisplayName("a felled tree never takes the last of an axe's durability")
    void timberStops() {
        assertThat(rule.mayFellOn(250, 100)).isTrue();
        assertThat(rule.mayFellOn(250, 249)).isFalse();
    }
}
