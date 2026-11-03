package de.raindancer.modules.skills.rules;

import de.raindancer.core.ui.tree.SkillTree;
import de.raindancer.core.ui.tree.TreeBranch;
import de.raindancer.core.ui.tree.TreeNode;
import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.model.Hero;
import de.raindancer.modules.skills.model.Perk;
import de.raindancer.modules.skills.model.SkillCatalog;
import de.raindancer.modules.skills.model.SkillDef;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Skill points and the skills' trees: one point for every adventurer level up to the cap, spent in any skill's tree —
 * so a hero is a specialist, not everything at once — what a node may be taken for, what the nodes taken add up to,
 * and which branches are mastered.
 */
public final class SkillRule implements ISkillsRule {

    /** Why another rank of a node may not be taken: Core's {@link SkillTree.Refusal}s, and one for an unknown node. */
    public enum Refusal { UNKNOWN, MAXED, BARRED, NEEDS, UNFINISHED, LEVEL, NO_POINTS }

    private final List<SkillDef> skills;
    private final IntSupplier cap;
    private final Supplier<Duration> unlearnEvery;

    /** @param cap and {@code unlearnEvery} are asked each time, so a changed setting counts at once */
    public SkillRule(List<SkillDef> skills, IntSupplier cap, Supplier<Duration> unlearnEvery) {
        this.skills = List.copyOf(skills);
        this.cap = cap;
        this.unlearnEvery = unlearnEvery;
    }

    public List<SkillDef> skills() {
        return skills;
    }

    public int cap() {
        return Math.max(0, cap.getAsInt());
    }

    /** A point for every level, up to the cap. */
    public int points(Hero hero) {
        return Math.min(hero.level(), cap());
    }

    public int spent(Hero hero) {
        int spent = 0;
        for (SkillDef skill : skills) {
            spent += skill.tree().spent(hero.nodesOf(skill.id()));
        }
        return spent;
    }

    public int spentIn(Hero hero, SkillDef skill) {
        return skill.tree().spent(hero.nodesOf(skill.id()));
    }

    public int left(Hero hero) {
        return Math.max(0, points(hero) - spent(hero));
    }

    public Optional<Refusal> refusal(Hero hero, SkillDef skill, String node) {
        Optional<TreeNode> found = skill.tree().node(node);
        if (found.isEmpty()) {
            return Optional.of(Refusal.UNKNOWN);
        }
        return skill.tree().refusal(hero.nodesOf(skill.id()), found.get(), hero.level(), left(hero))
                .map(why -> Refusal.valueOf(why.name()));
    }

    private long unlearnMillis() {
        Duration every = unlearnEvery.get();
        return every == null || every.isNegative() ? 0 : every.toMillis();
    }

    /** Whether a skill's tree may be unlearnt now: something in it taken, and the wait since the last one over. */
    public boolean mayUnlearn(Hero hero, SkillDef skill, long now) {
        if (hero.nodesOf(skill.id()).isEmpty()) {
            return false;
        }
        return hero.respecAt() <= 0 || now - hero.respecAt() >= unlearnMillis();
    }

    public long unlearnAgainAt(Hero hero) {
        return hero.respecAt() <= 0 ? 0 : hero.respecAt() + unlearnMillis();
    }

    // ------------------------------------------------------------------ what the nodes add up to

    /** Every effect the hero's nodes add up to, across every skill's tree, each to its cap, by effect id. */
    public Map<String, Double> effects(Hero hero) {
        Map<String, Double> sum = new HashMap<>();
        for (SkillDef skill : skills) {
            skill.tree().effects(hero.nodesOf(skill.id())).forEach((id, value) -> sum.merge(id, value, Double::sum));
        }
        Map<String, Effect> known = SkillCatalog.effects();
        sum.replaceAll((id, value) -> {
            Effect effect = known.get(id);
            return effect == null || effect.cap() <= 0 ? value : Math.min(value, effect.cap());
        });
        return sum;
    }

    /** The fighting skills' effects, for the services that work them. */
    public Map<Perk, Double> values(Hero hero) {
        Map<Perk, Double> values = new EnumMap<>(Perk.class);
        effects(hero).forEach((id, value) -> Perk.parse(id).ifPresent(perk -> values.put(perk, value)));
        return values;
    }

    public double value(Hero hero, Perk perk) {
        return values(hero).getOrDefault(perk, 0.0);
    }

    public boolean has(Hero hero, Perk feat) {
        return value(hero, feat) > 0;
    }

    // ------------------------------------------------------------------ mastery

    /** The branches of a skill that end in a mastery badge. */
    public List<TreeBranch> masteryBranches(SkillDef skill) {
        SkillTree tree = skill.tree();
        return tree.branches().stream().filter(branch -> !tree.masteries(branch.id()).isEmpty()).toList();
    }

    /** How many of a skill's mastery branches the hero has mastered. */
    public int mastered(Hero hero, SkillDef skill) {
        Map<String, Integer> ranks = hero.nodesOf(skill.id());
        return (int) masteryBranches(skill).stream().filter(branch -> skill.tree().mastered(ranks, branch.id())).count();
    }

    public boolean fullyMastered(Hero hero, SkillDef skill) {
        int branches = masteryBranches(skill).size();
        return branches > 0 && mastered(hero, skill) >= branches;
    }

    @Override
    public String describe() {
        return "a skill point a level up to the cap, spent in any skill's tree, and what the nodes add up to";
    }
}
