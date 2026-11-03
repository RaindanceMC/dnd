package de.raindancer.modules.skills.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

    @Test
    @DisplayName("a hero keeps its level in range and only the ranks actually taken")
    void heroIsTidy() {
        UUID ana = UUID.randomUUID();
        Map<String, Integer> nodes = new HashMap<>();
        nodes.put("toughness", 2);
        nodes.put("nothing", 0);
        nodes.put("negative", -3);
        Hero hero = new Hero(ana, 500, -10, nodes, -1);
        assertThat(hero.level()).isEqualTo(Hero.MOST_LEVEL);
        assertThat(hero.xp()).isZero();
        assertThat(hero.nodes()).containsOnlyKeys("toughness");
        assertThat(hero.respecAt()).isZero();
        assertThat(new Hero(ana, 0, 0, null, 0).level()).isEqualTo(1);
        assertThat(hero.withRank("brawler", 1).rank("brawler")).isEqualTo(1);
        assertThat(hero.unlearnt(5).nodes()).isEmpty();
        assertThat(hero.unlearnt(5).respecAt()).isEqualTo(5);
    }

    @Test
    @DisplayName("dice: averages, maxima and how they read")
    void dice() {
        Dice dice = new Dice(2, 4, 2);
        assertThat(dice.bonus()).isEqualTo(2);
        assertThat(Dice.flat(3).bonus()).isEqualTo(3);
    }

    @Test
    @DisplayName("magic items: ids round-trip, weapons and potions are what they say")
    void items() {
        for (MagicItem item : MagicItem.values()) {
            assertThat(MagicItem.byId(item.id())).contains(item);
        }
        assertThat(MagicItem.WEAPON_PLUS_TWO.isWeapon()).isTrue();
        assertThat(MagicItem.POTION_OF_HEALING.isPotion()).isTrue();
        assertThat(MagicItem.AMULET_OF_HEALTH.amount().bonus()).as("hearts").isEqualTo(2);
    }

    @Test
    @DisplayName("scroll kinds: ids round-trip, tiers clamp to 1..5")
    void scrolls() {
        for (Scroll scroll : Scroll.values()) {
            assertThat(Scroll.byId(scroll.id())).contains(scroll);
        }
        assertThat(Scroll.clampTier(0)).isEqualTo(1);
        assertThat(Scroll.clampTier(9)).isEqualTo(5);
    }
}
