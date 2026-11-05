package de.raindancer.modules.skills.skill.cooking;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CookingRuleTest {

    private final CookingRule rule = new CookingRule();

    @Test
    @DisplayName("food fills more: hunger by share and chance, saturation by share")
    void fills() {
        assertThat(rule.moreFood(8, 0, 0)).isZero();
        assertThat(rule.moreFood(8, 25, 0.99)).isEqualTo(2);
        assertThat(rule.moreFood(6, 25, 0.4)).as("1.5").isEqualTo(2);
        assertThat(rule.moreFood(6, 25, 0.6)).isOne();
        assertThat(rule.moreSaturation(12.8f, 50)).isEqualTo(6.4f);
        assertThat(rule.moreSaturation(12.8f, 0)).isZero();
    }

    @Test
    @DisplayName("a cooked meal's blessing fits it; raw food and golden food have none")
    void blessings() {
        assertThat(rule.blessing(Material.COOKED_BEEF)).map(CookingRule.Blessing::effect).contains("resistance");
        assertThat(rule.blessing(Material.COOKED_SALMON)).map(CookingRule.Blessing::effect)
                .contains("water_breathing");
        assertThat(rule.blessing(Material.BREAD)).map(CookingRule.Blessing::effect).contains("haste");
        assertThat(rule.blessing(Material.RABBIT_STEW)).map(CookingRule.Blessing::effect).contains("regeneration");
        assertThat(rule.blessing(Material.PUMPKIN_PIE)).map(CookingRule.Blessing::effect).contains("speed");
        assertThat(rule.blessing(Material.BEEF)).isEmpty();
        assertThat(rule.blessing(Material.GOLDEN_APPLE)).isEmpty();
        assertThat(rule.blessing(Material.COOKED_BEEF).orElseThrow().ticks(30)).isEqualTo(600);
        assertThat(rule.blessing(Material.MUSHROOM_STEW).orElseThrow().ticks(30)).as("regeneration is a third")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("faster cooking, longer fuel, Master Chef's moment")
    void kitchen() {
        assertThat(rule.cookTime(200, 0, false)).isEqualTo(200);
        assertThat(rule.cookTime(200, 50, false)).isEqualTo(100);
        assertThat(rule.cookTime(200, 99, false)).as("never quicker than a second").isEqualTo(20);
        assertThat(rule.cookTime(200, 0, true)).isEqualTo(CookingRule.MASTER_CHEF_TICKS);
        assertThat(rule.burnTime(1_600, 50)).isEqualTo(2_400);
        assertThat(rule.queasy("hunger")).isTrue();
        assertThat(rule.queasy("poison")).isTrue();
        assertThat(rule.queasy("nausea")).isTrue();
        assertThat(rule.queasy("absorption")).as("a golden apple's").isFalse();
        assertThat(rule.shared(7)).isEqualTo(3);
    }
}
