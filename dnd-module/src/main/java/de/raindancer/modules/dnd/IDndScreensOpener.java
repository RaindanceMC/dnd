package de.raindancer.modules.dnd;

import org.bukkit.entity.Player;

/** Opening one of this module's screens without naming its class — so a command built at bootstrap can. */
public interface IDndScreensOpener {

    /** The skill tree, on its first branch. */
    void tree(Player viewer);
}
