package de.raindancer.modules.dnd.rules;

import org.bukkit.Material;

/** The Potion of Mach Fuck: brewed from a dragon head, and while it works a glider goes where they look, very fast. */
public final class MachRule implements IDndRule {

    /** How long after it wears off a crash or a fall is still forgiven: time to land. */
    public static final long GRACE_MILLIS = 5_000;

    /** Along {@code look} (a unit vector) at {@code blocksPerSecond}, in blocks a tick. */
    public double[] velocity(double[] look, double blocksPerSecond) {
        double perTick = blocksPerSecond / 20.0;
        return new double[]{look[0] * perTick, look[1] * perTick, look[2] * perTick};
    }

    public boolean working(long drankAt, long now, int seconds) {
        return now - drankAt < seconds * 1000L;
    }

    public boolean shielded(long drankAt, long now, int seconds) {
        return now - drankAt < seconds * 1000L + GRACE_MILLIS;
    }

    /** What a dragon head turns into the potion: a plain bottle of any potion, not an adventurer's own. */
    public boolean brewsFrom(Material bottle, boolean magic) {
        return bottle == Material.POTION && !magic;
    }

    @Override
    public String describe() {
        return "the Potion of Mach Fuck: what it brews from, how fast and how long it flies";
    }
}
