package de.raindancer.modules.dnd.rules;

import de.raindancer.core.ui.tree.SkillTree;
import de.raindancer.core.ui.tree.TreeNode;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.Perk;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Skill points and the skill tree: a point for every level, what a node may be taken for, and what the nodes taken
 * add up to.
 */
public final class PerkRule implements IDndRule {

    /** Why another rank of a node may not be taken; {@link SkillTree.Refusal} with one more for an unknown node. */
    public enum Refusal { UNKNOWN, MAXED, BARRED, NEEDS, LEVEL, NO_POINTS }

    private final SkillTree tree;
    private final java.util.function.Supplier<Duration> unlearnEvery;

    public PerkRule(SkillTree tree, Duration unlearnEvery) {
        this(tree, () -> unlearnEvery);
    }

    /** @param unlearnEvery asked each time, so a changed setting counts at once */
    public PerkRule(SkillTree tree, java.util.function.Supplier<Duration> unlearnEvery) {
        this.tree = tree;
        this.unlearnEvery = unlearnEvery;
    }

    private long unlearnMillis() {
        Duration every = unlearnEvery.get();
        return every == null || every.isNegative() ? 0 : every.toMillis();
    }

    public SkillTree tree() {
        return tree;
    }

    public int points(Hero hero) {
        return hero.level();
    }

    public int spent(Hero hero) {
        return tree.spent(hero.nodes());
    }

    public int left(Hero hero) {
        return Math.max(0, points(hero) - spent(hero));
    }

    public Optional<Refusal> refusal(Hero hero, String id) {
        Optional<TreeNode> node = tree.node(id);
        if (node.isEmpty()) {
            return Optional.of(Refusal.UNKNOWN);
        }
        return tree.refusal(hero.nodes(), node.get(), hero.level(), left(hero))
                .map(why -> Refusal.valueOf(why.name()));
    }

    /** Whether the whole tree may be unlearnt now; never when nothing is taken. */
    public boolean mayUnlearn(Hero hero, long now) {
        if (hero.nodes().isEmpty()) {
            return false;
        }
        return hero.respecAt() <= 0 || now - hero.respecAt() >= unlearnMillis();
    }

    public long unlearnAgainAt(Hero hero) {
        return hero.respecAt() <= 0 ? 0 : hero.respecAt() + unlearnMillis();
    }

    /** Everything the hero's nodes add up to, each to its cap. */
    public Map<Perk, Double> values(Hero hero) {
        Map<Perk, Double> values = new java.util.EnumMap<>(Perk.class);
        tree.effects(hero.nodes()).forEach((id, sum) -> Perk.parse(id).ifPresent(perk ->
                values.put(perk, perk.cap() > 0 ? Math.min(sum, perk.cap()) : sum)));
        return values;
    }

    public double value(Hero hero, Perk perk) {
        return values(hero).getOrDefault(perk, 0.0);
    }

    public boolean has(Hero hero, Perk feat) {
        return value(hero, feat) > 0;
    }

    @Override
    public String describe() {
        return "a skill point a level, and what the skill tree's nodes add up to";
    }
}
