package de.raindancer.modules.skills.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChancesTest {

    @Test
    @DisplayName("a percentage is a chance; past 100 every whole hundred is sure and the rest a chance")
    void chances() {
        assertThat(Chances.hit(25, 0.24)).isTrue();
        assertThat(Chances.hit(25, 0.25)).isFalse();
        assertThat(Chances.hit(0, 0.0)).isFalse();
        assertThat(Chances.times(0, 0.0)).isZero();
        assertThat(Chances.times(40, 0.39)).isOne();
        assertThat(Chances.times(40, 0.41)).isZero();
        assertThat(Chances.times(150, 0.99)).isOne();
        assertThat(Chances.times(150, 0.10)).isEqualTo(2);
        assertThat(Chances.times(-20, 0.0)).isZero();
    }

    @Test
    @DisplayName("Core saw it as the world's own: told once, for that player and that block, and only just now")
    void witnessed() {
        Witnessed seen = new Witnessed(1_000);
        UUID me = UUID.randomUUID();
        UUID you = UUID.randomUUID();
        seen.saw(me, "wheat", 10_000);
        assertThat(seen.take(you, "wheat", 10_000)).as("somebody else").isFalse();
        assertThat(seen.take(me, "carrots", 10_000)).as("another block").isFalse();
        assertThat(seen.take(me, "wheat", 10_500)).isTrue();
        assertThat(seen.take(me, "wheat", 10_500)).as("only once").isFalse();
        seen.saw(me, "wheat", 10_000);
        assertThat(seen.take(me, "wheat", 12_000)).as("too long ago").isFalse();
        seen.saw(me, "wheat", 10_000);
        seen.forget(me);
        assertThat(seen.take(me, "wheat", 10_000)).isFalse();
    }
}
