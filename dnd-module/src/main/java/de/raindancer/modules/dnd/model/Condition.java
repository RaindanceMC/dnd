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
    HELD,
    /** Tides of Chaos: the next spell attack roll has advantage. */
    TIDES,
    /** Bend Luck: creatures saving against this caster subtract a d4. */
    BENT_LUCK,
    /** Overchannel: the next 1st- to 5th-level damage spell rolls its highest. */
    OVERCHANNELLING,
    /** A wild surge: spells cost no slot. */
    FREE_CASTING,
    /** Frightened by Intimidating or Draconic Presence. */
    FRIGHTENED
}
