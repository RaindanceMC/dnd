package de.raindancer.modules.skills.skill.cooking;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.modules.api.ModuleContext;
import de.raindancer.modules.skills.SkillsServices;
import de.raindancer.modules.skills.listener.ISkillsListener;
import de.raindancer.modules.skills.skill.Chances;
import de.raindancer.modules.skills.skill.SkillValues;
import de.raindancer.modules.skills.skill.Workers;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cooking at work: in a furnace or smoker — a blast furnace cooks no food — that the player last opened, and at the
 * table. A serving more is only ever cooked from raw food, which nothing turns cooked food back into.
 */
public final class CookingListener implements ISkillsListener {

    private static final int FULL = 20;

    private final Plugin plugin;
    private final SkillValues values;
    private final CookingRule rule = new CookingRule();
    private final Workers cooks = new Workers();

    CookingListener(Plugin plugin, SkillValues values) {
        this.plugin = plugin;
        this.values = values;
    }

    public static void start(ModuleContext context, SkillsServices services) {
        context.listener(new CookingListener(context.plugin(), new SkillValues(services)));
    }

    private static boolean kitchen(Block block) {
        return block.getType() == Material.FURNACE || block.getType() == Material.SMOKER;
    }

    private static FoodProperties food(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : stack.getData(DataComponentTypes.FOOD);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory() instanceof FurnaceInventory furnace && furnace.getLocation() != null) {
            cooks.worked(furnace.getLocation(), event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCook(FurnaceStartSmeltEvent event) {
        if (!kitchen(event.getBlock()) || food(event.getRecipe().getResult()) == null) {
            return;
        }
        Player cook = cooks.of(plugin.getServer(), event.getBlock());
        if (cook == null) {
            return;
        }
        event.setTotalCookTime(rule.cookTime(event.getTotalCookTime(), values.value(cook, CookingEffect.SPEED),
                values.has(cook, CookingEffect.MASTER_CHEF)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFuel(FurnaceBurnEvent event) {
        if (!kitchen(event.getBlock())) {
            return;
        }
        Player cook = cooks.of(plugin.getServer(), event.getBlock());
        if (cook != null) {
            event.setBurnTime(rule.burnTime(event.getBurnTime(), values.value(cook, CookingEffect.FUEL)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTakeOut(FurnaceExtractEvent event) {
        if (!kitchen(event.getBlock()) || food(event.getItemStack()) == null) {
            return;
        }
        Player cook = event.getPlayer();
        int more = Chances.times(event.getItemAmount() * values.value(cook, CookingEffect.SERVINGS),
                ThreadLocalRandom.current().nextDouble());
        if (more > 0) {
            cook.getInventory().addItem(new ItemStack(event.getItemType(), more)).values()
                    .forEach(left -> cook.getWorld().dropItemNaturally(cook.getLocation(), left));
        }
    }

    /** At the table: a meal fills more, blesses, feeds the others — a tick later, after the game's own eating. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        FoodProperties food = food(event.getItem());
        if (food == null) {
            return;
        }
        Player eater = event.getPlayer();
        Material meal = event.getItem().getType();
        double filling = values.value(eater, CookingEffect.FILLING);
        double blessed = values.value(eater, CookingEffect.WELL_FED);
        boolean feast = values.has(eater, CookingEffect.FEAST);
        if (filling <= 0 && blessed <= 0 && !feast) {
            return;
        }
        Scheduling.entityLater(plugin, eater, 1, () -> {
            feed(eater, rule.moreFood(food.nutrition(), filling, ThreadLocalRandom.current().nextDouble()),
                    rule.moreSaturation(food.saturation(), filling));
            if (blessed > 0) {
                rule.blessing(meal).ifPresent(blessing -> {
                    PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(blessing.effect()));
                    if (type != null) {
                        eater.addPotionEffect(new PotionEffect(type, blessing.ticks(blessed), blessing.amplifier()));
                    }
                });
            }
            if (feast) {
                share(eater, rule.shared(food.nutrition()));
            }
        });
    }

    private void share(Player host, int nutrition) {
        if (nutrition <= 0) {
            return;
        }
        double reach = CookingRule.FEAST_RADIUS * (double) CookingRule.FEAST_RADIUS;
        for (Player guest : host.getWorld().getPlayers()) {
            if (guest != host && guest.getLocation().distanceSquared(host.getLocation()) <= reach) {
                String name = host.getName();
                Scheduling.entity(plugin, guest, () -> {
                    feed(guest, nutrition, 0);
                    values.notice(guest, Cues.HEAL, "dnd.nature.feast", "player", name);
                });
            }
        }
    }

    private static void feed(Player player, int hunger, float saturation) {
        if (hunger <= 0 && saturation <= 0) {
            return;
        }
        int level = Math.min(FULL, player.getFoodLevel() + Math.max(0, hunger));
        player.setFoodLevel(level);
        player.setSaturation(Math.min(level, player.getSaturation() + Math.max(0, saturation)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onQueasy(EntityPotionEffectEvent event) {
        if (event.getCause() == EntityPotionEffectEvent.Cause.FOOD && event.getEntity() instanceof Player eater
                && rule.queasy(event.getModifiedType().getKey().getKey())
                && values.has(eater, CookingEffect.IRON_STOMACH)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @Override
    public void forget(UUID player) {
        cooks.forget(player);
    }
}
