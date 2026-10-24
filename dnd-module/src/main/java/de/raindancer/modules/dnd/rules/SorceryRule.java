package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;

import java.util.Map;
import java.util.Optional;

/** Font of Magic: sorcery points, and turning them into spell slots and back. */
public final class SorceryRule implements IDndRule {

    private static final Map<Integer, Integer> SLOT_COST = Map.of(1, 2, 2, 3, 3, 5, 4, 6, 5, 7);

    private final SpellSlotRule slots;

    public SorceryRule(SpellSlotRule slots) {
        this.slots = slots;
    }

    public int most(HeroClass heroClass, int level) {
        return heroClass == HeroClass.SORCERER && level >= 2 ? level : 0;
    }

    public int most(Hero hero) {
        return most(hero.heroClass(), hero.level());
    }

    public Optional<Integer> slotCost(int slotLevel) {
        return Optional.ofNullable(SLOT_COST.get(slotLevel));
    }

    /** Points spent on one more slot of that level, if they are there and such a slot exists at this level. */
    public Optional<Hero> makeSlot(Hero hero, int slotLevel) {
        Optional<Integer> cost = slotCost(slotLevel);
        if (cost.isEmpty() || hero.sorceryPoints() < cost.get() || slots.most(hero, slotLevel) == 0) {
            return Optional.empty();
        }
        return Optional.of(hero.withSorceryPoints(hero.sorceryPoints() - cost.get())
                .withSlotSpent(slotLevel, hero.spentSlots(slotLevel) - 1));
    }

    /** A slot given up for its level in points, if there is one left and the points would not overflow. */
    public Optional<Hero> burnSlot(Hero hero, int slotLevel) {
        if (slots.available(hero, slotLevel) <= 0 || hero.sorceryPoints() + slotLevel > most(hero)) {
            return Optional.empty();
        }
        return Optional.of(hero.withSorceryPoints(hero.sorceryPoints() + slotLevel)
                .withSlotSpent(slotLevel, hero.spentSlots(slotLevel) + 1));
    }

    @Override
    public String describe() {
        return "sorcery points, and what a spell slot costs in them";
    }
}
