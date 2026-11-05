package de.raindancer.modules.skills.skill.trading;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.skills.listener.ISkillsListener;
import de.raindancer.modules.skills.skill.SkillValues;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Material;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/**
 * The trader's effects. Prices are lowered for the one player as the trading window opens — after the game has worked
 * out its own prices for them, before the offers are sent. A villager puts its prices back itself when the window
 * closes; a wandering trader does not, so what was changed on one is put back here.
 */
public final class TradingListener implements ISkillsListener {

    /** A wandering trader's offers as they were before a player's window opened. */
    private record Before(Merchant merchant, List<MerchantRecipe> recipes, int[] special, int[] maxUses) {
    }

    private final Plugin plugin;
    private final SkillValues values;
    private final TradingRule rule;
    private final RandomGenerator random;
    private final Map<UUID, Before> open = new ConcurrentHashMap<>();
    private final Set<MerchantRecipe> restocking = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>()));

    public TradingListener(Plugin plugin, SkillValues values, TradingRule rule, RandomGenerator random) {
        this.plugin = plugin;
        this.values = values;
        this.rule = rule;
        this.random = random;
    }

    @Override
    public void forget(UUID player) {
        open.remove(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getInventory() instanceof MerchantInventory window) || !(event.getPlayer() instanceof Player player)
                || !(window.getMerchant() instanceof AbstractVillager trader)) {
            return;
        }
        boolean wanderer = trader instanceof WanderingTrader;
        double discount = values.value(player, wanderer ? TradingEffect.WANDERER_DISCOUNT : TradingEffect.DISCOUNT);
        boolean silver = !wanderer && values.has(player, TradingEffect.SILVER_TONGUE);
        int moreUses = wanderer ? (int) Math.round(values.value(player, TradingEffect.WANDERER_STOCK)) : 0;
        if (discount <= 0 && !silver && moreUses <= 0) {
            return;
        }
        List<MerchantRecipe> recipes = new ArrayList<>(trader.getRecipes());
        int[] special = new int[recipes.size()];
        int[] maxUses = new int[recipes.size()];
        for (int index = 0; index < recipes.size(); index++) {
            MerchantRecipe recipe = recipes.get(index);
            special[index] = recipe.getSpecialPrice();
            maxUses[index] = recipe.getMaxUses();
            if (recipe.getIngredients().isEmpty()) {
                continue;
            }
            int base = recipe.getIngredients().getFirst().getAmount();
            int off = 0;
            if (silver) {
                off += rule.demandBump(base, recipe.getDemand(), recipe.getPriceMultiplier());
            }
            int adjusted = recipe.getAdjustedIngredient1().getAmount() - off;
            off += rule.discount(base, adjusted, discount);
            off = Math.min(off, recipe.getAdjustedIngredient1().getAmount() - 1);
            if (off > 0) {
                recipe.setSpecialPrice(recipe.getSpecialPrice() - off);
            }
            if (moreUses > 0) {
                recipe.setMaxUses(recipe.getMaxUses() + moreUses);
            }
        }
        if (wanderer) {
            open.put(player.getUniqueId(), new Before(trader, recipes, special, maxUses));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Before before = open.remove(event.getPlayer().getUniqueId());
        if (before == null) {
            return;
        }
        for (int index = 0; index < before.recipes().size(); index++) {
            before.recipes().get(index).setSpecialPrice(before.special()[index]);
            before.recipes().get(index).setMaxUses(before.maxUses()[index]);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        Player player = event.getPlayer();
        MerchantRecipe trade = event.getTrade();
        ItemStack result = trade.getResult();
        boolean emerald = rule.paysEmeralds(result.getType().getKey().getKey())
                && values.roll(player, TradingEffect.EMERALD_BONUS, random);
        if (emerald) {
            Scheduling.entity(plugin, player, () -> player.getInventory().addItem(new ItemStack(Material.EMERALD))
                    .values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left)));
        }
        if (!(event.getVillager() instanceof Villager villager)) {
            return;
        }
        if (values.has(player, TradingEffect.MERCHANT_PRINCE) && trade.getVillagerExperience() > 0) {
            int more = trade.getVillagerExperience();
            Scheduling.entity(plugin, villager, () -> villager.setVillagerExperience(
                    villager.getVillagerExperience() + more));
        }
        long wait = rule.restockTicks(values.value(player, TradingEffect.RESTOCK));
        // This trade is the one that uses it up: the uses are counted after the event.
        if (wait > 0 && trade.getUses() + 1 >= trade.getMaxUses() && restocking.add(trade)) {
            Scheduling.entityLater(plugin, villager, wait, () -> {
                restocking.remove(trade);
                if (trade.getUses() >= trade.getMaxUses()) {
                    trade.setUses(0);
                }
            });
        }
    }
}
