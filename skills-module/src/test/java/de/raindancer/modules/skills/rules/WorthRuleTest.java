package de.raindancer.modules.skills.rules;

import de.raindancer.core.world.activity.Activity;
import de.raindancer.core.world.activity.ActivityKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorthRuleTest {

    private final WorthRule rule = new WorthRule();

    @Test
    @DisplayName("every kind of play is worth something; tougher monsters more, bosses most")
    void worths() {
        assertThat(rule.worth(Activity.of(ActivityKind.MONSTER, "zombie"))).isEqualTo(10);
        assertThat(rule.worth(Activity.of(ActivityKind.MONSTER, "blaze"))).isEqualTo(40);
        assertThat(rule.worth(Activity.of(ActivityKind.BOSS, "ender_dragon"))).isEqualTo(5_000);
        assertThat(rule.worth(Activity.of(ActivityKind.BOSS, "something_new"))).isEqualTo(1_000);
        assertThat(rule.worth(Activity.of(ActivityKind.MINE, "diamond"))).isEqualTo(40);
        assertThat(rule.worth(Activity.of(ActivityKind.DIG, "stone"))).isEqualTo(1);
        assertThat(rule.worth(Activity.of(ActivityKind.CHOP, "oak_log"))).isEqualTo(3);
        assertThat(rule.worth(new Activity(ActivityKind.CRAFT, "chest", 4))).isEqualTo(8);
        assertThat(rule.worth(new Activity(ActivityKind.SMELT, "iron_ingot", 16))).isEqualTo(16);
        assertThat(rule.worth(new Activity(ActivityKind.BREW, "nether_wart", 3))).isEqualTo(45);
        assertThat(rule.worth(Activity.of(ActivityKind.EXPLORE, "minecraft:jungle"))).isEqualTo(100);
        assertThat(rule.worth(Activity.of(ActivityKind.TRAVEL, 250))).isEqualTo(25);
        assertThat(rule.worth(new Activity(ActivityKind.REPAIR, "diamond_pickaxe", 5))).isEqualTo(50);
        assertThat(rule.worth(Activity.of(ActivityKind.PLAY, 1))).isEqualTo(5);
    }

    @Test
    @DisplayName("generators, farms and loops pay at most a minute of real work a minute; bosses and biomes are never capped")
    void caps() {
        assertThat(rule.allowed(ActivityKind.DIG, 0, 5)).isEqualTo(5);
        assertThat(rule.allowed(ActivityKind.DIG, 58, 5)).as("only what is left of the minute").isEqualTo(2);
        assertThat(rule.allowed(ActivityKind.DIG, 60, 5)).isZero();
        assertThat(rule.allowed(ActivityKind.MONSTER, 195, 10)).isEqualTo(5);
        assertThat(rule.allowed(ActivityKind.BOSS, 1_000_000, 5_000)).isEqualTo(5_000);
        assertThat(rule.allowed(ActivityKind.EXPLORE, 10_000, 100)).isEqualTo(100);
    }
}
