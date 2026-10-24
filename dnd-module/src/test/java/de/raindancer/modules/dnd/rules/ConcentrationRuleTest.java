package de.raindancer.modules.dnd.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConcentrationRuleTest {

    private final ConcentrationRule rule = new ConcentrationRule();

    @Test
    @DisplayName("the DC to keep concentrating is 10 or half the damage, whichever is higher")
    void dc() {
        assertThat(rule.dc(3)).isEqualTo(10);
        assertThat(rule.dc(22)).isEqualTo(11);
        assertThat(rule.dc(41)).isEqualTo(20);
    }
}
