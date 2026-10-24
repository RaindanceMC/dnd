package de.raindancer.modules.dnd.listener;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.dnd.DndServices;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

/** Coming, going, changing world, dressing and dying: the hero follows. */
public final class PresenceListener implements IDndListener {

    private final DndServices services;

    public PresenceListener(DndServices services) {
        this.services = services;
    }

    /** First thing: a hotbar left behind by a stance that never ended (a crash) is put back. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        services.stance().restore(player);
        services.heroes().joined(player);
    }

    /**
     * {@link EventPriority#LOWEST}: the real hotbar goes back before anything else on quit reads the inventory —
     * an inventory snapshot, a network sync, the server's own save.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        services.stance().leave(player, true);
        services.concentration().forget(player);
        services.features().foldWings(player);
        UUID id = player.getUniqueId();
        forget(id);
        services.heroes().left(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!services.config().worksIn(player.getWorld().getName())) {
            services.stance().leave(player, false);
            services.concentration().end(player, null);
            services.features().foldWings(player);
        }
        services.body().refresh(player, services.heroes().of(player.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmour(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        Scheduling.entity(services.plugin(), player,
                () -> services.body().refresh(player, services.heroes().of(player.getUniqueId())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Scheduling.entityLater(services.plugin(), player, 1L, () -> {
            services.body().refresh(player, services.heroes().of(player.getUniqueId()));
            player.setHealth(de.raindancer.modules.dnd.service.TargetService.maxHealth(player));
        });
    }

    @Override
    public void forget(UUID player) {
        services.casting().forget(player);
        services.combat().forget(player);
        services.resting().forget(player);
        services.stance().forget(player);
        services.features().forget(player);
        services.surges().forget(player);
        services.core().actionBars().forget(player);
    }

    @Override
    public String describe() {
        return "joining, leaving, changing world, armour and respawning";
    }
}
