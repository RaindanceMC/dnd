package de.raindancer.modules.skills.skill.gathering;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GatherRuleTest {

    private final GatherRule rule = new GatherRule();
    private final ConnectedRule connected = new ConnectedRule();

    @Test
    @DisplayName("a chance in percent is met by a roll below it, never at 0, always at 100")
    void chance() {
        assertThat(rule.chance(25, 0.24)).isTrue();
        assertThat(rule.chance(25, 0.25)).isFalse();
        assertThat(rule.chance(0, 0.0)).isFalse();
        assertThat(rule.chance(100, 0.999)).isTrue();
    }

    @Test
    @DisplayName("three times over is rolled first, then twice over")
    void times() {
        assertThat(rule.times(50, 10, 0.05, 0.9)).isEqualTo(3);
        assertThat(rule.times(50, 10, 0.5, 0.2)).isEqualTo(2);
        assertThat(rule.times(50, 10, 0.5, 0.7)).isEqualTo(1);
        assertThat(rule.times(0, 0, 0.0, 0.0)).isEqualTo(1);
    }

    @Test
    @DisplayName("a bonus in percent on a whole number: the fraction by chance, never less than it was")
    void scaled() {
        assertThat(rule.scaled(4, 50, 0.9)).isEqualTo(6);
        assertThat(rule.scaled(3, 50, 0.4)).isEqualTo(5);
        assertThat(rule.scaled(3, 50, 0.6)).isEqualTo(4);
        assertThat(rule.scaled(0, 100, 0.0)).isZero();
        assertThat(rule.scaled(5, 0, 0.0)).isEqualTo(5);
    }

    @Test
    @DisplayName("tools by kind — a pickaxe is no axe")
    void tools() {
        assertThat(rule.tool(Material.DIAMOND_PICKAXE)).isEqualTo(GatherRule.Tool.PICKAXE);
        assertThat(rule.tool(Material.IRON_AXE)).isEqualTo(GatherRule.Tool.AXE);
        assertThat(rule.tool(Material.WOODEN_SHOVEL)).isEqualTo(GatherRule.Tool.SHOVEL);
        assertThat(rule.tool(Material.BRUSH)).isEqualTo(GatherRule.Tool.BRUSH);
        assertThat(rule.tool(Material.STICK)).isEqualTo(GatherRule.Tool.NONE);
        assertThat(rule.tool(null)).isEqualTo(GatherRule.Tool.NONE);
    }

    @Test
    @DisplayName("silk touch keeps the block whole, so nothing it drops is multiplied")
    void silk() {
        assertThat(rule.multiplies(true)).isFalse();
        assertThat(rule.multiplies(false)).isTrue();
    }

    @Test
    @DisplayName("connected blocks: corners count, the start does not, no further than the reach, no more than asked")
    void connectedBlocks() {
        Set<Spot> vein = new HashSet<>(List.of(new Spot(0, 0, 0), new Spot(1, 0, 0), new Spot(2, 1, 0),
                new Spot(3, 2, 1), new Spot(9, 0, 0)));
        List<Spot> found = connected.connected(new Spot(0, 0, 0), vein::contains, 10, 8, false);
        assertThat(found).containsExactlyInAnyOrder(new Spot(1, 0, 0), new Spot(2, 1, 0), new Spot(3, 2, 1));
        assertThat(connected.connected(new Spot(0, 0, 0), vein::contains, 2, 8, false)).hasSize(2);
        assertThat(connected.connected(new Spot(0, 0, 0), vein::contains, 10, 2, false))
                .containsExactlyInAnyOrder(new Spot(1, 0, 0), new Spot(2, 1, 0));
        assertThat(connected.connected(new Spot(0, 0, 0), vein::contains, 0, 8, false)).isEmpty();
    }

    @Test
    @DisplayName("a tree is felled upwards and sideways, never into the ground below the cut")
    void notBelow() {
        Set<Spot> trunk = Set.of(new Spot(0, 0, 0), new Spot(0, 1, 0), new Spot(0, -1, 0), new Spot(1, 1, 0));
        assertThat(connected.connected(new Spot(0, 0, 0), trunk::contains, 10, 8, true))
                .containsExactlyInAnyOrder(new Spot(0, 1, 0), new Spot(1, 1, 0));
    }

    @Test
    @DisplayName("spots where falling sand or gravel landed are remembered, the oldest forgotten first")
    void fallen() {
        FallenSpots fallen = new FallenSpots(2);
        fallen.landed("w", 1, 2, 3);
        assertThat(fallen.contains("w", 1, 2, 3)).isTrue();
        assertThat(fallen.contains("x", 1, 2, 3)).isFalse();
        fallen.landed("w", 4, 5, 6);
        fallen.landed("w", 7, 8, 9);
        assertThat(fallen.contains("w", 1, 2, 3)).isFalse();
        fallen.gone("w", 7, 8, 9);
        assertThat(fallen.contains("w", 7, 8, 9)).isFalse();
        assertThat(fallen.contains("w", 4, 5, 6)).isTrue();
    }
}
