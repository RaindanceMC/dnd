package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressionRuleTest {

    private final ProgressionRule rule = new ProgressionRule();

    @Test
    @DisplayName("levels follow the experience table, starting at 1 and ending at 20")
    void levelsFollowTheTable() {
        assertThat(rule.levelFor(0)).isEqualTo(1);
        assertThat(rule.levelFor(299)).isEqualTo(1);
        assertThat(rule.levelFor(300)).isEqualTo(2);
        assertThat(rule.levelFor(6_500)).isEqualTo(5);
        assertThat(rule.levelFor(354_999)).isEqualTo(19);
        assertThat(rule.levelFor(355_000)).isEqualTo(20);
        assertThat(rule.levelFor(9_999_999)).isEqualTo(20);
        assertThat(rule.levelFor(-5)).isEqualTo(1);
    }

    @Test
    @DisplayName("the experience a level needs is the inverse of the table")
    void xpForLevel() {
        assertThat(rule.xpFor(1)).isZero();
        assertThat(rule.xpFor(2)).isEqualTo(300);
        assertThat(rule.xpFor(20)).isEqualTo(355_000);
        assertThat(rule.xpFor(21)).isEqualTo(355_000);
        for (int level = 1; level <= 20; level++) {
            assertThat(rule.levelFor(rule.xpFor(level))).isEqualTo(level);
        }
    }

    @Test
    @DisplayName("the proficiency bonus is +2 at 1, +3 at 5, +4 at 9, +5 at 13, +6 at 17")
    void proficiency() {
        assertThat(rule.proficiency(1)).isEqualTo(2);
        assertThat(rule.proficiency(4)).isEqualTo(2);
        assertThat(rule.proficiency(5)).isEqualTo(3);
        assertThat(rule.proficiency(9)).isEqualTo(4);
        assertThat(rule.proficiency(13)).isEqualTo(5);
        assertThat(rule.proficiency(17)).isEqualTo(6);
        assertThat(rule.proficiency(20)).isEqualTo(6);
    }

    @Test
    @DisplayName("an Ability Score Improvement at 4, 8, 12, 16 and 19 is two points each, however many are crossed")
    void improvements() {
        assertThat(rule.improvementPoints(1, 3)).isZero();
        assertThat(rule.improvementPoints(3, 4)).isEqualTo(2);
        assertThat(rule.improvementPoints(1, 20)).isEqualTo(10);
        assertThat(rule.improvementPoints(4, 4)).isZero();
        assertThat(rule.improvementPoints(18, 19)).isEqualTo(2);
    }

    @Test
    @DisplayName("cantrips gain a die at 5, 11 and 17")
    void cantripDice() {
        assertThat(rule.cantripDice(1)).isEqualTo(1);
        assertThat(rule.cantripDice(5)).isEqualTo(2);
        assertThat(rule.cantripDice(11)).isEqualTo(3);
        assertThat(rule.cantripDice(17)).isEqualTo(4);
    }

    @Test
    @DisplayName("progress through a level is a fraction, and full at the top")
    void progress() {
        assertThat(rule.progress(0)).isZero();
        assertThat(rule.progress(150)).isEqualTo(0.5);
        assertThat(rule.progress(355_000)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("growing adds experience, climbs levels and pays ability points for every improvement crossed")
    void growing() {
        Hero fresh = Hero.fresh(UUID.randomUUID(), HeroClass.FIGHTER, List.of());
        Hero grown = rule.grown(fresh, 2_700);
        assertThat(grown.level()).isEqualTo(4);
        assertThat(grown.improvements()).isEqualTo(2);
        assertThat(rule.grown(grown, -500).xp()).as("experience is never taken away").isEqualTo(2_700);
        assertThat(rule.grown(fresh, 400_000).level()).isEqualTo(20);
        assertThat(rule.grown(fresh, 400_000).improvements()).isEqualTo(10);
    }

    @Test
    @DisplayName("setting a level puts the experience at its start and pays points only going up")
    void settingALevel() {
        Hero fresh = Hero.fresh(UUID.randomUUID(), HeroClass.FIGHTER, List.of());
        Hero eight = rule.atLevel(fresh, 8);
        assertThat(eight.xp()).isEqualTo(34_000);
        assertThat(eight.improvements()).isEqualTo(4);
        Hero back = rule.atLevel(eight, 2);
        assertThat(back.level()).isEqualTo(2);
        assertThat(back.improvements()).isEqualTo(4);
    }
}
