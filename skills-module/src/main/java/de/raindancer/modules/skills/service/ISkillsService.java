package de.raindancer.modules.skills.service;

import de.raindancer.modules.skills.SkillsSettings;

/**
 * A service belonging to this module: does, and decides as little as possible. Takes every new settings snapshot,
 * whether or not it reads one today.
 */
public interface ISkillsService {

    void settings(SkillsSettings settings);
}
