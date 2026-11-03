package de.raindancer.modules.dnd.model;

/**
 * What a creature that is not a player is worth to an adventurer.
 *
 * @param xp     what killing it is worth
 * @param undead whether Undead Slayer and Monster Hunter bite on it
 */
public record MobProfile(int xp, boolean undead) {

    public static final MobProfile HARMLESS = new MobProfile(0, false);
}
