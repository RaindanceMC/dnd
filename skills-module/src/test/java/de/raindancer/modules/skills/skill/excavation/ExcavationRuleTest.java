package de.raindancer.modules.skills.skill.excavation;

import de.raindancer.modules.skills.skill.gathering.Loot;
import de.raindancer.modules.skills.skill.gathering.Spot;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExcavationRuleTest {

    private final ExcavationRule rule = new ExcavationRule();

    @Test
    @DisplayName("soft ground is dirt, sand, gravel and clay the way Core counts digging — never stone or cobble")
    void soft() {
        assertThat(rule.soft(Material.DIRT)).isTrue();
        assertThat(rule.soft(Material.GRAVEL)).isTrue();
        assertThat(rule.soft(Material.CLAY)).isTrue();
        assertThat(rule.soft(Material.STONE)).isFalse();
        assertThat(rule.soft(Material.COBBLESTONE)).isFalse();
    }

    @Test
    @DisplayName("a treasure: only below the chance; a rare one only with the mastery, and then one in ten")
    void treasure() {
        assertThat(rule.treasure(2, false, 0.03, 0.5)).isEmpty();
        Optional<Loot> found = rule.treasure(2, false, 0.01, 0.5);
        assertThat(found).isPresent();
        assertThat(ExcavationRule.RARE).extracting(Loot::material).doesNotContain(found.get().material());
        Set<Material> seen = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            rule.treasure(100, false, 0.0, i / 1_000.0).ifPresent(loot -> seen.add(loot.material()));
        }
        assertThat(seen).doesNotContain(Material.DIAMOND, Material.EMERALD);
        Set<Material> rare = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            rule.treasure(100, true, 0.0, i / 1_000.0).ifPresent(loot -> rare.add(loot.material()));
        }
        assertThat(rare).contains(Material.DIAMOND);
        assertThat(rule.treasure(100, true, 0.0, 0.95)).get().extracting(Loot::material)
                .isIn(ExcavationRule.RARE.stream().map(Loot::material).toList());
    }

    @Test
    @DisplayName("three by three: across the way you look, the block you dug in the middle and not in the list")
    void plane() {
        Spot at = new Spot(10, 64, 10);
        assertThat(rule.plane(at, 80, 0)).as("looking down").hasSize(8).allMatch(spot -> spot.y() == 64)
                .doesNotContain(at);
        assertThat(rule.plane(at, 0, 0)).as("looking south, along z").allMatch(spot -> spot.z() == 10);
        assertThat(rule.plane(at, 0, 90)).as("looking west, along x").allMatch(spot -> spot.x() == 10);
        assertThat(rule.plane(at, 10, 180)).hasSize(8).doesNotContain(at);
    }

    @Test
    @DisplayName("a brushed find is the item that pops out of the very block being brushed, a moment later")
    void brushFind() {
        assertThat(rule.brushFind(1_000, 1_000 + 3_000, 0.5, 1.1, 0.5)).isTrue();
        assertThat(rule.brushFind(1_000, 1_000 + ExcavationRule.BRUSH_MILLIS + 1, 0.5, 1.1, 0.5)).isFalse();
        assertThat(rule.brushFind(1_000, 1_500, 3.0, 1.0, 0.5)).as("somewhere else").isFalse();
        assertThat(rule.brushFind(0, 1_500, 0.5, 1.0, 0.5)).as("nobody brushed").isFalse();
    }

    @Test
    @DisplayName("Keen Eye looks for suspicious sand and gravel only")
    void suspicious() {
        assertThat(rule.suspicious(Material.SUSPICIOUS_SAND)).isTrue();
        assertThat(rule.suspicious(Material.SUSPICIOUS_GRAVEL)).isTrue();
        assertThat(rule.suspicious(Material.SAND)).isFalse();
    }
}
