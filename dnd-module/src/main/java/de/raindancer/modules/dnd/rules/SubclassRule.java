package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.Subclass;

import java.util.List;

/** Which subclass a hero has, gets, or may choose. */
public final class SubclassRule implements IDndRule {

    /** The hero with the subclass their class gives them by now, when there is only one to give. */
    public Hero settled(Hero hero) {
        if (hero.subclass() != null) {
            return hero;
        }
        List<Subclass> options = Subclass.of(hero.heroClass());
        if (options.size() == 1 && hero.level() >= options.getFirst().level()) {
            return hero.withSubclass(options.getFirst());
        }
        return hero;
    }

    /** Whether this hero has a choice waiting for them. */
    public boolean choosing(Hero hero) {
        if (hero.subclass() != null) {
            return false;
        }
        List<Subclass> options = Subclass.of(hero.heroClass());
        return options.size() > 1 && options.stream().anyMatch(option -> hero.level() >= option.level());
    }

    public boolean mayChoose(Hero hero, Subclass subclass) {
        return subclass != null && hero.subclass() == null && subclass.heroClass() == hero.heroClass()
                && hero.level() >= subclass.level();
    }

    @Override
    public String describe() {
        return "which subclass a hero is given or may choose";
    }
}
