package de.raindancer.modules.dnd.service;

import de.raindancer.modules.dnd.model.Condition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionServiceTest {

    private final AtomicLong now = new AtomicLong(1_000);
    private final ConditionService conditions = new ConditionService(now::get);
    private final UUID ana = UUID.randomUUID();

    @Test
    @DisplayName("a condition lasts exactly as long as it was given")
    void lasts() {
        conditions.set(ana, Condition.RAGING, 60_000);
        assertThat(conditions.has(ana, Condition.RAGING)).isTrue();
        now.addAndGet(59_999);
        assertThat(conditions.has(ana, Condition.RAGING)).isTrue();
        now.addAndGet(1);
        assertThat(conditions.has(ana, Condition.RAGING)).isFalse();
    }

    @Test
    @DisplayName("clearing says whether it was on, and conditions are separate")
    void clearing() {
        conditions.set(ana, Condition.WARDED, 10_000);
        assertThat(conditions.has(ana, Condition.SHIELDED)).isFalse();
        assertThat(conditions.clear(ana, Condition.WARDED)).isTrue();
        assertThat(conditions.clear(ana, Condition.WARDED)).isFalse();
    }

    @Test
    @DisplayName("a player who leaves is forgotten entirely")
    void forgetting() {
        conditions.set(ana, Condition.HIDDEN, 10_000);
        conditions.forget(ana);
        assertThat(conditions.tracked()).isZero();
    }
}
