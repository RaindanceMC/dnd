package de.raindancer.modules.skills.listener;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.skills.SkillsServices;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

/** Coming, going, changing world, dressing and dying: the hero follows. */
public final class PresenceListener implements ISkillsListener {

    private final SkillsServices services;

    public PresenceListener(SkillsServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        services.heroes().joined(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        forget(id);
        services.heroes().left(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        services.body().refresh(player, services.heroes().of(player.getUniqueId()));
    }

    /** Boots of Striding go on and off with the armour; the off hand's ring or amulet with a swap or a new slot. */
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
            player.setHealth(de.raindancer.modules.skills.service.TargetService.maxHealth(player));
        });
    }

    @Override
    public void forget(UUID player) {
        services.combat().forget(player);
        services.core().actionBars().forget(player);
    }

    @Override
    public String describe() {
        return "joining, leaving, changing world, armour and respawning";
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Scheduling.entity(services.plugin(), player,
                () -> services.body().refresh(player, services.heroes().of(player.getUniqueId())));
    }
}
