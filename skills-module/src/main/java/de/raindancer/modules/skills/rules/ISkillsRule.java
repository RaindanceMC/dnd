package de.raindancer.modules.skills.rules;

/**
 * A rule belonging to this module: decides, and does nothing else. No side effects, safe from any thread — on Folia
 * every region asks.
 */
public interface ISkillsRule {

    /** What this rule decides, for a diagnostic. */
    String describe();
}
