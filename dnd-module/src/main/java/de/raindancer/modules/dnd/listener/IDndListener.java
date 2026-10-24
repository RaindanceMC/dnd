package de.raindancer.modules.dnd.listener;

import org.bukkit.event.Listener;

import java.util.UUID;

/** A listener belonging to this module. {@link #forget} is on the interface so it cannot be left out quietly. */
public interface IDndListener extends Listener {

    void forget(UUID player);

    default String describe() {
        String name = getClass().getSimpleName();
        return name.isEmpty() ? getClass().getName() : name;
    }
}
