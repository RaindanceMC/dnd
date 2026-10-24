package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RestRuleTest {

    private final RestRule rule = new RestRule(new SorceryRule(new SpellSlotRule()), new HealthRule());

    private static Hero of(HeroClass heroClass, int level) {
        return Hero.fresh(UUID.randomUUID(), heroClass, List.of()).withLevel(level, 0, 0);
    }

    @Test
    @DisplayName("a long rest puts back every slot, every use, the sorcery points and half the hit dice")
    void longRest() {
        Hero tired = of(HeroClass.SORCERER, 10).withSlotSpent(1, 4).withSlotSpent(2, -1).withSorceryPoints(1)
                .withHitDiceSpent(8).withUsed(Feature.SECOND_WIND, 1);
        Hero rested = rule.afterLongRest(tired, 5_000L);
        assertThat(rested.spentSlots(1)).isZero();
        assertThat(rested.spentSlots(2)).as("a slot made from points is gone after a long rest").isZero();
        assertThat(rested.sorceryPoints()).isEqualTo(10);
        assertThat(rested.hitDiceSpent()).isEqualTo(3);
        assertThat(rested.used()).isEmpty();
        assertThat(rested.longRestAt()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("a short rest gives back only what recharges on one")
    void shortRest() {
        Hero tired = of(HeroClass.FIGHTER, 9).withUsed(Feature.SECOND_WIND, 1).withUsed(Feature.ACTION_SURGE, 1)
                .withUsed(Feature.INDOMITABLE, 1).withHitDiceSpent(2);
        Hero rested = rule.afterShortRest(tired);
        assertThat(rested.usedOf(Feature.SECOND_WIND)).isZero();
        assertThat(rested.usedOf(Feature.ACTION_SURGE)).isZero();
        assertThat(rested.usedOf(Feature.INDOMITABLE)).isEqualTo(1);
        assertThat(rested.hitDiceSpent()).isEqualTo(2);
    }

    @Test
    @DisplayName("a long rest needs the wait to have passed since the last one")
    void longRestWait() {
        assertThat(rule.mayLongRest(0, 1_000, 15)).isTrue();
        assertThat(rule.mayLongRest(1_000, 1_000 + 14 * 60_000, 15)).isFalse();
        assertThat(rule.mayLongRest(1_000, 1_000 + 15 * 60_000, 15)).isTrue();
        assertThat(rule.mayLongRest(1_000, 2_000, 0)).isTrue();
    }

    @Test
    @DisplayName("a short rest spends just enough hit dice for what is missing, and no more than are left")
    void hitDice() {
        Hero fighter = of(HeroClass.FIGHTER, 4);
        RestRule.Spent spent = rule.spendHitDice(fighter, 20.0, 8);
        assertThat(spent.dice()).isEqualTo(3);
        assertThat(spent.healed()).isEqualTo(20.0);

        RestRule.Spent little = rule.spendHitDice(fighter.withHitDiceSpent(3), 20.0, 8);
        assertThat(little.dice()).isEqualTo(1);
        assertThat(little.healed()).isEqualTo(8.0);

        assertThat(rule.spendHitDice(fighter, 0.0, 8).dice()).isZero();
        assertThat(rule.spendHitDice(fighter.withHitDiceSpent(4), 10.0, 8).dice()).isZero();
    }
}
