package de.raindancer.modules.skills.skill.artisan;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.world.activity.Crafts;
import de.raindancer.modules.skills.listener.ISkillsListener;
import de.raindancer.modules.skills.service.BodyService;
import de.raindancer.modules.skills.skill.SkillValues;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;
import java.util.random.RandomGenerator;

/**
 * The Artisan's effects. A crafted tool carries how much tougher it is in its own data, written when it is made; a
 * furnace carries its loader's speed, fuel and double-smelt in its block's data, written when they close it with
 * something in it to smelt — and wiped the moment a hopper feeds it, so an automatic smelter is nobody's.
 */
public final class ArtisanListener implements ISkillsListener {

    static final NamespacedKey TOUGH = new NamespacedKey(BodyService.NAMESPACE, "artisan_tough");
    static final NamespacedKey SPEED = new NamespacedKey(BodyService.NAMESPACE, "artisan_speed");
    static final NamespacedKey FUEL = new NamespacedKey(BodyService.NAMESPACE, "artisan_fuel");
    static final NamespacedKey DOUBLE = new NamespacedKey(BodyService.NAMESPACE, "artisan_double");
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final double FORGE_DOUBLE = 25;

    private final Plugin plugin;
    private final SkillValues values;
    private final ArtisanRule rule;
    private final UnaryOperator<String> wording;
    private final RandomGenerator random;
    private final LongSupplier clock;
    private final Map<UUID, int[]> minute = new ConcurrentHashMap<>();

    /** @param wording a line of messages.yml by its key, as written there */
    public ArtisanListener(Plugin plugin, SkillValues values, ArtisanRule rule, UnaryOperator<String> wording,
                           RandomGenerator random, LongSupplier clock) {
        this.plugin = plugin;
        this.values = values;
        this.rule = rule;
        this.wording = wording;
        this.random = random;
        this.clock = clock;
    }

    @Override
    public void forget(UUID player) {
        minute.remove(player);
    }

    private static String key(Material material) {
        return material.getKey().getKey();
    }

    // ------------------------------------------------------------------ the workbench

