package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import de.raindancer.modules.dnd.model.Spell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpellSlotRuleTest {

    private final SpellSlotRule rule = new SpellSlotRule();

    private static Hero wizard(int level) {
        return Hero.fresh(UUID.randomUUID(), HeroClass.WIZARD, List.of()).withLevel(level, 0, 0);
    }

    @Test
    @DisplayName("a full caster's slots follow the table")
    void theTable() {
        assertThat(rule.slots(HeroClass.WIZARD, 1, 1)).isEqualTo(2);
        assertThat(rule.slots(HeroClass.WIZARD, 3, 2)).isEqualTo(2);
        assertThat(rule.slots(HeroClass.WIZARD, 5, 3)).isEqualTo(2);
        assertThat(rule.slots(HeroClass.WIZARD, 20, 1)).isEqualTo(4);
        assertThat(rule.slots(HeroClass.WIZARD, 20, 6)).isEqualTo(2);
        assertThat(rule.slots(HeroClass.WIZARD, 17, 9)).isEqualTo(1);
        assertThat(rule.slots(HeroClass.WIZARD, 16, 9)).isZero();
        assertThat(rule.highestSlotLevel(HeroClass.CLERIC, 1)).isEqualTo(1);
        assertThat(rule.highestSlotLevel(HeroClass.CLERIC, 5)).isEqualTo(3);
        assertThat(rule.highestSlotLevel(HeroClass.CLERIC, 17)).isEqualTo(9);
    }

    @Test
    @DisplayName("a class that casts nothing has no slots at all")
    void martialClassesHaveNone() {
        assertThat(rule.slots(HeroClass.FIGHTER, 20, 1)).isZero();
        assertThat(rule.highestSlotLevel(HeroClass.ROGUE, 20)).isZero();
    }

    @Test
    @DisplayName("the lowest free slot that is high enough is used, and an upcast takes the highest")
    void picking() {
        Hero hero = wizard(5);
        assertThat(rule.pick(hero, 1, false)).contains(1);
        assertThat(rule.pick(hero, 2, false)).contains(2);
        assertThat(rule.pick(hero, 1, true)).contains(3);

        Hero spentFirst = hero.withSlotSpent(1, 4);
        assertThat(rule.pick(spentFirst, 1, false)).contains(2);

        Hero spentAll = hero.withSlotSpent(1, 4).withSlotSpent(2, 3).withSlotSpent(3, 2);
        assertThat(rule.pick(spentAll, 1, false)).isEmpty();
        assertThat(rule.pick(hero, 4, false)).isEmpty();
    }

    @Test
    @DisplayName("a slot made from sorcery points is one more than the table")
    void createdSlotsCount() {
        Hero hero = wizard(1).withSlotSpent(1, -1);
        assertThat(rule.available(hero, 1)).isEqualTo(3);
    }

    @Test
    @DisplayName("a spell is learnable by its own classes once a slot of its level exists; cantrips at once")
    void learnable() {
        assertThat(rule.canLearn(wizard(1), Spell.FIRE_BOLT)).isTrue();
        assertThat(rule.canLearn(wizard(1), Spell.MAGIC_MISSILE)).isTrue();
        assertThat(rule.canLearn(wizard(4), Spell.FIREBALL)).isFalse();
        assertThat(rule.canLearn(wizard(5), Spell.FIREBALL)).isTrue();
        assertThat(rule.canLearn(wizard(20), Spell.CURE_WOUNDS)).isFalse();
    }

    @Test
    @DisplayName("Arcane Recovery wins back slots worth half the level, highest first and never above 5th")
    void arcaneRecovery() {
        Hero hero = wizard(6).withSlotSpent(1, 2).withSlotSpent(2, 1).withSlotSpent(3, 3);
        Hero after = rule.recover(hero, 3);
        assertThat(after.spentSlots(3)).isEqualTo(2);
        assertThat(after.spentSlots(2)).isEqualTo(1);
        assertThat(after.spentSlots(1)).isEqualTo(2);

        Hero low = wizard(2).withSlotSpent(1, 2);
        assertThat(rule.recover(low, 1).spentSlots(1)).isEqualTo(1);

        Hero high = wizard(20).withSlotSpent(6, 1).withSlotSpent(5, 1);
        Hero recovered = rule.recover(high, 10);
        assertThat(recovered.spentSlots(6)).isEqualTo(1);
        assertThat(recovered.spentSlots(5)).isZero();
    }

    @Test
    @DisplayName("budget for recovery is half the level, rounded up")
    void recoveryBudget() {
        assertThat(rule.recoveryBudget(1)).isEqualTo(1);
        assertThat(rule.recoveryBudget(5)).isEqualTo(3);
        assertThat(rule.recoveryBudget(20)).isEqualTo(10);
    }
}
