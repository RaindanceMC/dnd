package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Castable;
import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import de.raindancer.modules.dnd.model.Spell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LoadoutRuleTest {

    private final LoadoutRule rule = new LoadoutRule(new SpellSlotRule());

    private static Hero of(HeroClass heroClass, int level) {
        Hero hero = Hero.fresh(UUID.randomUUID(), heroClass, List.of()).withLevel(level, 0, 0);
        return hero;
    }

    @Test
    @DisplayName("what is on offer is the class's learnable spells and its active features, nothing else")
    void available() {
        List<Castable> wizard = rule.available(of(HeroClass.WIZARD, 1));
        assertThat(wizard).contains(Spell.FIRE_BOLT, Spell.MAGIC_MISSILE, Feature.ARCANE_RECOVERY);
        assertThat(wizard).doesNotContain(Spell.FIREBALL, Spell.CURE_WOUNDS, Feature.SECOND_WIND);
        assertThat(rule.available(of(HeroClass.FIGHTER, 2))).containsExactly(Feature.SECOND_WIND, Feature.ACTION_SURGE);
        assertThat(rule.available(of(HeroClass.ROGUE, 1))).isEmpty();
    }

    @Test
    @DisplayName("a new adventurer starts with the first eight things on offer in their stance")
    void starting() {
        List<String> cleric = rule.starting(HeroClass.CLERIC);
        assertThat(cleric).hasSize(8);
        assertThat(cleric.getFirst()).isEqualTo("sacred_flame");
        assertThat(rule.starting(HeroClass.ROGUE)).containsOnly("");
    }

    @Test
    @DisplayName("placing refuses what the hero cannot use, and moves an id rather than doubling it")
    void placing() {
        Hero wizard = of(HeroClass.WIZARD, 5).withLoadout(List.of("fire_bolt"));
        assertThat(rule.place(wizard, 2, "fireball")).isPresent();
        assertThat(rule.place(wizard, 2, "cure_wounds")).isEmpty();
        assertThat(rule.place(wizard, 9, "fireball")).isEmpty();
        Hero moved = rule.place(wizard, 3, "fire_bolt").orElseThrow();
        assertThat(moved.loadout().get(0)).isEmpty();
        assertThat(moved.loadout().get(3)).isEqualTo("fire_bolt");
    }

    @Test
    @DisplayName("pruning empties slots holding what is no longer allowed; topping up fills empty ones with new things")
    void pruneAndTopUp() {
        Hero changed = of(HeroClass.FIGHTER, 1).withLoadout(List.of("fireball", "second_wind"));
        Hero pruned = rule.pruned(changed);
        assertThat(pruned.loadout().get(0)).isEmpty();
        assertThat(pruned.loadout().get(1)).isEqualTo("second_wind");

        Hero fighter2 = of(HeroClass.FIGHTER, 2).withLoadout(List.of("second_wind"));
        Hero topped = rule.toppedUp(fighter2, of(HeroClass.FIGHTER, 1));
        assertThat(topped.loadout()).contains("action_surge");
        assertThat(topped.loadout().stream().filter("second_wind"::equals).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("ids resolve to spells and features alike")
    void resolving() {
        assertThat(LoadoutRule.castable("fireball")).contains(Spell.FIREBALL);
        assertThat(LoadoutRule.castable("rage")).contains(Feature.RAGE);
        assertThat(LoadoutRule.castable("")).isEmpty();
        assertThat(LoadoutRule.castable("nonsense")).isEmpty();
        assertThat(rule.at(of(HeroClass.WIZARD, 1).withLoadout(List.of("fire_bolt")), 0)).contains(Spell.FIRE_BOLT);
    }
}
