package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Dice;
import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import de.raindancer.modules.dnd.model.Subclass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

/** The numbers the subclasses add. */
class SubclassNumbersTest {

    private final FeatureRule features = new FeatureRule();
    private final MartialRule martial = new MartialRule(features);
    private final DamageRule damage = new DamageRule(new ProgressionRule());
    private final HealthRule health = new HealthRule();

    private static Hero of(HeroClass heroClass, Subclass subclass, int level) {
        return Hero.fresh(UUID.randomUUID(), heroClass, List.of()).withLevel(level, 0, 0).withSubclass(subclass);
    }

    @Test
    @DisplayName("a champion crits one time in ten, three in twenty from 15; nobody else extra")
    void criticals() {
        assertThat(martial.critChance(of(HeroClass.FIGHTER, Subclass.CHAMPION, 3))).isEqualTo(0.10);
        assertThat(martial.critChance(of(HeroClass.FIGHTER, Subclass.CHAMPION, 15))).isEqualTo(0.15);
        assertThat(martial.critChance(of(HeroClass.FIGHTER, null, 20))).isZero();
    }

    @Test
    @DisplayName("Dueling adds two to a champion's blow from level 10")
    void dueling() {
        double nine = martial.melee(of(HeroClass.FIGHTER, Subclass.CHAMPION, 9), 4, false, false);
        double ten = martial.melee(of(HeroClass.FIGHTER, Subclass.CHAMPION, 10), 4, false, false);
        assertThat(ten - nine).isEqualTo(2 * 1.25);
    }

    @Test
    @DisplayName("retaliation is two plus Strength plus the rage bonus")
    void retaliation() {
        assertThat(martial.retaliation(of(HeroClass.BARBARIAN, Subclass.BERSERKER, 14))).isEqualTo(2 + 2 + 3);
    }

    @Test
    @DisplayName("draconic scales are armour without a chestplate; their blood is one more hit point a level")
    void draconic() {
        Hero dragon = of(HeroClass.SORCERER, Subclass.DRACONIC, 5);
        assertThat(martial.armourBonus(dragon, false)).isEqualTo(6.0);
        assertThat(martial.armourBonus(dragon, true)).isZero();
        Hero wild = of(HeroClass.SORCERER, Subclass.WILD_MAGIC, 5);
        assertThat(health.perLevel(dragon)).isEqualTo(health.perLevel(wild) + 1);
    }

    @Test
    @DisplayName("a remarkable athlete runs a bit faster")
    void athlete() {
        assertThat(martial.speedBonus(of(HeroClass.FIGHTER, Subclass.CHAMPION, 7))).isEqualTo(0.05);
    }

    @Test
    @DisplayName("Spell Bombardment adds one more die when a die rolls its highest, once")
    void bombardment() {
        RandomGenerator highest = new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return bound - 1;
            }
        };
        List<Dice> dice = List.of(Dice.of(3, 6));
        assertThat(damage.roll(dice, DamageRule.Rolling.PLAIN, highest)).isEqualTo(18);
        assertThat(damage.roll(dice, DamageRule.Rolling.PLAIN.bombarding(), highest)).isEqualTo(24);
    }

    @Test
    @DisplayName("maxed dice — Overchannel, Supreme Healing — roll nothing but their highest")
    void maxed() {
        RandomGenerator lowest = new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
        assertThat(damage.roll(List.of(new Dice(8, 6, 2)), DamageRule.Rolling.PLAIN.maxed(), lowest)).isEqualTo(50);
    }

    @Test
    @DisplayName("Fast Hands halves the cunning waits; others keep theirs")
    void fastHands() {
        Hero thief = of(HeroClass.ROGUE, Subclass.THIEF, 3);
        assertThat(features.cooldownSeconds(Feature.CUNNING_DASH, thief))
                .isEqualTo(features.cooldownSeconds(Feature.CUNNING_DASH) / 2);
        assertThat(features.cooldownSeconds(Feature.RECKLESS_ATTACK, thief))
                .isEqualTo(features.cooldownSeconds(Feature.RECKLESS_ATTACK));
    }

    @Test
    @DisplayName("Bend Luck costs two sorcery points and Draconic Presence five")
    void costs() {
        assertThat(features.sorceryCost(Feature.BEND_LUCK)).isEqualTo(2);
        assertThat(features.sorceryCost(Feature.DRACONIC_PRESENCE)).isEqualTo(5);
        assertThat(features.sorceryCost(Feature.RAGE)).isZero();
        assertThat(features.uses(Feature.TIDES_OF_CHAOS, 1)).isEqualTo(1);
        assertThat(features.uses(Feature.OVERCHANNEL, 14)).isEqualTo(1);
    }
}
