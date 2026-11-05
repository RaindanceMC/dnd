package de.raindancer.modules.skills.skill.mining;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MiningRuleTest {

    private final MiningRule rule = new MiningRule();

    @Test
    @DisplayName("a vein is one ore, deepslate or not; stone and another ore are not part of it")
    void vein() {
        assertThat(rule.sameVein(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE)).isTrue();
        assertThat(rule.sameVein(Material.IRON_ORE, Material.COPPER_ORE)).isFalse();
        assertThat(rule.sameVein(Material.IRON_ORE, Material.STONE)).isFalse();
        assertThat(rule.sameVein(Material.STONE, Material.STONE)).isFalse();
        assertThat(rule.ore(Material.ANCIENT_DEBRIS)).isTrue();
        assertThat(rule.ore(Material.COBBLESTONE)).isFalse();
    }

    @Test
    @DisplayName("the rarer the ore, the smaller its chance to drop twice — coal all of it, ancient debris two fifths")
    void rarerOresDoubleLess() {
        assertThat(rule.byRarity(50, Material.COAL_ORE)).isEqualTo(50);
        assertThat(rule.byRarity(50, Material.ANCIENT_DEBRIS)).isCloseTo(20, within(1e-9));
        assertThat(rule.byRarity(50, Material.DEEPSLATE_IRON_ORE)).isBetween(38.0, 42.0);
        assertThat(rule.byRarity(50, Material.DIAMOND_ORE)).isBetween(23.0, 26.0);
        assertThat(rule.byRarity(50, Material.EMERALD_ORE)).isLessThan(rule.byRarity(50, Material.DIAMOND_ORE))
                .isGreaterThan(20);
        assertThat(rule.byRarity(50, Material.STONE)).isZero();
    }

    @Test
    @DisplayName("haste: seconds to ticks, level II for the delver's mastery, nothing for nothing")
    void haste() {
        assertThat(rule.hasteTicks(6)).isEqualTo(120);
        assertThat(rule.hasteTicks(0)).isZero();
        assertThat(rule.hasteAmplifier(true)).isEqualTo(1);
        assertThat(rule.hasteAmplifier(false)).isZero();
        assertThat(rule.replacesHaste(0, 40, 0, 120)).isTrue();
        assertThat(rule.replacesHaste(1, 400, 0, 120)).as("a beacon's Haste II stays").isFalse();
        assertThat(rule.replacesHaste(0, 400, 0, 120)).as("a longer haste stays").isFalse();
    }

    @Test
    @DisplayName("night vision deep down: below y 0 in the overworld only; only ours is taken off again")
    void deepSight() {
        assertThat(rule.deep(-1, true)).isTrue();
        assertThat(rule.deep(0, true)).isFalse();
        assertThat(rule.deep(-30, false)).as("the nether has no deep").isFalse();
        assertThat(rule.ours(true, false, MiningRule.NIGHT_VISION_TICKS)).isTrue();
        assertThat(rule.ours(false, true, 3_600)).as("a potion's").isFalse();
        assertThat(rule.ours(true, false, PotionEffect.INFINITE_DURATION))
                .as("the Night Vision enchantment's endless one").isFalse();
        assertThat(rule.needsNightVision(MiningRule.NIGHT_VISION_TICKS - 100)).isFalse();
        assertThat(rule.needsNightVision(200)).as("before it starts to flicker").isTrue();
    }

    @Test
    @DisplayName("a vein never takes the last of a pickaxe's durability")
    void veinStopsBeforeThePickBreaks() {
        assertThat(rule.mayVeinOn(1561, 1500)).isTrue();
        assertThat(rule.mayVeinOn(1561, 1559)).isFalse();
        assertThat(rule.mayVeinOn(0, 0)).as("an unbreakable tool").isTrue();
    }
}
