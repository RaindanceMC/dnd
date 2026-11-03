package de.raindancer.modules.skills.model;

/**
 * Something a skill tree's node does, by the id its tree file names it with. Every skill's effects have ids of their
 * own; ranks of one effect add up — across every tree a hero has taken it in — to its cap.
 */
public interface Effect {

    String id();

    /** The most all ranks together may add up to; 0 for no cap. */
    int cap();

    /** What a rank of it reads as, for the value it adds. */
    String describe(double value);
}
