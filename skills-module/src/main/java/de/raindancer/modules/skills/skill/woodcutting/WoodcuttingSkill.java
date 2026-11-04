package de.raindancer.modules.skills.skill.woodcutting;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.world.blocks.PlacedBlocks;
import de.raindancer.modules.skills.SkillsServices;
import de.raindancer.modules.skills.listener.ISkillsListener;
import de.raindancer.modules.skills.skill.gathering.ConnectedRule;
import de.raindancer.modules.skills.skill.gathering.GatherRule;
import de.raindancer.modules.skills.skill.gathering.Hands;
import de.raindancer.modules.skills.skill.gathering.NaturalBreaks;
import de.raindancer.modules.skills.skill.gathering.SkillValues;
import de.raindancer.modules.skills.skill.gathering.Spot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static de.raindancer.modules.skills.skill.gathering.SkillValues.get;
import static de.raindancer.modules.skills.skill.gathering.SkillValues.has;

/**
 * The Woodcutting tree at work: a faster axe that wears slower, logs twice or three times over, saplings, apples and
 * sticks from leaves, Timber, Replanter and Clear Canopy. Only logs a tree grew and leaves it wore count — never a
 * log a player placed, never leaves a player placed — so no farm of placed logs pays.
 */
public final class WoodcuttingSkill implements ISkillsListener {

    static final NamespacedKey SPEED = Hands.key("woodcutting_speed");

    private final SkillsServices services;
    private final SkillValues values;
    private final NaturalBreaks natural;
    private final WoodcuttingRule rule = new WoodcuttingRule();
    private final GatherRule gather = new GatherRule();
    private final ConnectedRule connected = new ConnectedRule();
    /** Players whose tree is being felled right now; {@link #clearing} while its leaves come down. */
    private final Set<UUID> felling = ConcurrentHashMap.newKeySet();
    private final Set<UUID> clearing = ConcurrentHashMap.newKeySet();

    public WoodcuttingSkill(SkillsServices services, SkillValues values, NaturalBreaks natural) {
        this.services = services;
        this.values = values;
        this.natural = natural;
    }

    private static double roll() {
        return ThreadLocalRandom.current().nextDouble();
    }

    private boolean axe(ItemStack stack) {
        return stack != null && gather.tool(stack.getType()) == GatherRule.Tool.AXE;
    }

    public void tick(Player player, Map<String, Double> tree) {
        Hands.speed(player, SPEED, axe(player.getInventory().getItemInMainHand())
                ? get(tree, WoodcuttingEffect.CHOP_SPEED) : 0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!rule.log(block.getType()) || !natural.natural(event) || felling.contains(player.getUniqueId())) {
            return;
        }
        Map<String, Double> tree = values.of(player);
        if (tree.isEmpty()) {
            return;
        }
        if (has(tree, WoodcuttingEffect.CHOP_REPLANT)) {
            replant(player, block);
        }
        int more = (int) get(tree, WoodcuttingEffect.CHOP_TIMBER);
        if (more > 0 && player.isSneaking() && axe(player.getInventory().getItemInMainHand())) {
            fell(player, block, more, has(tree, WoodcuttingEffect.CHOP_CLEAR_CANOPY));
        }
    }

    /** Replanter: the lowest log of a tree, chopped, leaves a sapling from the bag in its place — the tick after. */
    private void replant(Player player, Block log) {
        Optional<Material> sapling = rule.saplingOf(log.getType());
        Block ground = log.getRelative(BlockFace.DOWN);
        if (sapling.isEmpty() || !rule.plantable(sapling.get(), ground.getType())
                || !player.getInventory().contains(sapling.get())) {
            return;
        }
        Material kind = sapling.get();
        Scheduling.regionLater(services.plugin(), log.getLocation(), 1, () -> {
            if (!log.getType().isAir() || !rule.plantable(kind, ground.getType()) || !player.isOnline()) {
                return;
            }
            Scheduling.entity(services.plugin(), player, () -> {
                if (!player.getInventory().removeItem(new ItemStack(kind)).isEmpty()) {
                    return;
                }
                Scheduling.region(services.plugin(), log.getLocation(), () -> {
                    if (log.getType().isAir()) {
                        log.setType(kind);
                    } else {
                        player.getInventory().addItem(new ItemStack(kind));
                    }
                });
            });
        });
    }

