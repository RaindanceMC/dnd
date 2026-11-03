package de.raindancer.modules.dnd.model;

/**
 * How strong a creature that is not a player is, for its loot.
 *
 * @param xp     its strength at the table, which sets its drop chance and rarity
 * @param undead whether Undead Slayer and Monster Hunter bite on it
 */
public record MobProfile(int xp, boolean undead) {

    public static final MobProfile HARMLESS = new MobProfile(0, false);
}
