package de.raindancer.modules.skills.skill.exploration;

import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class ExplorationRuleTest {

    private final ExplorationRule rule = new ExplorationRule();

    @Test
    @DisplayName("tired less by the percent, never below nothing")
    void exhaustion() {
        assertThat(rule.exhaustion(0.1f, 0)).isEqualTo(0.1f);
        assertThat(rule.exhaustion(0.1f, 50)).isEqualTo(0.05f, org.assertj.core.data.Offset.offset(1e-6f));
        assertThat(rule.exhaustion(0.1f, 150)).isZero();
    }

    @Test
    @DisplayName("a map's centre lies on the game's own grid for its zoom, so it lines up with every other map")
    void mapCentre() {
        assertThat(rule.centre(0, 0)).isEqualTo(0);
        assertThat(rule.centre(70, 0)).isEqualTo(128);
        assertThat(rule.centre(-65, 0)).isEqualTo(-128);
        assertThat(rule.centre(0, 1)).isEqualTo(64);
        assertThat(rule.centre(500, 4)).isEqualTo(960);
        assertThat(rule.zoom(3)).isEqualTo(3);
        assertThat(rule.zoom(9)).isEqualTo(4);
    }

    @Test
    @DisplayName("a road is a path, gravel, cobble or bricks — slabs and stairs of them too")
    void roads() {
        assertThat(rule.road("dirt_path")).isTrue();
        assertThat(rule.road("gravel")).isTrue();
        assertThat(rule.road("stone_brick_stairs")).isTrue();
        assertThat(rule.road("cobblestone_slab")).isTrue();
        assertThat(rule.road("grass_block")).isFalse();
        assertThat(rule.road("sand")).isFalse();
    }

    @Test
    @DisplayName("the structures a compass looks for depend on the world it is in")
    void structures() {
        assertThat(rule.structures(World.Environment.NORMAL)).contains("village_plains", "shipwreck", "desert_pyramid");
        assertThat(rule.structures(World.Environment.NETHER)).contains("fortress", "bastion_remnant");
        assertThat(rule.structures(World.Environment.THE_END)).containsExactly("end_city");
    }

    @Test
    @DisplayName("one more of something stackable in a structure's chest, as often as the percent says")
    void loot() {
        assertThat(rule.extraPick(List.of(64, 1, 16), new SplittableRandom(3))).isIn(0, 2);
        assertThat(rule.extraPick(List.of(1, 1), new SplittableRandom(3))).isEqualTo(-1);
        assertThat(rule.chestLoot("minecraft:chests/simple_dungeon")).isTrue();
        assertThat(rule.chestLoot("minecraft:entities/zombie")).isFalse();
    }

    @Test
    @DisplayName("which way someone faces, as a compass point")
    void facing() {
        assertThat(rule.point(0)).isEqualTo("S");
        assertThat(rule.point(90)).isEqualTo("W");
        assertThat(rule.point(-90)).isEqualTo("E");
        assertThat(rule.point(180)).isEqualTo("N");
        assertThat(rule.point(-135)).isEqualTo("NE");
    }
}
