package de.raindancer.modules.dnd.listener;

import de.raindancer.modules.dnd.AdventurerAdvancements;
import de.raindancer.modules.dnd.model.MagicItem;
import de.raindancer.modules.dnd.service.MachService;
import de.raindancer.modules.dnd.store.ItemTags;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** The Potion of Mach Fuck: brewed, drunk, and the crashes it would cause forgiven. */
public final class MachListener implements IDndListener {

    /** Who stands this close to a brewing stand when it brews the potion is taken to have brewed it. */
    private static final double BREWER_REACH = 8;

    private final MachService mach;
    private final AdventurerAdvancements advancements;

    public MachListener(MachService mach, AdventurerAdvancements advancements) {
        this.mach = mach;
        this.advancements = advancements;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        ItemTags.itemOf(event.getItem()).filter(MagicItem::isPotion).ifPresent(item -> {
            advancements.drank(event.getPlayer().getUniqueId());
            if (item == MagicItem.POTION_OF_MACH_FUCK) {
                mach.drank(event.getPlayer());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        int potions = (int) event.getResults().stream().filter(MachListener::isMach).count();
        if (potions == 0) {
            return;
        }
        Location stand = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        for (Player near : stand.getNearbyPlayers(BREWER_REACH)) {
            advancements.brewedMach(near.getUniqueId(), potions);
        }
    }

    private static boolean isMach(ItemStack item) {
        return ItemTags.itemOf(item).filter(found -> found == MagicItem.POTION_OF_MACH_FUCK).isPresent();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && (event.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL
                || event.getCause() == EntityDamageEvent.DamageCause.FALL)
                && mach.shielded(player)) {
            event.setCancelled(true);
            advancements.lithobraked(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @Override
    public void forget(UUID player) {
        mach.forget(player);
    }
}
