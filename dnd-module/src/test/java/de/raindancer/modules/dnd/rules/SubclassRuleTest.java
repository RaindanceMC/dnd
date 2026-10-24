package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import de.raindancer.modules.dnd.model.Subclass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubclassRuleTest {

    private final SubclassRule rule = new SubclassRule();

    private static Hero of(HeroClass heroClass, int level) {
        return Hero.fresh(UUID.randomUUID(), heroClass, List.of()).withLevel(level, 0, 0);
    }

    @Test
    @DisplayName("a class with one path is given it at its level, not before")
    void automatic() {
        assertThat(rule.settled(of(HeroClass.FIGHTER, 2)).subclass()).isNull();
        assertThat(rule.settled(of(HeroClass.FIGHTER, 3)).subclass()).isEqualTo(Subclass.CHAMPION);
        assertThat(rule.settled(of(HeroClass.WIZARD, 2)).subclass()).isEqualTo(Subclass.EVOCATION);
        assertThat(rule.settled(of(HeroClass.CLERIC, 1)).subclass()).isEqualTo(Subclass.LIFE);
    }

    @Test
    @DisplayName("a sorcerer chooses, once, between the two origins")
    void sorcererChooses() {
        Hero sorcerer = of(HeroClass.SORCERER, 1);
        assertThat(rule.settled(sorcerer).subclass()).as("never chosen for them").isNull();
        assertThat(rule.choosing(sorcerer)).isTrue();
        assertThat(rule.mayChoose(sorcerer, Subclass.WILD_MAGIC)).isTrue();
        assertThat(rule.mayChoose(sorcerer, Subclass.CHAMPION)).isFalse();
        Hero wild = sorcerer.withSubclass(Subclass.WILD_MAGIC);
        assertThat(rule.choosing(wild)).isFalse();
        assertThat(rule.mayChoose(wild, Subclass.DRACONIC)).as("chosen is chosen").isFalse();
    }

    @Test
    @DisplayName("a subclass of another class is never kept")
    void wrongClass() {
        assertThat(of(HeroClass.FIGHTER, 5).withSubclass(Subclass.LIFE).subclass()).isNull();
    }

    @Test
    @DisplayName("subclass features come only with the subclass")
    void features() {
        Hero champion = rule.settled(of(HeroClass.FIGHTER, 3));
        assertThat(Feature.IMPROVED_CRITICAL.has(champion)).isTrue();
        assertThat(Feature.IMPROVED_CRITICAL.has(of(HeroClass.FIGHTER, 3))).isFalse();
        assertThat(Feature.of(champion)).contains(Feature.SECOND_WIND, Feature.IMPROVED_CRITICAL);
        Hero wild = of(HeroClass.SORCERER, 20).withSubclass(Subclass.WILD_MAGIC);
        assertThat(Feature.of(wild)).contains(Feature.TIDES_OF_CHAOS).doesNotContain(Feature.DRAGON_WINGS);
    }
}
