package de.raindancer.modules.dnd;

import org.bukkit.entity.Player;

/** Opening one of this module's screens without naming its class — so a command built at bootstrap can. */
public interface IDndScreensOpener {

    /** The character sheet, or the class choice for somebody who has none yet. */
    void sheet(Player viewer);

    void classes(Player viewer);

    void spellbook(Player viewer);
}
