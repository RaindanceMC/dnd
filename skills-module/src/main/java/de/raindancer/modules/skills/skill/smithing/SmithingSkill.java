package de.raindancer.modules.skills.skill.smithing;

import com.destroystokyo.paper.event.block.AnvilDamagedEvent;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.modules.skills.SkillsServices;
import de.raindancer.modules.skills.listener.ISkillsListener;
import de.raindancer.modules.skills.screen.ConfirmScreen;
import de.raindancer.modules.skills.skill.gathering.GatherRule;
import de.raindancer.modules.skills.skill.gathering.Hands;
import de.raindancer.modules.skills.skill.gathering.Loot;
import de.raindancer.modules.skills.skill.gathering.SkillValues;
import de.raindancer.modules.skills.store.ItemTags;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.inventory.view.AnvilView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static de.raindancer.modules.skills.skill.gathering.SkillValues.get;
import static de.raindancer.modules.skills.skill.gathering.SkillValues.has;

/**
 * The Smithing tree at work: anvil work for fewer levels, less prior work and repairs that mend more, Master Smith's
 * work that is never too expensive, anvils that do not wear, salvage, armour and weapons that wear slower, smithing
 * templates kept and a netherite scrap back. Salvage gives back at most what went into an item, less its wear, so
 * nothing is ever made out of nothing; an item another plugin made is never salvaged.
 */
public final class SmithingSkill implements ISkillsListener {

    /** Far above any cost the game could reach, for a Master Smith's window. */
    private static final int NO_LIMIT = 10_000;

    private final SkillsServices services;
    private final SkillValues values;
    private final SmithingRule rule = new SmithingRule();
    private final GatherRule gather = new GatherRule();

    public SmithingSkill(SkillsServices services, SkillValues values) {
        this.services = services;
        this.values = values;
    }

    private static double roll() {
        return ThreadLocalRandom.current().nextDouble();
    }

    private static String name(Material material) {
        return material.getKey().getKey().replace('_', ' ');
    }

