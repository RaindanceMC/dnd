package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Ability;
import de.raindancer.modules.dnd.model.AbilityScores;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Without ability scores a class is its level, its spells and its features; the numbers underneath grow by themselves. */
class AbilityRuleTest {

    private final AbilityRule rule = new AbilityRule(new ProgressionRule());

    private static Hero wizard(int level) {
        return Hero.fresh(UUID.randomUUID(), HeroClass.WIZARD, List.of()).withLevel(level, 0, 0);
    }

    @Test
    @DisplayName("At level 1 a class has its starting scores")
    void startingScores() {
        assertThat(rule.simple(wizard(1))).isEqualTo(HeroClass.WIZARD.startingScores());
    }

    @Test
    @DisplayName("The class's main ability rises by two at every improvement level, never past 20; the rest stay")
    void mainAbilityGrows() {
        assertThat(rule.simple(wizard(3)).intelligence()).isEqualTo(15);
        assertThat(rule.simple(wizard(4)).intelligence()).isEqualTo(17);
        assertThat(rule.simple(wizard(8)).intelligence()).isEqualTo(19);
        assertThat(rule.simple(wizard(12)).intelligence()).isEqualTo(20);
        assertThat(rule.simple(wizard(20)).intelligence()).isEqualTo(20);
        assertThat(rule.simple(wizard(20)).strength()).isEqualTo(HeroClass.WIZARD.startingScores().strength());
        assertThat(rule.simple(Hero.fresh(UUID.randomUUID(), HeroClass.BARBARIAN, List.of()).withLevel(4, 0, 0))
                .score(Ability.STRENGTH)).isEqualTo(17);
    }

    @Test
    @DisplayName("Switched off, the rules see the derived scores whatever was spent; switched on, the hero as stored")
    void shown() {
        Hero spent = wizard(4).withScores(new AbilityScores(20, 10, 10, 10, 10, 10), 1);
        assertThat(rule.shown(spent, false).scores()).isEqualTo(rule.simple(spent));
        assertThat(rule.shown(spent, false).improvements()).isEqualTo(1);
        assertThat(rule.shown(spent, true)).isSameAs(spent);
    }

    @Test
    @DisplayName("A change made while switched off keeps the stored scores, so switching back loses nothing")
    void keptScores() {
        Hero stored = wizard(3).withScores(new AbilityScores(20, 10, 10, 10, 10, 10), 0);
        Hero seen = rule.shown(stored, false);
        Hero changed = seen.withLevel(4, 2700, seen.improvements() + 2);
        Hero kept = rule.kept(stored, changed, false);
        assertThat(kept.scores()).isEqualTo(stored.scores());
        assertThat(kept.level()).isEqualTo(4);
        assertThat(kept.improvements()).as("points still pile up for a later switch back").isEqualTo(2);
        assertThat(rule.kept(stored, changed, true)).isSameAs(changed);
    }
}
