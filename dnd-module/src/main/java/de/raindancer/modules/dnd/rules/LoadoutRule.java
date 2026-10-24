package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Castable;
import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import de.raindancer.modules.dnd.model.Spell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** What may sit on a hero's casting stance, and where. */
public final class LoadoutRule implements IDndRule {

    private final SpellSlotRule slots;

    public LoadoutRule(SpellSlotRule slots) {
        this.slots = slots;
    }

    public static Optional<Castable> castable(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        Optional<Castable> spell = Spell.byId(id).map(found -> found);
        if (spell.isPresent()) {
            return spell;
        }
        return Feature.byId(id).filter(Feature::active).map(found -> found);
    }

    /** Learnable spells, lowest level first, then the active class features. */
    public List<Castable> available(Hero hero) {
        List<Castable> found = new ArrayList<>();
        List<Spell> spells = new ArrayList<>();
        for (Spell spell : Spell.values()) {
            if (slots.canLearn(hero, spell)) {
                spells.add(spell);
            }
        }
        spells.sort(Comparator.comparingInt(Spell::level));
        found.addAll(spells);
        for (Feature feature : Feature.of(hero.heroClass(), hero.level())) {
            if (feature.active()) {
                found.add(feature);
            }
        }
        return found;
    }

    public boolean allowed(Hero hero, String id) {
        return castable(id).filter(found -> available(hero).contains(found)).isPresent();
    }

    public List<String> starting(HeroClass heroClass) {
        Hero fresh = Hero.fresh(null, heroClass, List.of());
        List<String> ids = new ArrayList<>();
        for (Castable castable : available(fresh)) {
            if (ids.size() >= Hero.LOADOUT_SIZE) {
                break;
            }
            ids.add(castable.id());
        }
        while (ids.size() < Hero.LOADOUT_SIZE) {
            ids.add("");
        }
        return ids;
    }

    /** Puts it in that slot, taking it out of any other slot it was in. Empty when it may not be there. */
    public Optional<Hero> place(Hero hero, int slot, String id) {
        if (slot < 0 || slot >= Hero.LOADOUT_SIZE || !allowed(hero, id)) {
            return Optional.empty();
        }
        List<String> next = new ArrayList<>(hero.loadout());
        for (int index = 0; index < next.size(); index++) {
            if (next.get(index).equals(id)) {
                next.set(index, "");
            }
        }
        next.set(slot, id);
        return Optional.of(hero.withLoadout(next));
    }

    public Hero cleared(Hero hero, int slot) {
        if (slot < 0 || slot >= Hero.LOADOUT_SIZE) {
            return hero;
        }
        List<String> next = new ArrayList<>(hero.loadout());
        next.set(slot, "");
        return hero.withLoadout(next);
    }

    /** The first empty slot, or -1. */
    public int firstEmpty(Hero hero) {
        return hero.loadout().indexOf("");
    }

    public Hero pruned(Hero hero) {
        List<String> next = new ArrayList<>(hero.loadout());
        for (int index = 0; index < next.size(); index++) {
            if (!next.get(index).isEmpty() && !allowed(hero, next.get(index))) {
                next.set(index, "");
            }
        }
        return hero.withLoadout(next);
    }

    /** Whatever {@code hero} may use that {@code before} could not goes into empty slots, while there are any. */
    public Hero toppedUp(Hero hero, Hero before) {
        List<Castable> old = before == null ? List.of() : available(before);
        Hero now = hero;
        for (Castable castable : available(hero)) {
            if (old.contains(castable) || now.loadout().contains(castable.id())) {
                continue;
            }
            int empty = firstEmpty(now);
            if (empty < 0) {
                break;
            }
            now = place(now, empty, castable.id()).orElse(now);
        }
        return now;
    }

    public Optional<Castable> at(Hero hero, int slot) {
        if (slot < 0 || slot >= Hero.LOADOUT_SIZE) {
            return Optional.empty();
        }
        return castable(hero.loadout().get(slot));
    }

    @Override
    public String describe() {
        return "which spells and features may sit on the casting stance";
    }
}
