package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Perk;

import java.util.Map;

/**
 * What the skill tree does to a fight, as numbers: how much harder a blow or an arrow lands, how much less one hurts,
 * the chance to dodge, what a blow heals, what Dual Wielder's off hand strikes for. Against a player, every bonus is
 * scaled by the owner's share (pvp-percent), so the tree tilts fights between players less than fights with monsters.
 */
public final class FightRule implements IDndRule {

    /** Arrows from at least this far count for Long Shot. */
    public static final double LONG_SHOT_BLOCKS = 20;
    /** Dual Wielder: the off hand strikes for this share of its weapon's damage. */
    public static final double OFF_HAND_SHARE = 0.5;
    /** A Ring of Protection in the off hand takes this share off every hurt. */
    public static final double RING_SHARE = 0.10;

    public enum Kind { MELEE, SWEEP, RANGED }

    /**
     * One blow or arrow, as it lands.
     *
     * @param vsPlayer       the one struck is a player
     * @param offHandEmpty   the striker's off hand holds nothing
     * @param belowHalf      the striker has less than half their health
     * @param distance       blocks between the striker and the one struck
     * @param weaponBonus    a +N weapon's N, added before any percent
     */
    public record Hit(Kind kind, boolean critical, boolean behind, boolean undead, boolean vsPlayer,
                      boolean offHandEmpty, boolean belowHalf, double distance, int weaponBonus) {
    }

    private static double v(Map<Perk, Double> values, Perk perk) {
        return values.getOrDefault(perk, 0.0);
    }

    private static double share(boolean vsPlayer, int pvpPercent) {
        return vsPlayer ? Math.max(0, Math.min(100, pvpPercent)) / 100.0 : 1.0;
    }

    /** The bonus a hit gets from the tree, in percent, before the players' share. */
    public double bonusPercent(Map<Perk, Double> values, Hit hit) {
        double percent = 0;
        if (hit.kind() == Kind.RANGED) {
            percent += v(values, Perk.RANGED_DAMAGE);
            if (hit.distance() >= LONG_SHOT_BLOCKS) {
                percent += v(values, Perk.LONG_SHOT);
            }
        } else {
            percent += v(values, Perk.MELEE_DAMAGE);
            if (hit.kind() == Kind.SWEEP) {
                percent += v(values, Perk.SWEEP_DAMAGE);
            }
            if (hit.critical()) {
                percent += v(values, Perk.CRIT_DAMAGE);
            }
            if (hit.offHandEmpty()) {
                percent += v(values, Perk.TWO_HANDED);
            }
            if (hit.belowHalf()) {
                percent += v(values, Perk.RAGE);
            }
            if (hit.behind()) {
                percent += v(values, Perk.BACKSTAB);
            }
        }
        if (hit.undead()) {
            percent += v(values, Perk.UNDEAD_DAMAGE);
        }
        return percent;
    }

    /** What a blow or an arrow does with the striker's tree. */
    public double dealt(Map<Perk, Double> values, Hit hit, double damage, int pvpPercent) {
        double base = Math.max(0, damage) + Math.max(0, hit.weaponBonus());
        return base * (1 + bonusPercent(values, hit) / 100.0 * share(hit.vsPlayer(), pvpPercent));
    }

    /** What a hurt does to the one with this tree; {@code ring} for a Ring of Protection in their off hand. */
    public double taken(Map<Perk, Double> values, double damage, boolean byPlayer, int pvpPercent, boolean ring) {
        double less = v(values, Perk.DAMAGE_TAKEN) / 100.0 * share(byPlayer, pvpPercent) + (ring ? RING_SHARE : 0);
        return Math.max(0, damage) * Math.max(0, 1 - less);
    }

    /** The chance, 0 to 1, a blow or an arrow misses the one with this tree. */
    public double dodgeChance(Map<Perk, Double> values, boolean byPlayer, int pvpPercent) {
        return v(values, Perk.DODGE) / 100.0 * share(byPlayer, pvpPercent);
    }

    /** What a melee blow that did {@code dealt} heals its striker. */
    public double healed(Map<Perk, Double> values, double dealt, boolean vsPlayer, int pvpPercent) {
        return Math.max(0, dealt) * v(values, Perk.LIFESTEAL) / 100.0 * share(vsPlayer, pvpPercent);
    }

    /** What Dual Wielder's off hand strikes for, from what its weapon would do in the main hand. */
    public double offHand(double weaponDamage) {
        return Math.max(0, weaponDamage) * OFF_HAND_SHARE;
    }

    /**
     * Whether a blow comes from behind: the striker stands more than 90 degrees off where the one struck faces.
     * {@code from} points from the one struck to the striker.
     */
    public static boolean behind(double facingX, double facingZ, double fromX, double fromZ) {
        double length = Math.hypot(facingX, facingZ) * Math.hypot(fromX, fromZ);
        if (length == 0) {
            return false;
        }
        return (facingX * fromX + facingZ * fromZ) / length < 0;
    }

    @Override
    public String describe() {
        return "what the skill tree does to blows, arrows and hurts";
    }
}
