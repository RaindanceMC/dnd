package de.raindancer.modules.dnd.rules;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MobRuleTest {

    private final MobRule rule = new MobRule();

    @Test
    @DisplayName("common monsters are worth a little, bosses a great deal, animals nothing")
    void experience() {
        assertThat(rule.of(EntityType.ZOMBIE).xp()).isEqualTo(50);
        assertThat(rule.of(EntityType.ENDER_DRAGON).xp()).isGreaterThan(rule.of(EntityType.WITHER).xp());
        assertThat(rule.of(EntityType.WARDEN).xp()).isGreaterThan(rule.of(EntityType.BLAZE).xp());
        assertThat(rule.of(EntityType.COW).xp()).isZero();
        assertThat(rule.of(EntityType.PLAYER).xp()).isZero();
    }

    @Test
    @DisplayName("the undead and the humanoids are known")
    void kinds() {
        assertThat(rule.of(EntityType.ZOMBIE).undead()).isTrue();
        assertThat(rule.of(EntityType.SKELETON).undead()).isTrue();
        assertThat(rule.of(EntityType.CREEPER).undead()).isFalse();
        assertThat(rule.of(EntityType.PILLAGER).humanoid()).isTrue();
        assertThat(rule.of(EntityType.PLAYER).humanoid()).isTrue();
        assertThat(rule.of(EntityType.SPIDER).humanoid()).isFalse();
    }

    @Test
    @DisplayName("tougher monsters are harder to hit and better at saving")
    void defences() {
        assertThat(rule.of(EntityType.WITHER).armourClass()).isGreaterThan(rule.of(EntityType.ZOMBIE).armourClass());
        assertThat(rule.of(EntityType.WITHER).saveBonus()).isGreaterThan(rule.of(EntityType.ZOMBIE).saveBonus());
    }
}
