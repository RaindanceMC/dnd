package de.raindancer.modules.skills.skill.building;

import de.raindancer.core.world.build.Schematic;

/**
 * A blueprint a player saved: its blocks, and where they stood when they saved it — a paste lands the same way round
 * them, wherever they stand then.
 *
 * @param anchorX where the player stood, from the box's lowest corner
 */
public record Blueprint(String name, Schematic schematic, int anchorX, int anchorY, int anchorZ, long savedAt) {

    /** What {@code /skills blueprint list} shows of one. */
    public record Summary(String name, int width, int height, int length) {
    }

    public Summary summary() {
        return new Summary(name, schematic.width(), schematic.height(), schematic.length());
    }
}
