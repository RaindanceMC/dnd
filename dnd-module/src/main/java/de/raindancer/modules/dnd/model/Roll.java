package de.raindancer.modules.dnd.model;

/**
 * One d20 test: what the die showed, what it came to, and what it was against.
 *
 * <p>A natural 20 always succeeds and a natural 1 always fails on an attack, as at the table; a saving
 * throw has no such rule, which is why {@link #automatic} is set by whoever rolled.
 */
public record Roll(int die, int bonus, int target, boolean automatic) {

    public int total() {
        return die + bonus;
    }

    public boolean critical() {
        return automatic && die == 20;
    }

    public boolean fumble() {
        return automatic && die == 1;
    }

    public boolean succeeded() {
        if (critical()) {
            return true;
        }
        if (fumble()) {
            return false;
        }
        return total() >= target;
    }
}
