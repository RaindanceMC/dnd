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
        if (Feature.EXTRA_ATTACK.has(hero.heroClass(), hero.level())) {
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
        if (Feature.FIGHTING_STYLE.has(hero.heroClass(), hero.level())) {
            return wearsChestplate ? 2.0 : 0.0;
        }
        if (Feature.UNARMORED_DEFENSE.has(hero.heroClass(), hero.level()) && !wearsChestplate) {
            int toughness = hero.modifier(Ability.DEXTERITY) + hero.modifier(Ability.CONSTITUTION);
            return Math.max(0, Math.min(12, toughness * 2));
        }
        return 0.0;
    }

    /** Movement speed added, as a fraction of the normal. */
    public double speedBonus(Hero hero) {
        return Feature.FAST_MOVEMENT.has(hero.heroClass(), hero.level()) ? 0.1 : 0.0;
    }

    @Override
    public String describe() {
        return "what rage, sneak attacks, extra attacks, reckless attacks and dodges do to a blow";
    }
}
