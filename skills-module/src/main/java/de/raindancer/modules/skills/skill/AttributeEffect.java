package de.raindancer.modules.skills.skill;

import de.raindancer.modules.skills.model.Effect;

/** An effect the game's own attribute does: lent to the player as a transient modifier by {@link SkillAttributes}. */
public interface AttributeEffect extends Effect {

    /** The attribute's key, {@code block_interaction_range}. */
    String attribute();

    /** What one point of the value is worth on the attribute. */
    double scale();

    /** Lent as a multiplier of the attribute's base rather than added to it. */
    boolean multiplies();
}
