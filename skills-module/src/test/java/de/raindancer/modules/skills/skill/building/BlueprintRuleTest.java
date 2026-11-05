package de.raindancer.modules.skills.skill.building;

import de.raindancer.core.world.safety.Spot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class BlueprintRuleTest {

    private final BlueprintRule rule = new BlueprintRule();

    /** What the server's getPlacementMaterial says, for the blocks these tests use. */
    private static final Map<String, String> ITEMS = Map.ofEntries(
            Map.entry("minecraft:wheat", "wheat_seeds"), Map.entry("minecraft:oak_door", "oak_door"),
            Map.entry("minecraft:red_bed", "red_bed"), Map.entry("minecraft:oak_slab", "oak_slab"),
            Map.entry("minecraft:sea_pickle", "sea_pickle"), Map.entry("minecraft:cauldron", "cauldron"),
            Map.entry("minecraft:composter", "composter"), Map.entry("minecraft:flower_pot", "flower_pot"),
            Map.entry("minecraft:cactus", "cactus"), Map.entry("minecraft:piston", "piston"),
            Map.entry("minecraft:respawn_anchor", "respawn_anchor"), Map.entry("minecraft:beehive", "beehive"),
            Map.entry("minecraft:snow", "snow"), Map.entry("minecraft:cave_vines", "glow_berries"),
            Map.entry("minecraft:stone", "stone"), Map.entry("minecraft:wall_torch", "torch"),
            Map.entry("minecraft:azalea", "azalea"), Map.entry("minecraft:grass_block", "grass_block"),
            Map.entry("minecraft:dirt", "dirt"));
    private static final Function<String, Optional<String>> ITEM_OF = id -> Optional.ofNullable(ITEMS.get(id));

    private BlueprintRule.Piece piece(String state) {
        return rule.piece(state, ITEM_OF).orElseThrow(() -> new AssertionError(state + " is not placed"));
    }

    @Test
    @DisplayName("a block costs its own item; a slab double, pickles, candles and layers as many as there are")
    void counts() {
        assertThat(piece("minecraft:stone").items()).isEqualTo(Map.of("stone", 1));
        assertThat(piece("minecraft:wall_torch[facing=north]").items()).isEqualTo(Map.of("torch", 1));
        assertThat(piece("minecraft:oak_slab[type=double,waterlogged=false]").items()).isEqualTo(Map.of("oak_slab", 2));
        assertThat(piece("minecraft:oak_slab[type=top,waterlogged=false]").items()).isEqualTo(Map.of("oak_slab", 1));
        assertThat(piece("minecraft:sea_pickle[pickles=3,waterlogged=false]").items()).isEqualTo(Map.of("sea_pickle", 3));
        assertThat(piece("minecraft:snow[layers=4]").items()).isEqualTo(Map.of("snow", 4));
    }

    @Test
    @DisplayName("a door, a tall plant and a bed are one item: the second half is placed for nothing")
    void halves() {
        assertThat(piece("minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]").items())
                .isEqualTo(Map.of("oak_door", 1));
        assertThat(piece("minecraft:oak_door[facing=north,half=upper,hinge=left,open=false,powered=false]").items())
                .isEmpty();
        assertThat(piece("minecraft:red_bed[facing=east,occupied=false,part=foot]").items())
                .isEqualTo(Map.of("red_bed", 1));
        assertThat(piece("minecraft:red_bed[facing=east,occupied=false,part=head]").items()).isEmpty();
    }

    @Test
    @DisplayName("nothing comes out of a paste that was not paid for: crops unripe, cauldrons empty, no free water")
    void noDupes() {
        BlueprintRule.Piece wheat = piece("minecraft:wheat[age=7]");
        assertThat(wheat.state()).isEqualTo("minecraft:wheat[age=0]");
        assertThat(wheat.items()).isEqualTo(Map.of("wheat_seeds", 1));
        assertThat(piece("minecraft:water_cauldron[level=3]").state()).isEqualTo("minecraft:cauldron");
        assertThat(piece("minecraft:lava_cauldron").state()).isEqualTo("minecraft:cauldron");
        assertThat(piece("minecraft:composter[level=7]").state()).isEqualTo("minecraft:composter[level=0]");
        assertThat(piece("minecraft:respawn_anchor[charges=4]").state()).isEqualTo("minecraft:respawn_anchor[charges=0]");
        assertThat(piece("minecraft:beehive[facing=north,honey_level=5]").state())
                .isEqualTo("minecraft:beehive[facing=north,honey_level=0]");
        assertThat(piece("minecraft:sea_pickle[pickles=1,waterlogged=true]").state())
                .isEqualTo("minecraft:sea_pickle[pickles=1,waterlogged=false]");
        assertThat(piece("minecraft:cave_vines[age=3,berries=true]").state())
                .isEqualTo("minecraft:cave_vines[age=0,berries=false]");
        assertThat(piece("minecraft:piston[extended=true,facing=up]").state())
                .isEqualTo("minecraft:piston[extended=false,facing=up]");
        assertThat(piece("minecraft:potted_cactus").items()).isEqualTo(Map.of("flower_pot", 1, "cactus", 1));
        assertThat(piece("minecraft:potted_azalea_bush").items()).isEqualTo(Map.of("flower_pot", 1, "azalea", 1));
        assertThat(piece("minecraft:grass_block[snowy=false]").items()).isEqualTo(Map.of("dirt", 1));
    }

    @Test
    @DisplayName("air, fluids, fire, portals and what has no item are never placed")
    void skipped() {
        for (String state : List.of("minecraft:air", "minecraft:cave_air", "minecraft:water[level=0]",
                "minecraft:lava[level=0]", "minecraft:fire[age=0,east=false,north=false,south=false,up=false,west=false]",
                "minecraft:nether_portal[axis=x]", "minecraft:piston_head[facing=up,short=false,type=normal]",
                "minecraft:moving_piston", "minecraft:light[level=15,waterlogged=false]", "minecraft:bedrock")) {
            assertThat(rule.piece(state, ITEM_OF)).as(state).isEmpty();
        }
    }

    @Test
    @DisplayName("what a paste needs, added up, and what of it the player does not have")
    void missing() {
        Map<String, Integer> needed = rule.needed(List.of(piece("minecraft:stone"), piece("minecraft:stone"),
                piece("minecraft:oak_slab[type=double,waterlogged=false]")));
        assertThat(needed).isEqualTo(Map.of("stone", 2, "oak_slab", 2));
        assertThat(rule.missing(needed, Map.of("stone", 5, "oak_slab", 1))).isEqualTo(Map.of("oak_slab", 1));
        assertThat(rule.missing(needed, Map.of("stone", 2, "oak_slab", 2))).isEmpty();
    }

    @Test
    @DisplayName("a box from two corners in either order")
    void box() {
        BlueprintRule.Box box = BlueprintRule.Box.of(new Spot("w", 10, 64, -3), new Spot("w", 3, 70, 4));
        assertThat(box).isEqualTo(new BlueprintRule.Box("w", 3, 64, -3, 8, 7, 8));
        assertThat(box.longest()).isEqualTo(8);
        assertThat(box.distanceTo(5, 65, 0)).isZero();
        assertThat(box.distanceTo(3, 64, -13)).isEqualTo(10.0);
    }

    @Test
    @DisplayName("saving needs Draftsman, a fair name, a box that fits the ranks, room, and the player near it")
    void saving() {
        BlueprintRule.Box eight = new BlueprintRule.Box("w", 0, 0, 0, 8, 8, 8);
        BlueprintRule.Box nine = new BlueprintRule.Box("w", 0, 0, 0, 9, 2, 2);
        assertThat(rule.maySave(eight, "hut", 0, 0, 0, false, 0)).contains(BlueprintRule.Refusal.NO_DRAFTSMAN);
        assertThat(rule.maySave(eight, "Hut!", 8, 0, 1, false, 0)).contains(BlueprintRule.Refusal.BAD_NAME);
        assertThat(rule.maySave(nine, "hut", 8, 0, 1, false, 0)).contains(BlueprintRule.Refusal.TOO_BIG);
        assertThat(rule.maySave(eight, "hut", 8, 1, 1, false, 0)).contains(BlueprintRule.Refusal.NO_ROOM);
        assertThat(rule.maySave(eight, "hut", 8, 1, 1, true, 0)).as("replacing one is no new room").isEmpty();
        assertThat(rule.maySave(eight, "hut", 8, 0, 1, false, 9)).contains(BlueprintRule.Refusal.TOO_FAR);
        assertThat(rule.maySave(eight, "my-hut_2", 8, 0, 1, false, 8)).isEmpty();
        assertThat(rule.mayPaste(9, 2, 2, 8)).contains(BlueprintRule.Refusal.TOO_BIG);
        assertThat(rule.mayPaste(8, 8, 8, 0)).contains(BlueprintRule.Refusal.NO_DRAFTSMAN);
        assertThat(rule.mayPaste(8, 8, 8, 16)).isEmpty();
    }

    @Test
    @DisplayName("turning clockwise a quarter at a time, as Minecraft's own structure rotation does")
    void turning() {
        assertThat(BlueprintRule.turn(3, 1, 0)).containsExactly(3, 1);
        assertThat(BlueprintRule.turn(3, 1, 1)).containsExactly(-1, 3);
        assertThat(BlueprintRule.turn(3, 1, 2)).containsExactly(-3, -1);
        assertThat(BlueprintRule.turn(3, 1, 3)).containsExactly(1, -3);
        assertThat(BlueprintRule.quarters(270)).contains(3);
        assertThat(BlueprintRule.quarters(45)).isEmpty();
    }
}
