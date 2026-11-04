package de.raindancer.modules.skills.skill.smithing;

import de.raindancer.modules.skills.skill.gathering.Loot;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SmithingRuleTest {

    private final SmithingRule rule = new SmithingRule();

    @Test
    @DisplayName("anvil work: fewer levels, never below one; a Master Smith's never above 39")
    void cost() {
        assertThat(rule.cost(10, 30, false)).isEqualTo(7);
        assertThat(rule.cost(1, 60, false)).isEqualTo(1);
        assertThat(rule.cost(0, 60, false)).isZero();
        assertThat(rule.cost(55, 0, true)).isEqualTo(39);
        assertThat(rule.cost(55, 0, false)).isEqualTo(55);
        assertThat(rule.cost(50, 20, true)).isEqualTo(39);
    }

    @Test
    @DisplayName("prior work grows less, but never below what the items brought")
    void priorWork() {
        assertThat(rule.priorWork(3, 7, 50)).isEqualTo(5);
        assertThat(rule.priorWork(3, 7, 100)).isEqualTo(3);
        assertThat(rule.priorWork(3, 7, 0)).isEqualTo(7);
        assertThat(rule.priorWork(7, 3, 50)).isEqualTo(3);
    }

    @Test
    @DisplayName("a repair with materials mends more, never past whole")
    void mended() {
        assertThat(rule.mended(1000, 600, 50)).isEqualTo(400);
        assertThat(rule.mended(1000, 0, 100)).isZero();
        assertThat(rule.mended(1000, 600, 0)).isEqualTo(600);
        assertThat(rule.mended(600, 600, 100)).as("nothing was repaired").isEqualTo(600);
    }

    @Test
    @DisplayName("salvage gives back at most what went in, less the wear, scaled by the skill")
    void salvage() {
        assertThat(rule.salvage(Material.IRON_PICKAXE, 0, 250, 100, 0.99))
                .containsExactly(new Loot(Material.IRON_INGOT, 3));
        assertThat(rule.salvage(Material.DIAMOND_CHESTPLATE, 200, 528, 50, 0.99))
                .containsExactly(new Loot(Material.DIAMOND, 2));
        assertThat(rule.salvage(Material.DIAMOND_CHESTPLATE, 200, 528, 50, 0.0))
                .containsExactly(new Loot(Material.DIAMOND, 3));
        List<Loot> netherite = rule.salvage(Material.NETHERITE_SWORD, 0, 2031, 100, 0.99);
        assertThat(netherite).containsExactly(new Loot(Material.DIAMOND, 2), new Loot(Material.NETHERITE_SCRAP, 4));
        assertThat(rule.salvage(Material.IRON_PICKAXE, 249, 250, 80, 0.99)).isEmpty();
        assertThat(rule.salvage(Material.STICK, 0, 0, 100, 0.0)).isEmpty();
        assertThat(rule.salvageable(Material.GOLDEN_BOOTS)).isTrue();
        assertThat(rule.salvageable(Material.WOODEN_SWORD)).isFalse();
        for (Material material : Material.values()) {
            if (!material.isLegacy() && rule.salvageable(material)) {
                assertThat(rule.salvage(material, 0, 100, 100, 0.0).stream().mapToInt(Loot::amount).sum())
                        .as(material + " gives back no more than it took").isLessThanOrEqualTo(12);
            }
        }
    }

    @Test
    @DisplayName("the best enchantment for the salvaged book: the highest level, then by name")
    void bestEnchantment() {
        assertThat(rule.best(Map.of("sharpness", 3, "unbreaking", 3, "looting", 2))).contains("sharpness");
        assertThat(rule.best(Map.of("efficiency", 5, "unbreaking", 3))).contains("efficiency");
        assertThat(rule.best(Map.of())).isEmpty();
    }

    @Test
    @DisplayName("gear is armour and weapons; tools are for the gathering skills")
    void gear() {
        assertThat(rule.gear(Material.IRON_CHESTPLATE)).isTrue();
        assertThat(rule.gear(Material.DIAMOND_SWORD)).isTrue();
        assertThat(rule.gear(Material.BOW)).isTrue();
        assertThat(rule.gear(Material.ELYTRA)).isTrue();
        assertThat(rule.gear(Material.IRON_PICKAXE)).isFalse();
        assertThat(rule.netheriteUpgrade(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Material.NETHERITE_SWORD))
                .isTrue();
        assertThat(rule.netheriteUpgrade(Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, Material.NETHERITE_SWORD))
                .isFalse();
    }

    @Test
    @DisplayName("a template is given back only if the work really took one — not if the window was closed")
    void templateTaken() {
        assertThat(rule.templateTaken(5, 4)).isTrue();
        assertThat(rule.templateTaken(5, 5)).isFalse();
        assertThat(rule.templateTaken(5, 3)).as("two at once is not one smithing").isFalse();
    }
}
