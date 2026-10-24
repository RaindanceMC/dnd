package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Ability;
import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;

/** What the fighting classes add to a blow, take off one, and wear as armour. */
public final class MartialRule implements IDndRule {

    /** Striking from within this many degrees of straight behind counts as from behind. */
    public static final double BEHIND_DEGREES = 60.0;

    public static final double RECKLESS = 1.25;
    public static final double SHIELD = 0.6;
    public static final int DUELING = 2;

    private final FeatureRule features;

    public MartialRule(FeatureRule features) {
        this.features = features;
    }

    /**
     * @param degreesFromBehind 0 when the attacker stands straight behind the target, 180 straight in front
     */
    public boolean sneakAttackApplies(boolean sneaking, boolean unseen, boolean targetBusyElsewhere,
                                      double degreesFromBehind) {
        return sneaking || unseen || targetBusyElsewhere || degreesFromBehind <= BEHIND_DEGREES;
    }

    public double melee(Hero hero, double base, boolean raging, boolean reckless) {
        double damage = base;
        if (raging && hero.heroClass() == HeroClass.BARBARIAN) {
            damage += features.rageDamage(hero.level());
        }
        if (Feature.DUELING.has(hero)) {
            damage += DUELING;
        }
        if (Feature.EXTRA_ATTACK.has(hero)) {
            damage *= 1.0 + features.extraAttackBonus(hero.level());
        }
        if (reckless) {
            damage *= RECKLESS;
        }
        return damage;
    }

    /**
     * @param physical a blow or an arrow, not magic, fire or a fall
     * @param dodge    Uncanny Dodge is spent on this one
     */
    public double incoming(double damage, boolean raging, boolean physical, boolean reckless, boolean shielded,
                           boolean dodge) {
        double left = damage;
        if (raging && physical) {
            left *= 0.5;
        }
        if (reckless) {
            left *= RECKLESS;
        }
        if (shielded) {
            left *= SHIELD;
        }
        if (dodge) {
            left *= 0.5;
        }
        return left;
    }

    /** Armour points added: Defense with a chestplate, Unarmored Defense without one. */
    public double armourBonus(Hero hero, boolean wearsChestplate) {
        if (Feature.FIGHTING_STYLE.has(hero)) {
            return wearsChestplate ? 2.0 : 0.0;
        }
        if (Feature.DRACONIC_RESILIENCE.has(hero)) {
            return wearsChestplate ? 0.0 : 6.0;
        }
        if (Feature.UNARMORED_DEFENSE.has(hero) && !wearsChestplate) {
            int toughness = hero.modifier(Ability.DEXTERITY) + hero.modifier(Ability.CONSTITUTION);
            return Math.max(0, Math.min(12, toughness * 2));
        }
        return 0.0;
    }

    /** Movement speed added, as a fraction of the normal. */
    public double speedBonus(Hero hero) {
        double bonus = Feature.FAST_MOVEMENT.has(hero) ? 0.1 : 0.0;
        return Feature.REMARKABLE_ATHLETE.has(hero) ? bonus + 0.05 : bonus;
    }

    /** The chance a melee blow is a critical hit doing double, beyond Minecraft's own jumping crits. */
    public double critChance(Hero hero) {
        if (Feature.SUPERIOR_CRITICAL.has(hero)) {
            return 0.15;
        }
        return Feature.IMPROVED_CRITICAL.has(hero) ? 0.10 : 0.0;
    }

    /** What Retaliation hits back with. */
    public double retaliation(Hero hero) {
        return 2 + hero.modifier(Ability.STRENGTH) + features.rageDamage(hero.level());
    }

    /** Divine Strike's dice: 1d8 from level 8, 2d8 from 14. */
    public int divineStrikeDice(Hero hero) {
        if (!Feature.DIVINE_STRIKE.has(hero)) {
            return 0;
        }
        return hero.level() >= 14 ? 2 : 1;
    }

    @Override
    public String describe() {
        return "what rage, sneak attacks, extra attacks, reckless attacks and dodges do to a blow";
    }
}
