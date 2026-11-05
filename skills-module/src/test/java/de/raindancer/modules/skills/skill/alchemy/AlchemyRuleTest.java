package de.raindancer.modules.skills.skill.alchemy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlchemyRuleTest {

    private final AlchemyRule rule = new AlchemyRule();

    @Test
    @DisplayName("a brew's mark: how much longer, and a level stronger by chance")
    void marks() {
        assertThat(rule.mark(0, 0, 0)).isEqualTo(AlchemyRule.Mark.NONE);
        assertThat(rule.mark(40, 0, 0)).isEqualTo(new AlchemyRule.Mark(40, 0));
        assertThat(rule.mark(40, 30, 0.2)).isEqualTo(new AlchemyRule.Mark(40, 1));
        assertThat(rule.mark(40, 30, 0.4)).isEqualTo(new AlchemyRule.Mark(40, 0));
        assertThat(AlchemyRule.Mark.NONE.plain()).isTrue();
        assertThat(rule.lasting(3_600, 50)).isEqualTo(5_400);
    }

    @Test
    @DisplayName("faster brewing never under a second; the extra potion goes into an empty slot beside a full one")
    void stand() {
        assertThat(rule.brewTime(400, 50)).isEqualTo(200);
        assertThat(rule.brewTime(400, 99)).isEqualTo(20);
        assertThat(rule.emptySlot(new boolean[]{true, false, true})).isOne();
        assertThat(rule.emptySlot(new boolean[]{true, true, true})).isEqualTo(-1);
        assertThat(rule.emptySlot(new boolean[]{false, false, false})).as("nothing brewed").isEqualTo(-1);
        assertThat(rule.fullSlot(new boolean[]{false, false, true})).isEqualTo(2);
    }
}
