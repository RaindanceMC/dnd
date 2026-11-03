package de.raindancer.modules.skills.model;

import de.raindancer.core.ui.tree.SkillTree;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One skill: what it is called, where it is listed, its tree ({@code trees/<id>.yml}) and the effects its nodes name.
 *
 * <p>A tree file is Core's {@link SkillTree} format: {@code branches} and {@code nodes} with {@code at: [column, row]}
 * on a canvas that scrolls, {@code requires}, {@code excludes}, {@code ranks}, {@code cost}, {@code level},
 * {@code level-step}, {@code choice}, {@code capstone}, {@code mastery} and {@code effects}. Node ids are unique across
 * every skill: a hero's ranks are kept as {@code <skill>:<node>}.
 */
public final class SkillDef {

    private final String id;
    private final String title;
    private final Material icon;
    private final int order;
    private final String badge;
    private final String blurb;
    private final Map<String, Effect> effects;
    private volatile SkillTree tree;

    public SkillDef(String id, String title, Material icon, int order, String badge, String blurb,
                    List<? extends Effect> effects) {
        this.id = id;
        this.title = title;
        this.icon = icon;
        this.order = order;
        this.badge = badge;
        this.blurb = blurb;
        this.effects = effects.stream().collect(Collectors.toUnmodifiableMap(Effect::id, Function.identity()));
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public Material icon() {
        return icon;
    }

    public int order() {
        return order;
    }

    /** The glyph shown beside a name once the whole skill is mastered. */
    public String badge() {
        return badge;
    }

    public String blurb() {
        return blurb;
    }

    /** The effects its nodes may name, by id. */
    public Map<String, Effect> effects() {
        return effects;
    }

    /** Its tree, read from the jar the first time it is asked for. */
    public SkillTree tree() {
        SkillTree known = tree;
        if (known == null) {
            known = SkillTree.resource(SkillDef.class, "/de/raindancer/modules/skills/trees/" + id + ".yml",
                    effects::containsKey);
            tree = known;
        }
        return known;
    }

    /** {@code melee:brawler}: how a hero keeps a rank of this skill's node. */
    public String key(String node) {
        return id + ':' + node;
    }
}
