package de.raindancer.modules.dnd.model;

/** How a spell meets the world. The caster service has one way of performing each. */
public enum SpellShape {
    /** A ray at whatever the caster looks at: an attack roll against its armour class. */
    BOLT,
    /** The same, at arm's length. */
    TOUCH,
    /** Darts that never miss, spread over the nearest targets in sight. */
    MISSILES,
    /** One creature looked at rolls a saving throw. */
    TARGET_SAVE,
    /** Everything in a cone in front of the caster saves. */
    CONE,
    /** Everything around the caster saves. */
    BURST_SELF,
    /** Everything around the point looked at saves. */
    BURST_AT,
    /** Everything along a straight line saves. */
    LINE,
    /** One target, then the nearest others it jumps to. */
    CHAIN,
    /** The caster, or the player looked at. */
    HEAL,
    /** The most hurt players near the caster. */
    HEAL_GROUP,
    /** The caster alone. */
    BUFF_SELF,
    /** The caster or the player looked at. */
    BUFF_ONE,
    /** Players near the caster. */
    BUFF_GROUP,
    /** Ends harmful effects on the caster or the player looked at. */
    CLEANSE,
    /** Moves the caster to where they look. */
    TELEPORT,
    /** A ring around the caster that hurts whatever stands in it, for as long as concentration lasts. */
    AURA
}