    /** A tool in the result slot is shown — and taken — already tougher, with Masterwork's Unbreaking. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (!(event.getView().getPlayer() instanceof Player crafter) || result == null || result.isEmpty()
                || result.getType().getMaxDurability() <= 0) {
            return;
        }
        int tougher = (int) Math.round(values.value(crafter, ArtisanEffect.TOUGHER_TOOLS));
        boolean masterwork = values.has(crafter, ArtisanEffect.MASTERWORK);
        if (tougher <= 0 && !masterwork) {
            return;
        }
        ItemStack made = result.clone();
        ItemMeta meta = made.getItemMeta();
        if (meta == null) {
            return;
        }
        if (tougher > 0) {
            meta.getPersistentDataContainer().set(TOUGH, PersistentDataType.INTEGER, tougher);
            List<net.kyori.adventure.text.Component> lore = meta.hasLore() && meta.lore() != null
                    ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(MINI.deserialize(wording.apply("dnd.artisan.tougher-lore")
                    .replace("<percent>", String.valueOf(tougher))).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        }
        Enchantment unbreaking = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
        if (masterwork && unbreaking != null && unbreaking.canEnchantItem(made) && !meta.hasEnchant(unbreaking)) {
            meta.addEnchant(unbreaking, 1, false);
        }
        made.setItemMeta(meta);
        event.getInventory().setResult(made);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player crafter) || event.getRecipe() == null) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        List<String> ingredients = new ArrayList<>();
        List<Integer> stacks = new ArrayList<>();
        for (ItemStack each : event.getInventory().getMatrix()) {
            if (each != null && !each.isEmpty()) {
                ingredients.add(key(each.getType()));
                stacks.add(each.getAmount());
            }
        }
        if (!rule.mayBonus(key(result.getType()), ingredients, result.getType().getMaxDurability() > 0)) {
            return;
        }
        int crafts = event.isShiftClick() ? Crafts.times(stacks) : 1;
        int extra = rule.bonus(crafts, values.value(crafter, ArtisanEffect.CRAFT_EXTRA), random);
        int refunds = rule.bonus(crafts, values.value(crafter, ArtisanEffect.INGREDIENT_REFUND), random);
        extra = take(crafter.getUniqueId(), extra);
        refunds = take(crafter.getUniqueId(), refunds);
        List<ItemStack> given = new ArrayList<>();
        if (extra > 0) {
            ItemStack more = result.clone();
            more.setAmount(extra);
            given.add(more);
        }
        for (int each = 0; each < refunds; each++) {
            rule.refund(ingredients, random.nextInt(ingredients.size()))
                    .map(id -> Material.matchMaterial(id))
                    .ifPresent(material -> given.add(new ItemStack(material)));
        }
        if (given.isEmpty()) {
            return;
        }
        // After the click: the craft itself has to be done before anything lands in the inventory beside it.
        Scheduling.entity(plugin, crafter, () -> {
            for (ItemStack stack : given) {
                crafter.getInventory().addItem(stack).values()
                        .forEach(left -> crafter.getWorld().dropItemNaturally(crafter.getLocation(), left));
            }
        });
    }

    /** How many of {@code wanted} extras still fit in this player's minute, counted as given. */
    private int take(UUID player, int wanted) {
        if (wanted <= 0) {
            return 0;
        }
        int now = (int) (clock.getAsLong() / 60_000L);
        int[] used = minute.compute(player, (id, held) -> held == null || held[0] != now ? new int[]{now, 0} : held);
        synchronized (used) {
            int allowed = rule.allowed(used[1], wanted);
            used[1] += allowed;
            return allowed;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent event) {
        ItemMeta meta = event.getItem().getItemMeta();
        Integer tougher = meta == null ? null : meta.getPersistentDataContainer().get(TOUGH, PersistentDataType.INTEGER);
        if (tougher == null || tougher <= 0) {
            return;
        }
        int spared = 0;
        double chance = rule.spareWear(tougher);
        for (int point = 0; point < event.getDamage(); point++) {
            if (random.nextDouble() < chance) {
                spared++;
            }
        }
        event.setDamage(event.getDamage() - spared);
        if (event.getDamage() <= 0) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ the furnace

    private static boolean smelter(InventoryType type) {
        // A smoker cooks food, which is Cooking's.
        return type == InventoryType.FURNACE || type == InventoryType.BLAST_FURNACE;
    }

    /** Closed with something in it to smelt: the furnace is theirs, with their numbers, until a hopper feeds it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory() instanceof FurnaceInventory furnace) || !smelter(furnace.getType())
                || !(event.getPlayer() instanceof Player loader) || furnace.getHolder() == null) {
            return;
        }
        ItemStack smelting = furnace.getSmelting();
        Block block = furnace.getHolder().getBlock();
        if (smelting == null || smelting.isEmpty()) {
            return;
        }
        stamp(block, values.value(loader, ArtisanEffect.FURNACE_SPEED), values.value(loader, ArtisanEffect.FUEL_LAST),
                values.value(loader, ArtisanEffect.DOUBLE_SMELT));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (event.getDestination() instanceof FurnaceInventory furnace && furnace.getHolder() != null) {
            Block block = furnace.getHolder().getBlock();
            if (stamped(block)) {
                stamp(block, 0, 0, 0);
            }
        }
    }

    private static boolean stamped(Block block) {
        return block.getState(false) instanceof Furnace furnace
                && furnace.getPersistentDataContainer().has(SPEED, PersistentDataType.DOUBLE);
    }

    private static void stamp(Block block, double speed, double fuel, double twice) {
        BlockState state = block.getState();
        if (!(state instanceof Furnace furnace)) {
            return;
        }
        PersistentDataContainer data = furnace.getPersistentDataContainer();
        if (speed <= 0 && fuel <= 0 && twice <= 0) {
            data.remove(SPEED);
            data.remove(FUEL);
            data.remove(DOUBLE);
        } else {
            data.set(SPEED, PersistentDataType.DOUBLE, speed);
            data.set(FUEL, PersistentDataType.DOUBLE, fuel);
            data.set(DOUBLE, PersistentDataType.DOUBLE, twice);
        }
        furnace.update(false, false);
    }

    private static double read(Block block, NamespacedKey key) {
        if (!(block.getState(false) instanceof Furnace furnace)) {
            return 0;
        }
        Double value = furnace.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
        return value == null ? 0 : value;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onStart(FurnaceStartSmeltEvent event) {
        double speed = read(event.getBlock(), SPEED);
        if (speed > 0) {
            event.setTotalCookTime(rule.cookTime(event.getTotalCookTime(), speed));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(FurnaceBurnEvent event) {
        double longer = read(event.getBlock(), FUEL);
        if (longer > 0) {
            event.setBurnTime(rule.burnTime(event.getBurnTime(), longer));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent event) {
        double twice = read(event.getBlock(), DOUBLE);
        ItemStack result = event.getResult();
        if (twice <= 0 || result.getAmount() != 1 || !(event.getBlock().getState(false) instanceof Furnace furnace)) {
            return;
        }
        ItemStack out = furnace.getInventory().getResult();
        int already = out == null || out.isEmpty() ? 0 : out.getAmount();
        if (out != null && !out.isEmpty() && !out.isSimilar(result)) {
            return;
        }
        if (rule.mayDouble(key(result.getType()), result.getType().isEdible(), already, result.getMaxStackSize())
                && SkillValues.chance(twice, random)) {
            ItemStack doubled = result.clone();
            doubled.setAmount(2);
            event.setResult(doubled);
        }
    }

    // ------------------------------------------------------------------ auto-smelt

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        Player miner = event.getPlayer();
        if (miner.isSneaking() || !values.has(miner, ArtisanEffect.AUTO_SMELT)) {
            return;
        }
        Enchantment silk = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("silk_touch"));
        if (silk != null && miner.getInventory().getItemInMainHand().containsEnchantment(silk)) {
            return;
        }
        boolean heart = values.has(miner, ArtisanEffect.FORGE_HEART);
        int smeltedCount = 0;
        double experience = 0;
        for (Item drop : event.getItems()) {
            ItemStack stack = drop.getItemStack();
            var smelted = rule.smelted(key(stack.getType())).map(Material::matchMaterial);
            if (smelted.isEmpty() || smelted.get() == null) {
                continue;
            }
            int amount = stack.getAmount();
            if (heart) {
                experience += amount * rule.experience(key(smelted.get()));
                amount += rule.bonus(amount, FORGE_DOUBLE, random);
            }
            smeltedCount += amount;
            drop.setItemStack(new ItemStack(smelted.get(), Math.min(amount, smelted.get().getMaxStackSize())));
        }
        if (heart && smeltedCount > 0) {
            int orbs = rule.orbs(1, experience, random);
            if (orbs > 0) {
                event.getBlock().getWorld().spawn(event.getBlock().getLocation().toCenterLocation(), ExperienceOrb.class,
                        orb -> orb.setExperience(orbs));
            }
        }
    }
}
