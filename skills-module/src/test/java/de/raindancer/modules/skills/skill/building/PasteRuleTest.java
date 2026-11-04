package de.raindancer.modules.skills.skill.building;

import de.raindancer.core.world.safety.Spot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class PasteRuleTest {

    private final PasteRule rule = new PasteRule();

    /** A little world: everything air but what is put in it. */
    private static final class World implements PasteRule.Site {
        final Map<Spot, String> blocks = new HashMap<>();
        final Set<Spot> chests = new java.util.HashSet<>();

        @Override
        public String stateAt(Spot spot) {
            return blocks.getOrDefault(spot, "minecraft:air");
        }

        @Override
        public boolean holdsMore(Spot spot) {
            return chests.contains(spot);
        }

        @Override
        public boolean unbreakable(Spot spot) {
            return stateAt(spot).startsWith("minecraft:bedrock");
        }
    }

    private static Spot at(int x, int y, int z) {
        return new Spot("w", x, y, z);
    }

    private static PasteRule.Placed stone(int x, int y, int z) {
        return new PasteRule.Placed(at(x, y, z), new BlueprintRule.Piece("minecraft:stone", Map.of("stone", 1)));
    }

    private static PasteRule.Request request(List<PasteRule.Placed> pieces, World world, Map<String, Integer> have) {
        return new PasteRule.Request(pieces, world, spot -> true, spot -> true, false, 0, have);
    }

    @Test
    @DisplayName("enough in the inventory: every block placed on air, and that much taken")
    void paid() {
        World world = new World();
        PasteRule.Plan plan = rule.plan(request(List.of(stone(0, 0, 0), stone(1, 0, 0)), world,
                Map.of("stone", 5)), new SplittableRandom(1));
        assertThat(plan.ok()).isTrue();
        assertThat(plan.steps()).hasSize(2);
        assertThat(plan.cost()).isEqualTo(Map.of("stone", 2));
    }

    @Test
    @DisplayName("not enough: it says what is missing and places nothing")
    void missing() {
        PasteRule.Plan plan = rule.plan(request(List.of(stone(0, 0, 0), stone(1, 0, 0), stone(2, 0, 0)), new World(),
                Map.of("stone", 1)), new SplittableRandom(1));
        assertThat(plan.ok()).isFalse();
        assertThat(plan.missing()).isEqualTo(Map.of("stone", 2));
        assertThat(plan.steps()).isEmpty();
    }

    @Test
    @DisplayName("what stands there stays, and costs nothing; a block already right is left alone")
    void neverRemoves() {
        World world = new World();
        world.blocks.put(at(0, 0, 0), "minecraft:oak_planks");
        world.blocks.put(at(1, 0, 0), "minecraft:stone");
        PasteRule.Plan plan = rule.plan(request(List.of(stone(0, 0, 0), stone(1, 0, 0), stone(2, 0, 0)), world,
                Map.of("stone", 64)), new SplittableRandom(1));
        assertThat(plan.steps()).extracting(PasteRule.Step::spot).containsExactly(at(2, 0, 0));
        assertThat(plan.occupied()).isEqualTo(1);
        assertThat(plan.cost()).isEqualTo(Map.of("stone", 1));
    }

    @Test
    @DisplayName("Demolition clears what is in the way — but never a chest's contents, nor bedrock")
    void demolition() {
        World world = new World();
        world.blocks.put(at(0, 0, 0), "minecraft:oak_planks");
        world.blocks.put(at(1, 0, 0), "minecraft:chest[facing=north,type=single,waterlogged=false]");
        world.chests.add(at(1, 0, 0));
        world.blocks.put(at(2, 0, 0), "minecraft:bedrock");
        PasteRule.Request asked = new PasteRule.Request(List.of(stone(0, 0, 0), stone(1, 0, 0), stone(2, 0, 0)),
                world, spot -> true, spot -> true, true, 0, Map.of("stone", 64));
        PasteRule.Plan plan = rule.plan(asked, new SplittableRandom(1));
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().getFirst().replaced()).isEqualTo("minecraft:oak_planks");
    }

    @Test
    @DisplayName("one block on land the player may not build on, and nothing is placed")
    void protection() {
        PasteRule.Request asked = new PasteRule.Request(List.of(stone(0, 0, 0), stone(1, 0, 0)), new World(),
                spot -> spot.x() == 0, spot -> true, false, 0, Map.of("stone", 64));
        PasteRule.Plan plan = rule.plan(asked, new SplittableRandom(1));
        assertThat(plan.ok()).isFalse();
        assertThat(plan.refused()).isEqualTo(1);
        assertThat(plan.steps()).isEmpty();
    }

    @Test
    @DisplayName("clearing a block needs leave to break there as well as to build")
    void breakingAsked() {
        World world = new World();
        world.blocks.put(at(0, 0, 0), "minecraft:dirt");
        PasteRule.Request asked = new PasteRule.Request(List.of(stone(0, 0, 0)), world, spot -> true,
                spot -> false, true, 0, Map.of("stone", 64));
        assertThat(rule.plan(asked, new SplittableRandom(1)).refused()).isEqualTo(1);
    }

    @Test
    @DisplayName("Thrift: some blocks free — marked so — never half a door")
    void thrift() {
        List<PasteRule.Placed> many = new java.util.ArrayList<>();
        for (int x = 0; x < 200; x++) {
            many.add(stone(x, 0, 0));
        }
        many.add(new PasteRule.Placed(at(0, 1, 5), new BlueprintRule.Piece(
                "minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]",
                Map.of("oak_door", 1))));
        many.add(new PasteRule.Placed(at(0, 2, 5), new BlueprintRule.Piece(
                "minecraft:oak_door[facing=north,half=upper,hinge=left,open=false,powered=false]", Map.of())));
        PasteRule.Request asked = new PasteRule.Request(many, new World(), spot -> true, spot -> true, false, 100,
                Map.of("stone", 0, "oak_door", 1));
        PasteRule.Plan plan = rule.plan(asked, new SplittableRandom(1));
        assertThat(plan.ok()).isTrue();
        assertThat(plan.cost()).isEqualTo(Map.of("oak_door", 1));
        assertThat(plan.steps().stream().filter(PasteRule.Step::free).count()).isEqualTo(200);
    }

    @Test
    @DisplayName("the second half of a door or a bed only with its first")
    void halves() {
        World world = new World();
        world.blocks.put(at(0, 0, 0), "minecraft:dirt");
        PasteRule.Placed lower = new PasteRule.Placed(at(0, 0, 0), new BlueprintRule.Piece(
                "minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]",
                Map.of("oak_door", 1)));
        PasteRule.Placed upper = new PasteRule.Placed(at(0, 1, 0), new BlueprintRule.Piece(
                "minecraft:oak_door[facing=north,half=upper,hinge=left,open=false,powered=false]", Map.of()));
        PasteRule.Placed head = new PasteRule.Placed(at(5, 0, 4), new BlueprintRule.Piece(
                "minecraft:red_bed[facing=north,occupied=false,part=head]", Map.of()));
        PasteRule.Plan plan = rule.plan(request(List.of(lower, upper, head), world, Map.of("oak_door", 1)),
                new SplittableRandom(1));
        assertThat(plan.steps()).as("the lower half is blocked, the head has no foot").isEmpty();
        assertThat(PasteRule.partner(at(5, 0, 4), "minecraft:red_bed[facing=north,occupied=false,part=head]"))
                .isEqualTo(at(5, 0, 5));
        assertThat(PasteRule.partner(at(5, 0, 4), "minecraft:red_bed[facing=east,occupied=false,part=head]"))
                .isEqualTo(at(4, 0, 4));
    }
}
