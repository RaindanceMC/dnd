package de.raindancer.modules.skills.skill.artisan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class ArtisanRuleTest {

    private final ArtisanRule rule = new ArtisanRule();

    @Test
    @DisplayName("an extra or a refund only for a real recipe: two kinds of ingredient, nothing that goes round in circles")
    void bonusOnlyForRealRecipes() {
        assertThat(rule.mayBonus("chest", List.of("oak_planks"), false)).as("one kind of ingredient").isFalse();
        assertThat(rule.mayBonus("iron_block", List.of("iron_ingot"), false)).isFalse();
        assertThat(rule.mayBonus("piston", List.of("oak_planks", "cobblestone", "iron_ingot", "redstone"), false))
                .isTrue();
        assertThat(rule.mayBonus("red_wool", List.of("white_wool", "red_dye"), false))
                .as("wool dyed and bleached back is a loop").isFalse();
        assertThat(rule.mayBonus("red_bed", List.of("red_wool", "oak_planks"), false)).isFalse();
        assertThat(rule.mayBonus("filled_map", List.of("filled_map", "map"), false))
                .as("a copy of an ingredient").isFalse();
        assertThat(rule.mayBonus("diamond_pickaxe", List.of("diamond", "stick"), true))
                .as("a tool is made tougher, never twice").isFalse();
    }

    @Test
    @DisplayName("a refund is one ingredient that leaves nothing behind")
    void refund() {
        assertThat(rule.refund(List.of("milk_bucket", "sugar", "egg", "wheat"), 0)).contains("sugar");
        assertThat(rule.refund(List.of("milk_bucket", "honey_bottle"), 3)).isEmpty();
        assertThat(rule.refund(List.of("cobblestone", "redstone"), 5)).contains("redstone");
    }

    @Test
    @DisplayName("a shift-click rolls once for every craft; a minute gives at most so many extras")
    void rolls() {
        assertThat(rule.bonus(10, 0, new SplittableRandom(1))).isZero();
        assertThat(rule.bonus(10, 100, new SplittableRandom(1))).isEqualTo(10);
        int some = rule.bonus(1_000, 25, new SplittableRandom(7));
        assertThat(some).isBetween(180, 320);
        assertThat(rule.allowed(60, 10)).isEqualTo(4);
        assertThat(rule.allowed(64, 3)).isZero();
        assertThat(rule.allowed(0, 3)).isEqualTo(3);
    }

    @Test
    @DisplayName("X% longer lasting: a point of wear is spared X out of 100 + X times")
    void tougher() {
        assertThat(rule.spareWear(0)).isZero();
        assertThat(rule.spareWear(100)).isEqualTo(0.5);
        assertThat(rule.spareWear(50)).isEqualTo(1 / 3.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("faster furnaces and longer fuel, never below a tick")
    void furnaces() {
        assertThat(rule.cookTime(200, 0)).isEqualTo(200);
        assertThat(rule.cookTime(200, 100)).isEqualTo(100);
        assertThat(rule.cookTime(200, 150)).isEqualTo(80);
        assertThat(rule.cookTime(1, 150)).isEqualTo(1);
        assertThat(rule.burnTime(1_600, 50)).isEqualTo(2_400);
        assertThat(rule.burnTime(1_600, 0)).isEqualTo(1_600);
    }

    @Test
    @DisplayName("a double smelt only where both fit, never food (Cooking's) nor a sponge (water makes them again)")
    void doubleSmelt() {
        assertThat(rule.mayDouble("iron_ingot", false, 0, 64)).isTrue();
        assertThat(rule.mayDouble("iron_ingot", false, 62, 64)).isTrue();
        assertThat(rule.mayDouble("iron_ingot", false, 63, 64)).isFalse();
        assertThat(rule.mayDouble("cooked_beef", true, 0, 64)).isFalse();
        assertThat(rule.mayDouble("sponge", false, 0, 64)).isFalse();
    }

    @Test
    @DisplayName("auto-smelt turns raw ores into what a furnace makes of them, and knows a furnace's experience")
    void autoSmelt() {
        assertThat(rule.smelted("raw_iron")).contains("iron_ingot");
        assertThat(rule.smelted("raw_gold")).contains("gold_ingot");
        assertThat(rule.smelted("raw_copper")).contains("copper_ingot");
        assertThat(rule.smelted("ancient_debris")).contains("netherite_scrap");
        assertThat(rule.smelted("cobblestone")).isEqualTo(Optional.empty());
        assertThat(rule.experience("netherite_scrap")).isEqualTo(2.0);
        assertThat(rule.experience("iron_ingot")).isEqualTo(0.7);
        assertThat(rule.orbs(10, 0.7, new SplittableRandom(3))).isBetween(7, 8);
    }
}
