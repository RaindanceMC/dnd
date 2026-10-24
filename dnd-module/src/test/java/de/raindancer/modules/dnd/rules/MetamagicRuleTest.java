package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import de.raindancer.modules.dnd.model.Metamagic;
import de.raindancer.modules.dnd.model.Spell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MetamagicRuleTest {

    private final MetamagicRule rule = new MetamagicRule();

    @Test
    @DisplayName("each kind only bends the spells it makes sense for")
    void fits() {
        assertThat(rule.fits(Metamagic.TWINNED, Spell.FIRE_BOLT)).isTrue();
        assertThat(rule.fits(Metamagic.TWINNED, Spell.FIREBALL)).isFalse();
        assertThat(rule.fits(Metamagic.TWINNED, Spell.SCORCHING_RAY)).as("several rays are not one target").isFalse();
        assertThat(rule.fits(Metamagic.QUICKENED, Spell.MISTY_STEP)).as("already a bonus action").isFalse();
        assertThat(rule.fits(Metamagic.QUICKENED, Spell.FIREBALL)).isTrue();
        assertThat(rule.fits(Metamagic.EMPOWERED, Spell.SHIELD)).isFalse();
        assertThat(rule.fits(Metamagic.EXTENDED, Spell.HASTE)).isTrue();
        assertThat(rule.fits(Metamagic.EXTENDED, Spell.FIREBALL)).isFalse();
        assertThat(rule.fits(Metamagic.HEIGHTENED, Spell.HOLD_PERSON)).isTrue();
        assertThat(rule.fits(Metamagic.HEIGHTENED, Spell.MAGIC_MISSILE)).isFalse();
        assertThat(rule.fits(Metamagic.CAREFUL, Spell.FIREBALL)).isTrue();
        assertThat(rule.fits(Metamagic.CAREFUL, Spell.HOLD_PERSON)).isFalse();
    }

    @Test
    @DisplayName("it costs its points, Twinned the spell's level")
    void affordable() {
        Hero sorcerer = Hero.fresh(UUID.randomUUID(), HeroClass.SORCERER, List.of()).withLevel(5, 0, 0)
                .withSorceryPoints(2);
        assertThat(rule.affordable(sorcerer, Metamagic.QUICKENED, Spell.FIREBALL)).isTrue();
        assertThat(rule.affordable(sorcerer, Metamagic.HEIGHTENED, Spell.HOLD_PERSON)).isFalse();
        assertThat(rule.affordable(sorcerer, Metamagic.TWINNED, Spell.FIRE_BOLT)).isTrue();
        assertThat(rule.affordable(sorcerer.withSorceryPoints(0), Metamagic.TWINNED, Spell.FIRE_BOLT)).isFalse();
    }

    @Test
    @DisplayName("only a sorcerer from level 3 may use it")
    void whoMay() {
        Hero two = Hero.fresh(UUID.randomUUID(), HeroClass.SORCERER, List.of()).withLevel(2, 0, 0);
        Hero three = two.withLevel(3, 0, 0);
        Hero wizard = Hero.fresh(UUID.randomUUID(), HeroClass.WIZARD, List.of()).withLevel(20, 0, 0);
        assertThat(rule.knows(two)).isFalse();
        assertThat(rule.knows(three)).isTrue();
        assertThat(rule.knows(wizard)).isFalse();
    }
}
