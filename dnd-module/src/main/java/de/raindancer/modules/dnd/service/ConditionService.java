package de.raindancer.modules.dnd.service;

import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.Condition;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Who is raging, shielded, warded or hidden, and until when. */
public final class ConditionService implements IDndService {

    private final LongSupplier clock;
    private final Map<UUID, Map<Condition, Long>> until = new ConcurrentHashMap<>();

    public ConditionService(LongSupplier clock) {
        this.clock = clock;
    }

    @Override
    public void settings(DndSettings settings) {
        // Nothing here reads the settings: every duration is the spell's or the feature's own.
    }

    public void set(UUID player, Condition condition, long millis) {
        until.computeIfAbsent(player, key -> java.util.Collections.synchronizedMap(new EnumMap<>(Condition.class)))
                .put(condition, clock.getAsLong() + Math.max(0, millis));
    }

    public boolean has(UUID player, Condition condition) {
        Map<Condition, Long> mine = until.get(player);
        if (mine == null) {
            return false;
        }
        Long ends = mine.get(condition);
        if (ends == null) {
            return false;
        }
        if (ends <= clock.getAsLong()) {
            mine.remove(condition);
            return false;
        }
        return true;
    }

    public long remainingMillis(UUID player, Condition condition) {
        Map<Condition, Long> mine = until.get(player);
        Long ends = mine == null ? null : mine.get(condition);
        return ends == null ? 0 : Math.max(0, ends - clock.getAsLong());
    }

    /** Ends it now; true if it was on. */
    public boolean clear(UUID player, Condition condition) {
        boolean was = has(player, condition);
        Map<Condition, Long> mine = until.get(player);
        if (mine != null) {
            mine.remove(condition);
        }
        return was;
    }

    public void forget(UUID player) {
        until.remove(player);
    }

    public int tracked() {
        return until.size();
    }
}
