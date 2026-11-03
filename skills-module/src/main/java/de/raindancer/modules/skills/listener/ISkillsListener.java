package de.raindancer.modules.skills.listener;

import org.bukkit.event.Listener;

import java.util.UUID;

/** A listener belonging to this module. {@link #forget} is on the interface so it cannot be left out quietly. */
public interface ISkillsListener extends Listener {

    void forget(UUID player);

    default String describe() {
        String name = getClass().getSimpleName();
        return name.isEmpty() ? getClass().getName() : name;
    }
}
