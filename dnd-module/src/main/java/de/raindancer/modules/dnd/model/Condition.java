package de.raindancer.modules.dnd.model;

/** Something a player is for a while, that a blow or a rule asks about. */
public enum Condition {
    RAGING,
    RECKLESS,
    /** The Shield spell: blows land softer. */
    SHIELDED,
    /** Death Ward: the next blow that would kill leaves one hit point. */
    WARDED,
    /** Invisibility that attacking or casting ends. */
    HIDDEN,
    /** Paralysed by Hold Person. */
    HELD
}