    /** Timber: the rest of the tree, the tick after, each log a real break; then its leaves for Clear Canopy. */
    private void fell(Player player, Block cut, int more, boolean canopy) {
        Material type = cut.getType();
        World world = cut.getWorld();
        Spot start = new Spot(cut.getX(), cut.getY(), cut.getZ());
        List<Spot> logs = connected.connected(start, spot -> {
            Block other = world.getBlockAt(spot.x(), spot.y(), spot.z());
            return rule.sameTree(type, other.getType()) && !PlacedBlocks.isPlaced(other);
        }, more, WoodcuttingRule.TIMBER_REACH, true);
        if (logs.isEmpty()) {
            return;
        }
        Scheduling.entityLater(services.plugin(), player, 1, () -> {
            List<Spot> felled = new ArrayList<>();
            felled.add(start);
            felling.add(player.getUniqueId());
            try {
                for (Spot spot : logs) {
                    ItemStack tool = player.getInventory().getItemInMainHand();
                    int[] wear = Hands.wear(tool);
                    if (!axe(tool) || !rule.mayFellOn(wear[1], wear[0])) {
                        break;
                    }
                    Block block = world.getBlockAt(spot.x(), spot.y(), spot.z());
                    if (rule.sameTree(type, block.getType()) && player.breakBlock(block)) {
                        felled.add(spot);
                    }
                }
                if (canopy) {
                    clearCanopy(player, world, felled);
                }
            } finally {
                felling.remove(player.getUniqueId());
            }
        });
    }

    private void clearCanopy(Player player, World world, List<Spot> felled) {
        Set<Block> leaves = new LinkedHashSet<>();
        int reach = WoodcuttingRule.CANOPY_REACH;
        for (Spot log : felled) {
            for (int dx = -reach; dx <= reach && leaves.size() < WoodcuttingRule.MOST_LEAVES; dx++) {
                for (int dy = -1; dy <= reach && leaves.size() < WoodcuttingRule.MOST_LEAVES; dy++) {
                    for (int dz = -reach; dz <= reach && leaves.size() < WoodcuttingRule.MOST_LEAVES; dz++) {
                        Block block = world.getBlockAt(log.x() + dx, log.y() + dy, log.z() + dz);
                        if (rule.leaves(block.getType()) && block.getBlockData() instanceof Leaves data
                                && !data.isPersistent()) {
                            leaves.add(block);
                        }
                    }
                }
            }
        }
        clearing.add(player.getUniqueId());
        try {
            leaves.forEach(player::breakBlock);
        } finally {
            clearing.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        BlockState broken = event.getBlockState();
        Material type = broken.getType();
        boolean log = rule.log(type);
        if ((!log && !rule.leaves(type)) || !natural.natural(player, broken)) {
            return;
        }
        Map<String, Double> tree = values.of(player);
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tree.isEmpty() || !gather.multiplies(Hands.silk(tool))) {
            return;
        }
        if (log) {
            int times = gather.times(get(tree, WoodcuttingEffect.CHOP_LOG_DOUBLE),
                    get(tree, WoodcuttingEffect.CHOP_LOG_TRIPLE), roll(), roll());
            if (times > 1) {
                for (Item item : event.getItems()) {
                    ItemStack stack = item.getItemStack();
                    if (stack.getType() == type) {
                        stack.setAmount(Math.min(stack.getMaxStackSize(), stack.getAmount() * times));
                        item.setItemStack(stack);
                    }
                }
            }
            return;
        }
        if (tool.getType() == Material.SHEARS) {
            return;
        }
        Location centre = broken.getLocation().add(0.5, 0.5, 0.5);
        Optional<Material> sapling = rule.saplingOf(type);
        if (sapling.isPresent() && gather.chance(get(tree, WoodcuttingEffect.CHOP_SAPLING), roll())) {
            centre.getWorld().dropItemNaturally(centre, new ItemStack(sapling.get()));
        }
        if (rule.dropsApples(type) && gather.chance(get(tree, WoodcuttingEffect.CHOP_APPLE), roll())) {
            centre.getWorld().dropItemNaturally(centre, new ItemStack(Material.APPLE));
        }
        if (gather.chance(get(tree, WoodcuttingEffect.CHOP_STICKS), roll())) {
            centre.getWorld().dropItemNaturally(centre, new ItemStack(Material.STICK, 1 + (roll() < 0.5 ? 1 : 0)));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        if (!axe(event.getItem()) || !natural.justBroke(player)) {
            return;
        }
        if (clearing.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        Map<String, Double> tree = values.of(player);
        boolean saved = gather.chance(get(tree, WoodcuttingEffect.CHOP_WEAR), roll())
                || (felling.contains(player.getUniqueId())
                && gather.chance(get(tree, WoodcuttingEffect.CHOP_TIMBER_WEAR), roll()));
        if (saved) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @Override
    public void forget(UUID player) {
        felling.remove(player);
        clearing.remove(player);
    }
}
