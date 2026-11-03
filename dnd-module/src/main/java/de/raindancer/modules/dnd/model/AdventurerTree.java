package de.raindancer.modules.dnd.model;

import de.raindancer.core.ui.tree.SkillTree;

/** The adventurer's skill tree, {@code skilltree.yml} in the jar. */
public final class AdventurerTree {

    private static volatile SkillTree shipped;

    private AdventurerTree() {
    }

    public static SkillTree shipped() {
        SkillTree known = shipped;
        if (known == null) {
            known = SkillTree.resource(AdventurerTree.class, "/de/raindancer/modules/dnd/skilltree.yml", Perk::known);
            shipped = known;
        }
        return known;
    }
}
