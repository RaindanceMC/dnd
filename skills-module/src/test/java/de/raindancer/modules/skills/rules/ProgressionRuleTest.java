package de.raindancer.modules.skills.rules;

import de.raindancer.modules.skills.model.Hero;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressionRuleTest {

    private final ProgressionRule rule = new ProgressionRule();

    @Test
    @DisplayName("levels 1 to 20 keep the table, so heroes from before the tree stay the level they were")
    void levelsFollowTheTable() {
        assertThat(rule.levelFor(0)).isEqualTo(1);
        assertThat(rule.levelFor(299)).isEqualTo(1);
        assertThat(rule.levelFor(300)).isEqualTo(2);
        assertThat(rule.levelFor(6_500)).isEqualTo(5);
        assertThat(rule.levelFor(354_999)).isEqualTo(19);
        assertThat(rule.levelFor(355_000)).isEqualTo(20);
        assertThat(rule.levelFor(-5)).isEqualTo(1);
    }

    @Test
    @DisplayName("past 20 every level costs the same, up to 100")
    void pastTwenty() {
        assertThat(rule.levelFor(355_000 + ProgressionRule.PAST_TWENTY - 1)).isEqualTo(20);
        assertThat(rule.levelFor(355_000 + ProgressionRule.PAST_TWENTY)).isEqualTo(21);
        assertThat(rule.xpFor(30)).isEqualTo(355_000 + 10 * ProgressionRule.PAST_TWENTY);
        assertThat(rule.levelFor(Long.MAX_VALUE / 2)).isEqualTo(Hero.MOST_LEVEL);
        for (int level = 1; level <= Hero.MOST_LEVEL; level++) {
            assertThat(rule.levelFor(rule.xpFor(level))).as("level %d", level).isEqualTo(level);
        }
    }

    @Test
    @DisplayName("progress through a level, full at the top")
    void progress() {
        assertThat(rule.progress(150)).isEqualTo(0.5);
        assertThat(rule.progress(rule.xpFor(Hero.MOST_LEVEL))).isEqualTo(1.0);
    }

    @Test
    @DisplayName("experience only ever raises a level; atLevel puts one exactly")
    void growing() {
        Hero hero = Hero.fresh(UUID.randomUUID());
        Hero grown = rule.grown(hero, 1_000);
        assertThat(grown.level()).isEqualTo(3);
        assertThat(grown.xp()).isEqualTo(1_000);
        assertThat(rule.grown(grown, -500).xp()).as("never down").isEqualTo(1_000);
        Hero set = rule.atLevel(grown, 25);
        assertThat(set.level()).isEqualTo(25);
        assertThat(set.xp()).isEqualTo(rule.xpFor(25));
    }
}
