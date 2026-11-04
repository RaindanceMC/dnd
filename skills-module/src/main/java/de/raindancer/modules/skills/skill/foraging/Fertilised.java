package de.raindancer.modules.skills.skill.foraging;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spots bone meal grew something on, for a while: a flower it made is not the world's own, so it is never doubled —
 * bone meal on grass, the flowers composted back into bone meal, would otherwise be a loop. Bounded: the oldest go
 * first.
 */
public final class Fertilised {

    private final long keep;
    private final int most;
    private final Map<String, Long> spots;

    public Fertilised(long keepMillis, int most) {
        this.keep = keepMillis;
        this.most = Math.max(1, most);
        this.spots = new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > Fertilised.this.most;
            }
        };
    }

    private static String key(String world, int x, int y, int z) {
        return world + ':' + x + ':' + y + ':' + z;
    }

    public synchronized void mark(String world, int x, int y, int z, long now) {
        String key = key(world, x, y, z);
        spots.remove(key);
        spots.put(key, now);
    }

    public synchronized boolean was(String world, int x, int y, int z, long now) {
        Long at = spots.get(key(world, x, y, z));
        return at != null && now - at <= keep;
    }

    public synchronized int size() {
        return spots.size();
    }
}
