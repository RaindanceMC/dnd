package de.raindancer.modules.dnd.rules;

import de.raindancer.core.world.activity.Activity;
import de.raindancer.core.world.activity.ActivityKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorthRuleTest {

    private final WorthRule rule = new WorthRule();

    @Test
    @DisplayName("monsters are worth nothing: a mob farm earns no levels")
    void noMonsters() {
        assertThat(rule.worth(Activity.of(ActivityKind.MONSTER, "zombie"))).isZero();
        assertThat(rule.worth(Activity.of(ActivityKind.MONSTER, "blaze"))).isZero();
    }

    @Test
    @DisplayName("playing, mining by rarity, harvests, fish, breeding, trades, enchanting levels, advancements, raids")
    void activities() {
        assertThat(rule.worth(Activity.of(ActivityKind.PLAY, 3))).isEqualTo(30);
        assertThat(rule.worth(Activity.of(ActivityKind.MINE, "coal"))).isEqualTo(5);
        assertThat(rule.worth(Activity.of(ActivityKind.MINE, "diamond"))).isEqualTo(40);
        assertThat(rule.worth(Activity.of(ActivityKind.HARVEST, "wheat"))).isEqualTo(2);
        assertThat(rule.worth(Activity.of(ActivityKind.FISH, 1))).isEqualTo(15);
        assertThat(rule.worth(Activity.of(ActivityKind.BREED, "cow"))).isEqualTo(10);
        assertThat(rule.worth(Activity.of(ActivityKind.TRADE, 1))).isEqualTo(10);
        assertThat(rule.worth(Activity.of(ActivityKind.ENCHANT, 3))).isEqualTo(60);
        assertThat(rule.worth(Activity.of(ActivityKind.ADVANCEMENT, "story/mine_diamond"))).isEqualTo(150);
        assertThat(rule.worth(Activity.of(ActivityKind.RAID, 1))).isEqualTo(1_000);
    }

    @Test
    @DisplayName("bosses by kind; one the table does not know is worth the least boss")
    void bosses() {
        assertThat(rule.worth(Activity.of(ActivityKind.BOSS, "ender_dragon"))).isEqualTo(5_000);
        assertThat(rule.worth(Activity.of(ActivityKind.BOSS, "wither"))).isEqualTo(3_000);
        assertThat(rule.worth(Activity.of(ActivityKind.BOSS, "elder_guardian"))).isEqualTo(1_000);
        assertThat(rule.worth(Activity.of(ActivityKind.BOSS, "something_new"))).isEqualTo(1_000);
    }

    @Test
    @DisplayName("a level an hour or so of mixed play early on; level 20 takes a long while")
    void pacing() {
        long hourOfMining = rule.worth(Activity.of(ActivityKind.PLAY, 60))
                + 200 * rule.worth(Activity.of(ActivityKind.MINE, "iron"));
        assertThat(hourOfMining).isBetween(2_000L, 4_000L);
    }
}
