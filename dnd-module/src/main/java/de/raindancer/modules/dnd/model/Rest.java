package de.raindancer.modules.dnd.model;

public enum Rest {
    SHORT,
    LONG;

    /** Whether resting like this gives back what recharges like that. A long rest includes a short one. */
    public boolean restores(Recharge recharge) {
        return switch (recharge) {
            case NONE -> false;
            case SHORT_REST -> true;
            case LONG_REST -> this == LONG;
        };
    }
}
