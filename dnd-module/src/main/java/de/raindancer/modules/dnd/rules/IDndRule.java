package de.raindancer.modules.dnd.rules;

/**
 * A rule belonging to this module: decides, and does nothing else. No side effects, safe from any thread — on Folia
 * every region asks.
 */
public interface IDndRule {

    /** What this rule decides, for a diagnostic. */
    String describe();
}
