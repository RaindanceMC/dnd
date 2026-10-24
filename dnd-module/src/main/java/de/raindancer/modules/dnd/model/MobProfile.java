package de.raindancer.modules.dnd.model;

/**
 * What a creature that is not a player counts as at the table.
 *
 * @param armourClass what an attack roll has to reach
 * @param saveBonus   added to every saving throw it makes
 * @param xp          what killing it is worth
 * @param undead      whether Turn Undead reaches it
 * @param humanoid    whether Hold Person reaches it
 */
public record MobProfile(int armourClass, int saveBonus, int xp, boolean undead, boolean humanoid) {

    public static final MobProfile HARMLESS = new MobProfile(10, 0, 0, false, false);
}
