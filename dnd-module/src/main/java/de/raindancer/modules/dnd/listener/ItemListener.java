package de.raindancer.modules.dnd.listener;

import de.raindancer.modules.dnd.DndServices;
import de.raindancer.modules.dnd.model.MagicItem;
import de.raindancer.modules.dnd.store.IconTags;
import de.raindancer.modules.dnd.store.ItemTags;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

/** Magic items in use: a scroll read, a wand zapped, a bag opened, a potion drunk, a magic bow's arrow. */
public final class ItemListener implements IDndListener {

    private final DndServices services;

    public ItemListener(DndServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL
                || event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (IconTags.isIcon(held)) {
            return;
        }
        Optional<MagicItem> item = ItemTags.itemOf(held);
        if (item.isEmpty()) {
            return;
        }
        switch (item.get()) {
            case SPELL_SCROLL -> {
                deny(event);
                services.items().read(player, held);
            }
            case WAND_OF_MAGIC_MISSILES -> {
                deny(event);
                services.items().zap(player, held, player.isSneaking());
            }
            case BAG_OF_HOLDING -> {
                deny(event);
                services.items().openBag(player);
            }
            default -> {
            }
        }
    }

    private static void deny(PlayerInteractEvent event) {
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        ItemTags.itemOf(event.getItem()).filter(MagicItem::isPotion)
                .ifPresent(item -> services.items().drink(event.getPlayer(), item));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        int bonus = services.items().weaponBonus(event.getBow());
        if (bonus > 0 && event.getProjectile() instanceof Projectile arrow) {
            ItemTags.bonus(arrow, bonus);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrow(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Projectile arrow) {
            int bonus = ItemTags.bonusOf(arrow);
            if (bonus > 0) {
                event.setDamage(event.getDamage() + bonus);
            }
        }
    }

    @Override
    public void forget(UUID player) {
        // Everything an item needs is written on the item.
    }

    @Override
    public String describe() {
        return "magic items: scrolls, wands, the bag, potions and magic bows";
    }
}
