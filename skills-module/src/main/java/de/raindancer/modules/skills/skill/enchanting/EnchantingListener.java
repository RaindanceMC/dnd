package de.raindancer.modules.skills.skill.enchanting;

import de.raindancer.core.platform.util.Cooldowns;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.skills.listener.ISkillsListener;
import de.raindancer.modules.skills.skill.SkillValues;
import com.destroystokyo.paper.event.block.AnvilDamagedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;

/**
 * The enchanter's effects. With more bookshelf power or the treasure mastery, the table's offers are worked out here
 * from the player's own enchantment seed — the clue shown is the first of what is then given, so what was offered
 * is what comes out.
 */
public final class EnchantingListener implements ISkillsListener {

    private record Offered(ItemStack item, Map<Integer, Map<Enchantment, Integer>> bySlot) {
    }

    private static final Duration REROLL_EVERY = Duration.ofMinutes(1);

    private final Plugin plugin;
    private final SkillValues values;
    private final EnchantingRule rule;
    private final Messages messages;
    private final RandomGenerator random;
    private final Cooldowns<UUID> rerolls;
    private final Map<UUID, Offered> offered = new ConcurrentHashMap<>();

    public EnchantingListener(Plugin plugin, SkillValues values, EnchantingRule rule, Messages messages,
                              RandomGenerator random, LongSupplier clock) {
        this.plugin = plugin;
        this.values = values;
        this.rule = rule;
        this.messages = messages;
        this.random = random;
        this.rerolls = new Cooldowns<>(clock);
        this.rerolls.every(REROLL_EVERY);
    }

    @Override
    public void forget(UUID player) {
        offered.remove(player);
        rerolls.forget(player);
    }

    // ------------------------------------------------------------------ the table

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOffers(PrepareItemEnchantEvent event) {
        Player player = event.getEnchanter();
        offered.remove(player.getUniqueId());
        double more = values.value(player, EnchantingEffect.BOOKSHELF_POWER);
        boolean treasure = values.has(player, EnchantingEffect.TREASURE_OFFERS);
        int around = event.getEnchantmentBonus();
        int shelves = rule.shelves(around, more);
        if (shelves <= around && !treasure) {
            return;
        }
        EnchantmentOffer[] offers = event.getOffers();
        int seed = player.getEnchantmentSeed();
        int[] costs = rule.offerCosts(shelves, new Random(seed));
        Map<Integer, Map<Enchantment, Integer>> bySlot = new HashMap<>();
        for (int slot = 0; slot < offers.length && slot < costs.length; slot++) {
            int cost = shelves > around ? costs[slot] : offers[slot] == null ? 0 : offers[slot].getCost();
            Map<Enchantment, Integer> picked = cost <= 0 ? Map.of() : enchantments(event.getItem(), cost, treasure,
                    seed + slot);
            if (picked.isEmpty()) {
                offers[slot] = null;
                continue;
            }
            Map.Entry<Enchantment, Integer> clue = picked.entrySet().iterator().next();
            offers[slot] = new EnchantmentOffer(clue.getKey(), clue.getValue(), cost);
            bySlot.put(slot, picked);
        }
        offered.put(player.getUniqueId(), new Offered(event.getItem().clone(), bySlot));
    }

