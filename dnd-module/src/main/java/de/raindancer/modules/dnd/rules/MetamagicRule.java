package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.Metamagic;
import de.raindancer.modules.dnd.model.Spell;

/** Which metamagic bends which spell, and whether the points are there. */
public final class MetamagicRule implements IDndRule {

    public boolean knows(Hero hero) {
        return Feature.METAMAGIC.has(hero.heroClass(), hero.level());
    }

    public boolean fits(Metamagic metamagic, Spell spell) {
        return switch (metamagic) {
            case QUICKENED -> !spell.bonusAction();
            case TWINNED -> spell.singleTarget();
            case EMPOWERED -> spell.harmful() && !spell.damage().isNone() && spell.damage().count() > 0;
            case EXTENDED -> spell.seconds() > 0;
            case HEIGHTENED -> spell.save().isPresent();
            case CAREFUL -> spell.save().isPresent() && switch (spell.shape()) {
                case CONE, BURST_SELF, BURST_AT, LINE, CHAIN, AURA -> true;
                default -> false;
            };
        };
    }

    public boolean affordable(Hero hero, Metamagic metamagic, Spell spell) {
        return hero.sorceryPoints() >= metamagic.cost(spell.level());
    }

    @Override
    public String describe() {
        return "which metamagic bends which spell, and what it costs";
    }
}
