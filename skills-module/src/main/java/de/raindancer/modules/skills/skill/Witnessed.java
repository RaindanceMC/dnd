package de.raindancer.modules.skills.skill;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What Core's Activities just said a player gathered — a grown crop, a flower the world made — kept for the drops of
 * the same break. Core alone decides what is the world's own (never a placed block, never a farm's), so a bonus asks
 * this rather than guessing again; a break Core did not tell of gets nothing.
 */
public final class Witnessed {

    private record Seen(String what, long at) {
    }

    private final long window;
    private final Map<UUID, Seen> last = new ConcurrentHashMap<>();

    /** @param window how long after Core told of it the drops may still come, in milliseconds */
    public Witnessed(long window) {
        this.window = window;
    }

    public void saw(UUID player, String what, long now) {
        last.put(player, new Seen(what, now));
    }

    /** Whether Core told of this player gathering {@code what} just now; answers yes once. */
    public boolean take(UUID player, String what, long now) {
        Seen seen = last.get(player);
        if (seen == null || !seen.what().equals(what) || now - seen.at() > window) {
            return false;
        }
        return last.remove(player, seen);
    }

    public void forget(UUID player) {
        last.remove(player);
    }
}
