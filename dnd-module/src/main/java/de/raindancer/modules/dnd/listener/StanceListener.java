package de.raindancer.modules.dnd.listener;

import de.raindancer.modules.dnd.DndServices;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.rules.LoadoutRule;
import de.raindancer.modules.dnd.service.StanceService;
import de.raindancer.modules.dnd.store.IconTags;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The casting stance from the player's side: F takes it up and puts it down, a click casts, and nothing they do
 * with the icons can take one out of the hotbar or put anything into it.
 */
public final class StanceListener implements IDndListener {

    private final DndServices services;

    public StanceListener(DndServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        StanceService stance = services.stance();
        if (stance.isIn(player.getUniqueId())) {
            event.setCancelled(true);
            stance.leave(player, false);
            return;
        }
        Optional<Hero> hero = services.heroes().of(player.getUniqueId());
        if (hero.isEmpty() || services.config().stanceNeedsSneak() && !player.isSneaking()
                || !services.config().worksIn(player.getWorld().getName())) {
            return;
        }
        event.setCancelled(true);
        stance.enter(player, hero.get());
    }

    /**
     * {@link EventPriority#LOWEST} and regardless of cancellation: an icon is never used as the item it looks like,
     * whatever another plugin decided about the block.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        Optional<String> id = IconTags.idOf(held);
        if (id.isEmpty()) {
            if (IconTags.isIcon(event.getItem())) {
                block(event);
            }
            return;
        }
        block(event);
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) {
            return;
        }
        click(player, id.get());
    }

    private static void block(PlayerInteractEvent event) {
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Optional<String> id = IconTags.idOf(event.getPlayer().getInventory().getItemInMainHand());
        if (id.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            click(event.getPlayer(), id.get());
        }
    }

    /** A punch with an icon in hand is a cast, not a punch. Spell damage itself is magic and passes. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPunch(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || event.getDamageSource().getDamageType() != DamageType.PLAYER_ATTACK) {
            return;
        }
        Optional<String> id = IconTags.idOf(player.getInventory().getItemInMainHand());
        if (id.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        click(player, id.get());
    }

    private void click(Player player, String id) {
        if (!services.stance().isIn(player.getUniqueId())) {
            services.stance().purge(player);
            return;
        }
        Optional<Hero> found = services.heroes().of(player.getUniqueId());
        if (found.isEmpty()) {
            services.stance().leave(player, true);
            return;
        }
        Hero hero = found.get();
        switch (id) {
            case IconTags.SHEET -> {
                if (services.metamagic().knows(hero) && !player.isSneaking()) {
                    services.stance().cycle(player, hero);
                } else {
                    services.screens().sheet(player);
                }
            }
            case IconTags.EMPTY -> services.screens().spellbook(player);
            default -> LoadoutRule.castable(id).ifPresent(castable ->
                    services.casting().cast(player, castable, player.isSneaking()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (IconTags.isIcon(event.getItemDrop().getItemStack())) {
            if (services.stance().isIn(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
            } else {
                event.getItemDrop().remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean inStance = services.stance().isIn(player.getUniqueId());
        boolean touchesIcon = IconTags.isIcon(event.getCurrentItem()) || IconTags.isIcon(event.getCursor());
        if (event.getHotbarButton() >= 0) {
            touchesIcon |= IconTags.isIcon(player.getInventory().getItem(event.getHotbarButton()));
        }
        boolean hotbarSlot = event.getClickedInventory() != null
                && event.getClickedInventory().getType() == InventoryType.PLAYER
                && event.getSlot() >= 0 && event.getSlot() < StanceService.SHEET_SLOT + 1;
        if (touchesIcon || inStance && (hotbarSlot || event.getHotbarButton() >= 0)) {
            event.setCancelled(true);
            if (!inStance && touchesIcon) {
                services.stance().purge(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (IconTags.isIcon(event.getOldCursor())) {
            event.setCancelled(true);
            return;
        }
        if (!services.stance().isIn(player.getUniqueId())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                continue;
            }
            int slot = event.getView().convertSlot(raw);
            if (slot >= 0 && slot <= StanceService.SHEET_SLOT) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (IconTags.isIcon(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (IconTags.isIcon(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * {@link EventPriority#LOWEST}: the icons leave the drops and the real hotbar takes their place before any
     * grave or death-chest plugin collects them. With keepInventory the hotbar is simply put back.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        event.getDrops().removeIf(IconTags::isIcon);
        services.concentration().end(player, null);
        if (!services.stance().isIn(player.getUniqueId())) {
            return;
        }
        if (event.getKeepInventory()) {
            services.stance().leave(player, true);
            return;
        }
        List<ItemStack> hotbar = services.stance().takeKeptForDrops(player);
        event.getDrops().addAll(hotbar);
    }

    @Override
    public void forget(UUID player) {
        // What the stance remembers belongs to StanceService, and PresenceListener tells it.
    }

    @Override
    public String describe() {
        return "the casting stance: F, clicks, and keeping the icons where they belong";
    }
}
