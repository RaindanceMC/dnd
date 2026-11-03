package de.raindancer.modules.skills.rules;

import de.raindancer.modules.skills.model.Hero;
import de.raindancer.modules.skills.model.Perk;
import de.raindancer.modules.skills.model.SkillCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SkillRuleTest {

    private static final UUID ME = UUID.randomUUID();
    private final int[] cap = {100};
    private final SkillRule rule = new SkillRule(SkillCatalog.all(), () -> cap[0], () -> Duration.ofDays(1));

    private static Hero hero(int level, Map<String, Integer> nodes) {
        return new Hero(ME, level, 0, nodes, 0);
    }

    @Test
    @DisplayName("a skill point a level up to the cap, shared by every skill's tree")
    void points() {
        assertThat(rule.points(hero(12, Map.of()))).isEqualTo(12);
        cap[0] = 10;
        assertThat(rule.points(hero(12, Map.of()))).as("the cap").isEqualTo(10);
        cap[0] = 100;
        Hero mixed = hero(12, Map.of("defense:toughness", 2, "melee:dual_wielder", 1, "archery:steady_aim", 1));
        assertThat(rule.spent(mixed)).isEqualTo(2 + 2 + 1);
        assertThat(rule.left(mixed)).isEqualTo(7);
        assertThat(rule.spentIn(mixed, SkillCatalog.MELEE)).isEqualTo(2);
    }

    @Test
    @DisplayName("refusals per skill: unknown, needs, level, points, maxed, a choice's other side")
    void refusals() {
        assertThat(rule.refusal(hero(50, Map.of()), SkillCatalog.MELEE, "toughness")).as("not Melee's")
                .contains(SkillRule.Refusal.UNKNOWN);
        assertThat(rule.refusal(hero(50, Map.of()), SkillCatalog.MELEE, "brawler")).isEmpty();
        assertThat(rule.refusal(hero(50, Map.of()), SkillCatalog.MELEE, "veteran")).contains(SkillRule.Refusal.NEEDS);
        assertThat(rule.refusal(hero(3, Map.of("melee:brawler", 1, "melee:crusher", 1)), SkillCatalog.MELEE,
                "dual_wielder")).contains(SkillRule.Refusal.LEVEL);
        assertThat(rule.refusal(hero(50, Map.of("defense:toughness", 3)), SkillCatalog.DEFENSE, "toughness"))
                .contains(SkillRule.Refusal.MAXED);
        assertThat(rule.refusal(hero(50, Map.of("melee:brawler", 1, "melee:bloodthirst", 1,
                "melee:great_weapon", 1)), SkillCatalog.MELEE, "dual_wielder")).contains(SkillRule.Refusal.BARRED);
    }

    @Test
    @DisplayName("a branch's mastery badge waits for the rest of the branch, then is free")
    void mastery() {
        assertThat(rule.refusal(hero(50, Map.of("melee:brawler", 1)), SkillCatalog.MELEE, "melee_mastery"))
                .contains(SkillRule.Refusal.UNFINISHED);
        assertThat(rule.masteryBranches(SkillCatalog.MELEE)).hasSize(1);
        assertThat(rule.mastered(hero(50, Map.of()), SkillCatalog.MELEE)).isZero();
    }

    @Test
    @DisplayName("effects add up across every skill's tree, to their caps; feats are had or not")
    void values() {
        Hero tough = hero(50, Map.of("defense:toughness", 3, "defense:big_heart", 3, "defense:giant_blood", 3,
                "defense:undying", 1));
        assertThat(rule.value(tough, Perk.HEARTS)).isEqualTo(11);
        assertThat(rule.has(tough, Perk.UNDYING)).isTrue();
        Hero dodgy = hero(50, Map.of("agility:sidestep", 3, "agility:evasion", 3, "agility:blur", 3,
                "agility:phantom", 1));
        assertThat(rule.value(dodgy, Perk.DODGE)).isEqualTo(24);
    }

    @Test
    @DisplayName("one skill is unlearnt at a time, at most once a day, and only when something is taken in it")
    void unlearn() {
        long day = Duration.ofDays(1).toMillis();
        Hero taken = hero(10, Map.of("melee:brawler", 1, "defense:toughness", 1));
        assertThat(rule.mayUnlearn(taken, SkillCatalog.ARCHERY, 5 * day)).as("nothing in Archery").isFalse();
        assertThat(rule.mayUnlearn(taken, SkillCatalog.MELEE, 5 * day)).isTrue();
        Hero after = taken.unlearnt("melee", 5 * day);
        assertThat(after.nodes()).containsOnlyKeys("defense:toughness");
        assertThat(rule.mayUnlearn(after, SkillCatalog.DEFENSE, 5 * day + 1)).isFalse();
        assertThat(rule.mayUnlearn(after, SkillCatalog.DEFENSE, 6 * day)).isTrue();
    }
}
