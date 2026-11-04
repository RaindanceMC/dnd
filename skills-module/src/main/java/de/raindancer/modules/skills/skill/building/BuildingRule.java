package de.raindancer.modules.skills.skill.building;

import de.raindancer.modules.skills.rules.ISkillsRule;

import java.util.List;

/** The mason's numbers: which placed blocks may be refunded, how fast a ladder is climbed, how long a quick undo waits. */
public final class BuildingRule implements ISkillsRule {

    /** How long after placing a block Quick Undo breaks it at one hit. */
    public static final long UNDO_MILLIS = 5_000;

    /** Vanilla climbs 0.2 blocks a tick. */
    private static final double CLIMB = 0.2;
    private static final double MOST_CLIMB = 0.4;

    /** Blocks that break into something else than themselves — a refunded one would pay twice. */
    private static final List<String> BREAK_INTO_OTHERS = List.of("_ore", "glowstone", "sea_lantern", "clay",
            "melon", "bookshelf", "_leaves", "gilded_blackstone", "amethyst_cluster", "snow_block", "ice",
            "infested_");

    /**
     * Whether a placed block may be refunded: a full, solid block that does not fall, holds nothing and breaks into
     * itself. A refunded block is marked ({@link FreeBlocks}) and drops nothing when broken, so placing and breaking
     * pays nothing either way.
     */
    public boolean mayRefund(String block, boolean solid, boolean occluding, boolean falls, boolean holds) {
        if (!solid || !occluding || falls || holds) {
            return false;
        }
        return BREAK_INTO_OTHERS.stream().noneMatch(block::contains);
    }

    /** Upwards speed on a ladder, a tick. */
    public double climb(double faster) {
        return Math.min(MOST_CLIMB, CLIMB * (100 + Math.max(0, faster)) / 100);
    }

    public boolean undoable(long placedAt, long now) {
        return now - placedAt <= UNDO_MILLIS;
    }

    @Override
    public String describe() {
        return "which placed blocks a mason gets back, and how fast they climb";
    }
}
