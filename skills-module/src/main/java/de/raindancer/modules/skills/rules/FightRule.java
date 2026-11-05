package de.raindancer.modules.skills.rules;

import de.raindancer.modules.skills.model.Perk;

import java.util.Map;

/**
 * What the skill tree does to a fight, as numbers: how much harder a blow or an arrow lands, how much less one hurts,
 * the chance to dodge, what a blow heals, what Dual Wielder's off hand strikes for, thorns, the wards, Mana Shield,
 * Executioner, and how long a potion lasts. Against a player, every bonus and chance is scaled by the owner's share
 * (pvp-percent), so the tree tilts fights between players less than fights with monsters.
 */
public final class FightRule implements ISkillsRule {

    /** Arrows from at least this far count for Long Shot. */
    public static final double LONG_SHOT_BLOCKS = 20;
    /** Dual Wielder: the off hand strikes for this share of its weapon's damage. */
    public static final double OFF_HAND_SHARE = 0.5;
    /** A Ring of Protection in the off hand takes this share off every hurt. */
    public static final double RING_SHARE = 0.10;
    /** Arrows from this near count for Point Blank. */
    public static final double CLOSE_SHOT_BLOCKS = 6;
    /** Hunter's Mark: a marked foe takes this many percent more. */
    public static final double MARK_PERCENT = 15;
    /** Momentum counts this many kills in a row at most. */
    public static final int MOST_STREAK = 5;
    /** Executioner finishes a monster left below this share of its health. */
    public static final double EXECUTE_SHARE = 0.2;
    /** Mana Shield: experience points a half-heart (one point of damage) costs. */
    public static final int MANA_POINTS_PER_DAMAGE = 2;

    public enum Kind { MELEE, SWEEP, RANGED }

    /** The kind of weapon a blow is struck with, for the weapon schools. */
    public enum Weapon {
        NONE, SWORD, AXE, OTHER;

        /** By the material's name: {@code DIAMOND_SWORD} is a sword, {@code PICKAXE} only another tool. */
        public static Weapon of(String material) {
            if (material == null || material.isEmpty() || material.endsWith("AIR")) {
                return NONE;
            }
            if (material.endsWith("_SWORD")) {
                return SWORD;
            }
            return material.endsWith("_AXE") ? AXE : OTHER;
        }
    }

    /** Which ward, besides the tree's damage taken, softens a hurt. */
    public enum Ward { NONE, FIRE, BLAST, MISSILE, MAGIC }

    /**
     * One blow or arrow, as it lands.
     *
     * @param vsPlayer       the one struck is a player
     * @param offHandEmpty   the striker's off hand holds nothing
     * @param belowHalf      the striker has less than half their health
     * @param distance       blocks between the striker and the one struck
     * @param weaponBonus    a +N weapon's N, added before any percent
     * @param weapon         what the blow is struck with (not counted for arrows)
     * @param sneaking       the striker is sneaking
     * @param headshot       an arrow hit the top quarter of the one struck
     * @param marked         the one struck bears the striker's Hunter's Mark
     * @param riposte        the striker's shield stopped a blow just now
     * @param streak         the striker's kills in a row
     * @param armour         the armour of the one struck, for Sunder
     */
    public record Hit(Kind kind, boolean critical, boolean behind, boolean undead, boolean vsPlayer,
                      boolean offHandEmpty, boolean belowHalf, double distance, int weaponBonus, Weapon weapon,
                      boolean sneaking, boolean headshot, boolean marked, boolean riposte, int streak, double armour) {

        public Hit(Kind kind, boolean critical, boolean behind, boolean undead, boolean vsPlayer, boolean offHandEmpty,
                   boolean belowHalf, double distance, int weaponBonus) {
            this(kind, critical, behind, undead, vsPlayer, offHandEmpty, belowHalf, distance, weaponBonus, Weapon.NONE,
                    false, false, false, false, 0, 0);
        }
    }

    /** What is left of a hurt after Mana Shield, and the experience points it took. */
    public record Paid(double damage, int points) {
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
            if (hit.distance() <= CLOSE_SHOT_BLOCKS) {
                percent += v(values, Perk.CLOSE_SHOT);
            }
            if (hit.headshot()) {
                percent += v(values, Perk.HEADSHOT);
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
            percent += switch (hit.weapon()) {
                case SWORD -> v(values, Perk.SWORD_DAMAGE);
                case AXE -> v(values, Perk.AXE_DAMAGE);
                default -> 0;
            };
            if (hit.sneaking()) {
                percent += v(values, Perk.SNEAK_ATTACK);
            }
            if (hit.riposte()) {
                percent += v(values, Perk.RIPOSTE);
            }
            percent += v(values, Perk.MOMENTUM) * Math.max(0, Math.min(MOST_STREAK, hit.streak()));
            if (v(values, Perk.SUNDER) > 0) {
                percent += Math.max(0, hit.armour());
            }
        }
        if (hit.undead()) {
            percent += v(values, Perk.UNDEAD_DAMAGE);
        }
        if (hit.marked() && v(values, Perk.HUNTERS_MARK) > 0) {
            percent += MARK_PERCENT;
        }
        return percent;
    }

