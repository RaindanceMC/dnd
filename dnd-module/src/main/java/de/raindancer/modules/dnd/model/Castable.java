package de.raindancer.modules.dnd.model;

import org.bukkit.Material;

/** Something that can sit on the casting stance's hotbar: a spell, or a class feature used like one. */
public interface Castable {

    /** Unique across spells and features, lower case, as stored in a loadout. */
    String id();

    String title();

    Material icon();

    /** One plain sentence, no markup. */
    String summary();

    /** Takes a bonus action rather than an action: a much shorter wait before the next. */
    boolean bonusAction();
}
