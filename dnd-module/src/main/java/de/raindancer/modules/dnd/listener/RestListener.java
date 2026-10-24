package de.raindancer.modules.dnd.listener;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.dnd.DndServices;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.TimeSkipEvent;

import java.util.UUID;

/** A night slept through is a long rest for everyone who slept. */
public final class RestListener implements IDndListener {

    private final DndServices services;

    public RestListener(DndServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNightSkipped(TimeSkipEvent event) {
        if (event.getSkipReason() != TimeSkipEvent.SkipReason.NIGHT_SKIP) {
            return;
        }
        for (Player player : event.getWorld().getPlayers()) {
            if (player.isSleeping()) {
                Scheduling.entity(services.plugin(), player, () -> services.resting().longRest(player, false));
            }
        }
    }

    @Override
    public void forget(UUID player) {
        // Nothing remembered here.
    }

    @Override
    public String describe() {
        return "long rests from sleeping through the night";
    }
}
