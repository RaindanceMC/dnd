package de.raindancer.modules.dnd.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

    @Test
    @DisplayName("modifiers round down: 10 is +0, 9 is -1, 20 is +5")
    void modifiers() {
        assertThat(Ability.modifier(10)).isZero();
        assertThat(Ability.modifier(11)).isZero();
        assertThat(Ability.modifier(9)).isEqualTo(-1);
        assertThat(Ability.modifier(8)).isEqualTo(-1);
        assertThat(Ability.modifier(20)).isEqualTo(5);
        assertThat(Ability.modifier(1)).isEqualTo(-5);
    }

    @Test
    @DisplayName("scores survive being written and read, and rubbish reads as 10")
    void scoresRoundTrip() {
        AbilityScores scores = new AbilityScores(15, 14, 13, 12, 10, 8);
        assertThat(AbilityScores.decode(scores.encode())).isEqualTo(scores);
        assertThat(AbilityScores.decode("x,,3")).isEqualTo(new AbilityScores(10, 10, 3, 10, 10, 10));
        assertThat(AbilityScores.decode(null)).isEqualTo(new AbilityScores(10, 10, 10, 10, 10, 10));
    }

    @Test
    @DisplayName("an improvement stops at 20")
    void raiseStopsAtTwenty() {
        AbilityScores top = new AbilityScores(20, 10, 10, 10, 10, 10);
        assertThat(top.raised(Ability.STRENGTH)).isEqualTo(top);
        assertThat(top.raised(Ability.DEXTERITY).dexterity()).isEqualTo(11);
    }

    @Test
    @DisplayName("every class starts with the standard array, its best score where its class wants it")
    void startingScores() {
        for (HeroClass heroClass : HeroClass.values()) {
            List<Integer> sorted = heroClass.startingScores().asMap().values().stream().sorted().toList();
            assertThat(sorted).as(heroClass.title()).containsExactly(8, 10, 12, 13, 14, 15);
            assertThat(heroClass.startingScores().score(heroClass.primary())).as(heroClass.title()).isEqualTo(15);
            assertThat(heroClass.savingThrows()).hasSize(2);
        }
    }

    @Test
    @DisplayName("a hero keeps its lists the right length and its numbers in range")
    void heroIsTidy() {
        Hero hero = new Hero(UUID.randomUUID(), HeroClass.WIZARD, 99, -5, new AbilityScores(10, 10, 10, 10, 10, 10),
                -1, List.of("a"), List.of(0, 5), -3, 50, null, 0, null);
        assertThat(hero.level()).isEqualTo(20);
        assertThat(hero.xp()).isZero();
        assertThat(hero.loadout()).hasSize(Hero.LOADOUT_SIZE);
        assertThat(hero.slotsSpent()).hasSize(Hero.SLOT_LEVELS + 1);
        assertThat(hero.spentSlots(1)).isEqualTo(5);
        assertThat(hero.hitDiceSpent()).isEqualTo(20);
        assertThat(hero.used()).isEmpty();
    }

    @Test
    @DisplayName("dice: averages, maxima and how they read")
    void dice() {
        assertThat(Dice.of(8, 6).average()).isEqualTo(28.0);
        assertThat(new Dice(1, 4, 1).most()).isEqualTo(5);
        assertThat(new Dice(10, 6, 40).toString()).isEqualTo("10d6+40");
        assertThat(Dice.flat(70).toString()).isEqualTo("70");
    }

    @Test
    @DisplayName("spell and feature ids never collide, since a loadout holds both")
    void idsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (Spell spell : Spell.values()) {
            assertThat(seen.add(spell.id())).as(spell.id()).isTrue();
        }
        for (Feature feature : Feature.values()) {
            assertThat(seen.add(feature.id())).as(feature.id()).isTrue();
        }
    }

    @Test
    @DisplayName("every caster has a cantrip and something at every spell level from 1 to 9")
    void spellListsAreFull() {
        for (HeroClass heroClass : HeroClass.values()) {
            if (!heroClass.castsSpells()) {
                continue;
            }
            for (int level = 0; level <= 9; level++) {
                int wanted = level;
                assertThat(java.util.Arrays.stream(Spell.values())
                        .filter(spell -> spell.learnedBy(heroClass) && spell.level() == wanted))
                        .as(heroClass.title() + " at level " + level).isNotEmpty();
            }
        }
    }

    @Test
    @DisplayName("every class has something on its stance by level 2")
    void everyClassHasActions() {
        for (HeroClass heroClass : HeroClass.values()) {
            boolean spells = heroClass.castsSpells();
            boolean active = Feature.of(heroClass, 2).stream().anyMatch(Feature::active);
            assertThat(spells || active).as(heroClass.title()).isTrue();
        }
    }

    @Test
    @DisplayName("an area spell has a radius, a ranged one a range, a damaging one dice")
    void spellsAreComplete() {
        for (Spell spell : Spell.values()) {
            switch (spell.shape()) {
                case BURST_SELF, BURST_AT, AURA, HEAL_GROUP, BUFF_GROUP -> assertThat(spell.radius())
                        .as(spell.id()).isPositive();
                case BOLT, TOUCH, MISSILES, TARGET_SAVE, CONE, LINE, CHAIN, TELEPORT -> assertThat(spell.range())
                        .as(spell.id()).isPositive();
                default -> {
                }
            }
            if (spell.harmful() && spell.shape() != SpellShape.TARGET_SAVE) {
                assertThat(spell.damage().isNone()).as(spell.id()).isFalse();
            }
            if (spell.concentration()) {
                assertThat(spell.seconds()).as(spell.id()).isPositive();
            }
        }
    }

    @Test
    @DisplayName("Twinned Spell is for one-target spells only")
    void singleTarget() {
        assertThat(Spell.FIRE_BOLT.singleTarget()).isTrue();
        assertThat(Spell.SCORCHING_RAY.singleTarget()).isFalse();
        assertThat(Spell.FIREBALL.singleTarget()).isFalse();
        assertThat(Spell.HASTE.singleTarget()).isTrue();
    }

    @Test
    @DisplayName("rests: a long one gives back what a short one does, and more")
    void rests() {
        assertThat(Rest.SHORT.restores(Recharge.SHORT_REST)).isTrue();
        assertThat(Rest.SHORT.restores(Recharge.LONG_REST)).isFalse();
        assertThat(Rest.LONG.restores(Recharge.SHORT_REST)).isTrue();
        assertThat(Rest.LONG.restores(Recharge.NONE)).isFalse();
    }
}