    /** Spellstrike: the force a critical blow adds, from a roll of its dice. */
    public double force(Map<Perk, Double> values, Hit hit, int roll, int pvpPercent) {
        if (v(values, Perk.SPELLSTRIKE) <= 0 || !hit.critical() || hit.kind() == Kind.RANGED) {
            return 0;
        }
        return Math.max(0, roll) * share(hit.vsPlayer(), pvpPercent);
    }

    /** What a blow or an arrow does with the striker's tree. */
    public double dealt(Map<Perk, Double> values, Hit hit, double damage, int pvpPercent) {
        double base = Math.max(0, damage) + Math.max(0, hit.weaponBonus());
        return base * (1 + bonusPercent(values, hit) / 100.0 * share(hit.vsPlayer(), pvpPercent));
    }

    /** What a hurt does to the one with this tree; {@code ring} for a Ring of Protection in their off hand. */
    public double taken(Map<Perk, Double> values, double damage, boolean byPlayer, int pvpPercent, boolean ring) {
        return taken(values, damage, byPlayer, pvpPercent, ring, Ward.NONE);
    }

    /** As {@link #taken(Map, double, boolean, int, boolean)}, with the ward for this kind of hurt on top. */
    public double taken(Map<Perk, Double> values, double damage, boolean byPlayer, int pvpPercent, boolean ring,
                        Ward ward) {
        double percent = v(values, Perk.DAMAGE_TAKEN) + switch (ward) {
            case FIRE -> v(values, Perk.FIRE_WARD);
            case BLAST -> v(values, Perk.BLAST_WARD);
            case MISSILE -> v(values, Perk.ARROW_WARD);
            case MAGIC -> v(values, Perk.MAGIC_WARD);
            case NONE -> 0;
        };
        double less = percent / 100.0 * share(byPlayer, pvpPercent) + (ring ? RING_SHARE : 0);
        return Math.max(0, damage) * Math.max(0, 1 - less);
    }

    /** What a fall does — falls are the world's, so only the tree's fall share softens them. */
    public double fall(Map<Perk, Double> values, double damage) {
        return Math.max(0, damage) * Math.max(0, 1 - v(values, Perk.FALL_DAMAGE) / 100.0);
    }

    /** What Thorns hurts a striker back for, from what their blow did. */
    public double thorns(Map<Perk, Double> values, double damage, boolean byPlayer, int pvpPercent) {
        return Math.max(0, damage) * v(values, Perk.THORNS) / 100.0 * share(byPlayer, pvpPercent);
    }

    /** Mana Shield: its share of the hurt paid with experience points, as far as {@code points} go. */
    public Paid manaShield(Map<Perk, Double> values, double damage, boolean byPlayer, int pvpPercent, int points) {
        double wanted = Math.max(0, damage) * v(values, Perk.MANA_SHIELD) / 100.0 * share(byPlayer, pvpPercent);
        int cost = Math.min(Math.max(0, points), (int) Math.ceil(wanted * MANA_POINTS_PER_DAMAGE - 1e-9));
        if (cost <= 0) {
            return new Paid(Math.max(0, damage), 0);
        }
        double paid = Math.min(wanted, cost / (double) MANA_POINTS_PER_DAMAGE);
        return new Paid(Math.max(0, damage - paid), cost);
    }

    /**
     * Executioner: whether a blow that leaves a monster with {@code healthLeft} of {@code maxHealth} finishes it.
     * Never a player, never a boss, never one the blow kills anyway.
     */
    public boolean finishes(Map<Perk, Double> values, boolean vsPlayer, boolean boss, double healthLeft,
                            double maxHealth) {
        return v(values, Perk.EXECUTE) > 0 && !vsPlayer && !boss && healthLeft > 0
                && healthLeft < maxHealth * EXECUTE_SHARE;
    }

    /** The chance, 0 to 1, of a perk that is a chance on a hit — Bleed, Stun, Venom, Hex, Ignite, Pin. */
    public double chance(Map<Perk, Double> values, Perk perk, boolean vsPlayer, int pvpPercent) {
        return v(values, perk) / 100.0 * share(vsPlayer, pvpPercent);
    }

    /** A drunk potion's ticks, with Potion Duration. */
    public int longer(Map<Perk, Double> values, int ticks) {
        return (int) Math.min(Integer.MAX_VALUE, Math.round(ticks * (1 + v(values, Perk.POTION_DURATION) / 100.0)));
    }

    /** A harmful effect's ticks, with Effect Shed — never nothing, so it still lands. */
    public int shorter(Map<Perk, Double> values, int ticks) {
        return (int) Math.max(1, Math.round(ticks * Math.max(0, 1 - v(values, Perk.EFFECT_SHED) / 100.0)));
    }

    /** Whether an arrow that hit at height {@code y} hit the head of one standing from {@code minY} to {@code maxY}. */
    public static boolean headshot(double y, double minY, double maxY) {
        return maxY > minY && y >= maxY - (maxY - minY) / 4;
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
