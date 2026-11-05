package de.raindancer.modules.skills.skill.building;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuildingRuleTest {

    private final BuildingRule rule = new BuildingRule();

    @Test
    @DisplayName("a refund only for a plain full block: nothing that falls, holds things, decays or breaks to something else")
    void refundable() {
        assertThat(rule.mayRefund("stone_bricks", true, true, false, false)).isTrue();
        assertThat(rule.mayRefund("sand", true, true, true, false)).as("falls, and lands as an item").isFalse();
        assertThat(rule.mayRefund("chest", true, false, false, true)).isFalse();
        assertThat(rule.mayRefund("torch", false, false, false, false)).isFalse();
        assertThat(rule.mayRefund("oak_leaves", true, true, false, false)).as("decays into saplings").isFalse();
        assertThat(rule.mayRefund("diamond_ore", true, true, false, false)).as("breaks to diamonds").isFalse();
        assertThat(rule.mayRefund("glowstone", true, true, false, false)).as("breaks to dust").isFalse();
    }

    @Test
    @DisplayName("climbing faster: vanilla's 0.2 a tick, more by the percent, never a launch")
    void climbing() {
        assertThat(rule.climb(0)).isEqualTo(0.2);
        assertThat(rule.climb(50)).isEqualTo(0.3, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(rule.climb(1000)).isEqualTo(0.4);
    }

    @Test
    @DisplayName("quick undo: the player's own block, placed in the last five seconds")
    void quickUndo() {
        assertThat(rule.undoable(10_000, 14_999)).isTrue();
        assertThat(rule.undoable(10_000, 15_001)).isFalse();
    }

    @Test
    @DisplayName("a free spot packs into one number per chunk and back, from the bottom of the world to its top")
    void packing() {
        for (int[] spot : new int[][]{{0, -64, 0}, {15, 319, 15}, {-1, 70, -17}, {33, 2_000, 7}}) {
            int packed = FreeBlocks.pack(spot[0], spot[1], spot[2]);
            int[] back = FreeBlocks.unpack(packed);
            assertThat(back[0]).isEqualTo(Math.floorMod(spot[0], 16));
            assertThat(back[1]).isEqualTo(spot[1]);
            assertThat(back[2]).isEqualTo(Math.floorMod(spot[2], 16));
        }
        assertThat(FreeBlocks.with(new int[]{1, 2}, 3)).containsExactly(1, 2, 3);
        assertThat(FreeBlocks.with(new int[]{1, 2}, 2)).containsExactly(1, 2);
        assertThat(FreeBlocks.without(new int[]{1, 2, 3}, 2)).containsExactly(1, 3);
    }
}