    /** Master Smith: the window's own limit raised, so the game works out the result at all. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getView() instanceof AnvilView anvil && event.getPlayer() instanceof Player player
                && has(values.of(player), SmithingEffect.SMITH_NO_LIMIT)) {
            anvil.setMaximumRepairCost(NO_LIMIT);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null || result.isEmpty() || !(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        Map<String, Double> tree = values.of(player);
        if (tree.isEmpty()) {
            return;
        }
        AnvilView view = event.getView();
        AnvilInventory anvil = event.getInventory();
        ItemStack first = anvil.getFirstItem();
        ItemStack second = anvil.getSecondItem();
        ItemStack made = result.clone();
        ItemMeta meta = made.getItemMeta();
        if (meta instanceof Damageable damaged && first != null && first.getItemMeta() instanceof Damageable was
                && second != null && second.getType() != first.getType()
                && second.getType() != Material.ENCHANTED_BOOK) {
            damaged.setDamage(rule.mended(was.getDamage(), damaged.getDamage(), get(tree, SmithingEffect.SMITH_MEND)));
        }
        if (meta instanceof Repairable work) {
            int brought = Math.max(priorWork(first), priorWork(second));
            work.setRepairCost(rule.priorWork(brought, work.getRepairCost(), get(tree, SmithingEffect.SMITH_REWORK)));
        }
        made.setItemMeta(meta);
        event.setResult(made);
        boolean noLimit = has(tree, SmithingEffect.SMITH_NO_LIMIT);
        if (noLimit && view.getMaximumRepairCost() < NO_LIMIT) {
            view.setMaximumRepairCost(NO_LIMIT);
        }
        view.setRepairCost(rule.cost(view.getRepairCost(), get(tree, SmithingEffect.SMITH_DISCOUNT), noLimit));
    }

    private static int priorWork(ItemStack stack) {
        return stack != null && stack.getItemMeta() instanceof Repairable work ? work.getRepairCost() : 0;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnvilWorn(AnvilDamagedEvent event) {
        for (HumanEntity viewer : event.getInventory().getViewers()) {
            if (viewer instanceof Player player && has(values.of(player), SmithingEffect.SMITH_ANVIL_SAVE)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent event) {
        if (rule.gear(event.getItem().getType())
                && gather.chance(get(values.of(event.getPlayer()), SmithingEffect.SMITH_GEAR_WEAR), roll())) {
            event.setCancelled(true);
        }
    }

    /**
     * A template kept, a netherite scrap back: the tick after, and only if the work really took one template —
     * counted in the table, the bag and on the cursor, so closing the window is never mistaken for it.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmith(SmithItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        SmithingInventory table = event.getInventory();
        ItemStack template = table.getInputTemplate();
        ItemStack result = table.getResult();
        if (template == null || template.isEmpty()) {
            return;
        }
        Map<String, Double> tree = values.of(player);
        Material kind = template.getType();
        double keep = get(tree, SmithingEffect.SMITH_TEMPLATE);
        boolean scrap = has(tree, SmithingEffect.SMITH_NETHERITE_SCRAP)
                && rule.netheriteUpgrade(kind, result == null ? null : result.getType());
        if (keep <= 0 && !scrap) {
            return;
        }
        int before = templates(player, table, kind);
        Scheduling.entityLater(services.plugin(), player, 1, () -> {
            if (!rule.templateTaken(before, templates(player, table, kind))) {
                return;
            }
            if (gather.chance(keep, roll())) {
                give(player, new ItemStack(kind));
                services.messages().send(player, "dnd.smithing.template-kept");
            }
            if (scrap) {
                give(player, new ItemStack(Material.NETHERITE_SCRAP));
                services.messages().send(player, "dnd.smithing.scrap-back");
            }
        });
    }

    private static int templates(Player player, SmithingInventory table, Material kind) {
        int count = 0;
        ItemStack slot = table.getInputTemplate();
        if (slot != null && slot.getType() == kind) {
            count += slot.getAmount();
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == kind) {
                count += stack.getAmount();
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        return cursor.getType() == kind ? count + cursor.getAmount() : count;
    }

    private static void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    /** Salvage: crouch and use a tool, a weapon or armour on an anvil — what it gives back is asked first. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSalvage(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || block == null
                || !block.getType().name().endsWith("ANVIL") || !player.isSneaking()
                || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!rule.salvageable(held.getType())) {
            return;
        }
        Map<String, Double> tree = values.of(player);
        double percent = get(tree, SmithingEffect.SMITH_SALVAGE);
        if (percent <= 0) {
            return;
        }
        event.setCancelled(true);
        var messages = services.messages();
        if (ItemTags.itemOf(held).isPresent() || !held.getItemMeta().getPersistentDataContainer().isEmpty()) {
            messages.send(player, "dnd.smithing.salvage-refused");
            return;
        }
        int[] wear = Hands.wear(held);
        List<Loot> back = rule.salvage(held.getType(), wear[0], wear[1], percent, roll());
        if (back.isEmpty()) {
            messages.send(player, "dnd.smithing.salvage-worn");
            return;
        }
        Map<String, Integer> enchantments = new HashMap<>();
        Map<String, Enchantment> byKey = new HashMap<>();
        held.getEnchantments().forEach((enchantment, level) -> {
            enchantments.put(enchantment.getKey().asString(), level);
            byKey.put(enchantment.getKey().asString(), enchantment);
        });
        Optional<Enchantment> book = has(tree, SmithingEffect.SMITH_SALVAGE_BOOK)
                ? rule.best(enchantments).map(byKey::get) : Optional.empty();
        List<String> consequences = new ArrayList<>();
        for (Loot loot : back) {
            consequences.add(messages.raw("dnd.smithing.salvage-gives").replace("<amount>",
                    String.valueOf(loot.amount())).replace("<item>", name(loot.material())));
        }
        if (book.isPresent()) {
            consequences.add(messages.raw("dnd.smithing.salvage-book").replace("<enchantment>",
                    book.get().getKey().getKey().replace('_', ' ')));
        } else if (!enchantments.isEmpty()) {
            consequences.add(messages.raw("dnd.smithing.salvage-enchantments-lost"));
        }
        ItemStack asked = held.clone();
        new ConfirmScreen(player, services.brand(), null,
                messages.raw("dnd.smithing.salvage-question").replace("<item>", name(held.getType())), consequences,
                () -> salvage(player, asked, back, book.map(enchantment -> Map.entry(enchantment,
                        enchantments.get(enchantment.getKey().asString()))))).open();
    }

    private void salvage(Player player, ItemStack asked, List<Loot> back,
                         Optional<Map.Entry<Enchantment, Integer>> book) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!held.isSimilar(asked) || held.getAmount() != asked.getAmount()) {
            services.messages().send(player, "dnd.smithing.salvage-moved");
            return;
        }
        player.getInventory().setItemInMainHand(null);
        back.forEach(loot -> give(player, new ItemStack(loot.material(), loot.amount())));
        book.ifPresent(kept -> {
            ItemStack stored = new ItemStack(Material.ENCHANTED_BOOK);
            if (stored.getItemMeta() instanceof EnchantmentStorageMeta meta) {
                meta.addStoredEnchant(kept.getKey(), kept.getValue(), true);
                stored.setItemMeta(meta);
                give(player, stored);
            }
        });
        services.effects().play(player.getUniqueId(), Cues.REWARD);
        services.messages().send(player, "dnd.smithing.salvaged", "item", name(asked.getType()));
    }

    @Override
    public void forget(UUID player) {
        // nothing is kept per player
    }
}
