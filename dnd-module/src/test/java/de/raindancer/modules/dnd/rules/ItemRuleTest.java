package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import de.raindancer.modules.dnd.model.MagicItem;
import de.raindancer.modules.dnd.model.Spell;
import de.raindancer.modules.dnd.model.Subclass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ItemRuleTest {

    private final ItemRule rule = new ItemRule();

    @Test
    @DisplayName("a scroll's save DC and attack bonus follow its spell level")
    void scrollNumbers() {
        assertThat(rule.scrollDc(0)).isEqualTo(13);
        assertThat(rule.scrollAttack(2)).isEqualTo(5);
        assertThat(rule.scrollDc(3)).isEqualTo(15);
        assertThat(rule.scrollDc(5)).isEqualTo(17);
        assertThat(rule.scrollDc(7)).isEqualTo(18);
        assertThat(rule.scrollAttack(9)).isEqualTo(11);
    }

    @Test
    @DisplayName("a scroll is read by its own classes, and by a thief from level 13")
    void whoReads() {
        Hero wizard = Hero.fresh(UUID.randomUUID(), HeroClass.WIZARD, List.of());
        Hero fighter = Hero.fresh(UUID.randomUUID(), HeroClass.FIGHTER, List.of());
        Hero thief = Hero.fresh(UUID.randomUUID(), HeroClass.ROGUE, List.of()).withLevel(13, 0, 0)
                .withSubclass(Subclass.THIEF);
        assertThat(rule.mayRead(wizard, Spell.FIREBALL)).isTrue();
        assertThat(rule.mayRead(wizard, Spell.CURE_WOUNDS)).isFalse();
        assertThat(rule.mayRead(fighter, Spell.FIREBALL)).isFalse();
        assertThat(rule.mayRead(thief, Spell.CURE_WOUNDS)).isTrue();
        assertThat(rule.mayRead(null, Spell.FIREBALL)).isFalse();
    }

    @Test
    @DisplayName("a wand wins back a charge every few minutes and never holds more than seven")
    void wand() {
        long step = MagicItem.WAND_RECHARGE_MILLIS;
        assertThat(rule.charges(3, 1_000, 1_000)).isEqualTo(3);
        assertThat(rule.charges(3, 1_000, 1_000 + step)).isEqualTo(4);
        assertThat(rule.charges(3, 1_000, 1_000 + step * 2 + 5)).isEqualTo(5);
        assertThat(rule.charges(6, 0, 1_000 + step * 100)).isEqualTo(MagicItem.WAND_CHARGES);
        assertThat(rule.chargedAt(3, 1_000, 1_000 + step + 10)).as("the part-way minutes are kept")
                .isEqualTo(1_000 + step);
    }
}
