package de.raindancer.modules.dnd.service;

import de.raindancer.modules.dnd.DndSettings;

/**
 * A service belonging to this module: does, and decides as little as possible. Takes every new settings snapshot,
 * whether or not it reads one today.
 */
public interface IDndService {

    void settings(DndSettings settings);
}
