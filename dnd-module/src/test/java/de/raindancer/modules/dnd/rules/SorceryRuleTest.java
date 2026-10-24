package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SorceryRuleTest {

    private final SorceryRule rule = new SorceryRule(new SpellSlotRule());

    private static Hero sorcerer(int level, int points) {
        return Hero.fresh(UUID.randomUUID(), HeroClass.SORCERER, List.of()).withLevel(level, 0, 0)
                .withSorceryPoints(points);
    }

    @Test
    @DisplayName("sorcery points equal the sorcerer level from 2; nobody else has any")
    void points() {
        assertThat(rule.most(HeroClass.SORCERER, 1)).isZero();
        assertThat(rule.most(HeroClass.SORCERER, 2)).isEqualTo(2);
        assertThat(rule.most(HeroClass.SORCERER, 20)).isEqualTo(20);
        assertThat(rule.most(HeroClass.WIZARD, 20)).isZero();
    }

    @Test
    @DisplayName("a slot costs 2, 3, 5, 6 or 7 points, and nothing above 5th can be made")
    void costs() {
        assertThat(rule.slotCost(1)).contains(2);
        assertThat(rule.slotCost(2)).contains(3);
        assertThat(rule.slotCost(3)).contains(5);
        assertThat(rule.slotCost(4)).contains(6);
        assertThat(rule.slotCost(5)).contains(7);
        assertThat(rule.slotCost(6)).isEmpty();
    }

    @Test
    @DisplayName("making a slot takes the points and adds one slot of that level")
    void makingASlot() {
        Hero hero = sorcerer(5, 5);
        Hero after = rule.makeSlot(hero, 2).orElseThrow();
        assertThat(after.sorceryPoints()).isEqualTo(2);
        assertThat(after.spentSlots(2)).isEqualTo(-1);
        assertThat(rule.makeSlot(sorcerer(5, 1), 1)).isEmpty();
        assertThat(rule.makeSlot(sorcerer(2, 2), 2)).as("no 2nd-level slots exist at level 2 to add to").isEmpty();
    }

    @Test
    @DisplayName("burning a slot gives its level in points, never past the maximum")
    void burningASlot() {
        Hero hero = sorcerer(5, 0);
        Hero after = rule.burnSlot(hero, 3).orElseThrow();
        assertThat(after.sorceryPoints()).isEqualTo(3);
        assertThat(after.spentSlots(3)).isEqualTo(1);
        assertThat(rule.burnSlot(sorcerer(5, 5), 1)).isEmpty();
        assertThat(rule.burnSlot(sorcerer(5, 0).withSlotSpent(3, 2), 3)).isEmpty();
    }
}