    /** What the table would put on the item at so many levels, curses taken out, in the order it rolled them. */
    private static Map<Enchantment, Integer> enchantments(ItemStack item, int levels, boolean treasure, int seed) {
        ItemStack target = item.getType() == Material.BOOK ? new ItemStack(Material.BOOK) : item.clone();
        ItemStack done = Bukkit.getItemFactory().enchantWithLevels(target, levels, treasure, new Random(seed));
        ItemMeta meta = done.getItemMeta();
        Map<Enchantment, Integer> rolled = meta instanceof EnchantmentStorageMeta book ? book.getStoredEnchants()
                : meta == null ? Map.of() : meta.getEnchants();
        Map<Enchantment, Integer> kept = new LinkedHashMap<>();
        rolled.forEach((enchantment, level) -> {
            if (!enchantment.isCursed()) {
                kept.put(enchantment, level);
            }
        });
        return kept;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        Offered ours = offered.remove(player.getUniqueId());
        if (ours != null && ours.item().isSimilar(event.getItem())) {
            Map<Enchantment, Integer> picked = ours.bySlot().get(event.whichButton());
            if (picked != null) {
                event.getEnchantsToAdd().clear();
                event.getEnchantsToAdd().putAll(picked);
            }
        }
        int levels = event.whichButton() + 1;
        boolean refund = values.roll(player, EnchantingEffect.LEVEL_REFUND, random);
        boolean lapis = values.roll(player, EnchantingEffect.LAPIS_SAVE, random);
        if (!refund && !lapis) {
            return;
        }
        EnchantingInventory table = event.getInventory() instanceof EnchantingInventory inventory ? inventory : null;
        // After the enchanting is paid for: the levels and lapis go first, then some come back.
        Scheduling.entity(plugin, player, () -> {
            if (refund) {
                player.giveExpLevels(levels);
            }
            if (lapis) {
                giveLapis(player, table, levels);
            }
        });
    }

    private static void giveLapis(Player player, EnchantingInventory table, int count) {
        ItemStack back = new ItemStack(Material.LAPIS_LAZULI, count);
        ItemStack secondary = table == null ? null : table.getSecondary();
        if (table != null && player.getOpenInventory().getTopInventory().equals(table)) {
            if (secondary == null || secondary.isEmpty()) {
                table.setSecondary(back);
                return;
            }
            if (secondary.isSimilar(back) && secondary.getAmount() + count <= secondary.getMaxStackSize()) {
                secondary.setAmount(secondary.getAmount() + count);
                return;
            }
        }
        player.getInventory().addItem(back).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onReroll(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.ENCHANTING_TABLE) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking() || !player.getInventory().getItemInMainHand().isEmpty()
                || !values.has(player, EnchantingEffect.FRESH_OFFERS)) {
            return;
        }
        event.setCancelled(true);
        if (player.getLevel() < 1) {
            messages.send(player, "dnd.enchanting.reroll-no-level");
            return;
        }
        if (!rerolls.tryUse(player.getUniqueId())) {
            messages.send(player, "dnd.enchanting.reroll-wait", "seconds", String.valueOf(rerolls
                    .remaining(player.getUniqueId()).map(Duration::toSeconds).orElse(0L) + 1));
            return;
        }
        player.setLevel(player.getLevel() - 1);
        player.setEnchantmentSeed(random.nextInt());
        offered.remove(player.getUniqueId());
        messages.send(player, "dnd.enchanting.rerolled");
    }

    // ------------------------------------------------------------------ the anvil

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player) || event.getResult() == null
                || event.getResult().isEmpty()) {
            return;
        }
        double cheaper = values.value(player, EnchantingEffect.ANVIL_DISCOUNT);
        if (cheaper > 0) {
            event.getView().setRepairCost(rule.anvilCost(event.getView().getRepairCost(), cheaper));
        }
        if (values.has(player, EnchantingEffect.NO_PRIOR_PENALTY)) {
            ItemStack result = event.getResult().clone();
            if (result.getItemMeta() instanceof Repairable worked) {
                worked.setRepairCost(rule.priorWork(priorWork(event.getInventory().getFirstItem()),
                        priorWork(event.getInventory().getSecondItem())));
                result.setItemMeta(worked);
                event.setResult(result);
            }
        }
    }

    private static int priorWork(ItemStack item) {
        return item != null && item.getItemMeta() instanceof Repairable repairable ? repairable.getRepairCost() : 0;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnvilWorn(AnvilDamagedEvent event) {
        if (event.getView().getPlayer() instanceof Player player
                && values.roll(player, EnchantingEffect.ANVIL_SAVE, random)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ orbs

    @EventHandler(priority = EventPriority.HIGH)
    public void onOrb(PlayerExpChangeEvent event) {
        double more = values.value(event.getPlayer(), EnchantingEffect.ORB_XP);
        if (more > 0) {
            event.setAmount(rule.orbs(event.getAmount(), more));
        }
    }
}
