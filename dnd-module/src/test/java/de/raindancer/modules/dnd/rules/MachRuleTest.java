package de.raindancer.modules.dnd.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MachRuleTest {

    private final MachRule rule = new MachRule();

    @Test
    @DisplayName("gliding with it in you: along the look at the potion's speed, in blocks a tick")
    void velocity() {
        double[] v = rule.velocity(new double[]{0.6, -0.8, 0}, 600);
        assertThat(v[0]).isCloseTo(18, within(1e-9));
        assertThat(v[1]).isCloseTo(-24, within(1e-9));
        assertThat(Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])).isCloseTo(30, within(1e-9));
    }

    @Test
    @DisplayName("it works for its seconds; crashes and falls are forgiven a few seconds longer")
    void timing() {
        long drank = 1_000_000;
        assertThat(rule.working(drank, drank + 19_999, 20)).isTrue();
        assertThat(rule.working(drank, drank + 20_000, 20)).isFalse();
        assertThat(rule.shielded(drank, drank + 20_000 + MachRule.GRACE_MILLIS - 1, 20)).isTrue();
        assertThat(rule.shielded(drank, drank + 20_000 + MachRule.GRACE_MILLIS, 20)).isFalse();
    }

    @Test
    @DisplayName("brewed from any plain potion bottle: not a splash or lingering one, not an adventurer's own potion")
    void brewable() {
        assertThat(rule.brewsFrom(org.bukkit.Material.POTION, false)).isTrue();
        assertThat(rule.brewsFrom(org.bukkit.Material.POTION, true)).as("already magic").isFalse();
        assertThat(rule.brewsFrom(org.bukkit.Material.SPLASH_POTION, false)).isFalse();
        assertThat(rule.brewsFrom(org.bukkit.Material.GLASS_BOTTLE, false)).isFalse();
    }
}
