package de.raindancer.modules.skills.skill.gathering;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Where falling sand and gravel came to rest. Core marks a block a player placed, but one that fell and landed is a
 * new block without the mark — place sand on a ledge, let it fall, dig it, and again: these spots never count as
 * the world's own. Kept in memory, newest last, so a long session cannot grow it without end.
 */
public final class FallenSpots {

    private final Set<String> spots;

    public FallenSpots(int most) {
        this.spots = Collections.synchronizedSet(Collections.newSetFromMap(new LinkedHashMap<>(256, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > most;
            }
        }));
    }

    private static String key(String world, int x, int y, int z) {
        return world + ':' + x + ':' + y + ':' + z;
    }

    public void landed(String world, int x, int y, int z) {
        spots.add(key(world, x, y, z));
    }

    public void gone(String world, int x, int y, int z) {
        spots.remove(key(world, x, y, z));
    }

    public boolean contains(String world, int x, int y, int z) {
        return spots.contains(key(world, x, y, z));
    }
}
