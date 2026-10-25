package de.raindancer.modules.dnd.service;

import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.store.DndPack;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The game's elytra speed check pulls anybody faster than about 17 blocks a tick back, so while a Potion of Mach
 * Fuck works in a world that world's {@code elytra_movement_check} is off, and back on once none works there. What
 * it was is written on the world first, so a crash mid-flight is put right at the next start. Global thread only.
 */
public final class MachWorlds implements IDndService {

    private static final NamespacedKey SAVED = new NamespacedKey(DndPack.NAMESPACE, "mach_elytra_check");

    private final Set<UUID> eased = ConcurrentHashMap.newKeySet();

    @Override
    public void settings(DndSettings settings) {
        // Nothing to tune.
    }

    /** The worlds somebody flies a Potion of Mach Fuck in right now. */
    public void flyingIn(Collection<World> worlds, Collection<World> all) {
        for (World world : worlds) {
            if (eased.add(world.getUID())) {
                ease(world);
            }
        }
        for (World world : all) {
            if (eased.contains(world.getUID()) && !worlds.contains(world)) {
                eased.remove(world.getUID());
                restore(world);
            }
        }
    }

    private static void ease(World world) {
        var data = world.getPersistentDataContainer();
        if (!data.has(SAVED, PersistentDataType.BOOLEAN)) {
            Boolean was = world.getGameRuleValue(GameRules.ELYTRA_MOVEMENT_CHECK);
            data.set(SAVED, PersistentDataType.BOOLEAN, was == null || was);
        }
        world.setGameRule(GameRules.ELYTRA_MOVEMENT_CHECK, false);
    }

    private static void restore(World world) {
        var data = world.getPersistentDataContainer();
        Boolean was = data.get(SAVED, PersistentDataType.BOOLEAN);
        if (was == null) {
            return;
        }
        world.setGameRule(GameRules.ELYTRA_MOVEMENT_CHECK, was);
        data.remove(SAVED);
    }

    /** Every world back as it was: when the module stops, and when it starts after a crash mid-flight. */
    public void restoreAll(Collection<World> all) {
        eased.clear();
        all.forEach(MachWorlds::restore);
    }
}
