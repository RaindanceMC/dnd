package de.raindancer.modules.skills.skill.building;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.skills.listener.ISkillsListener;
import de.raindancer.modules.skills.skill.SkillValues;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;

/**
 * The mason's effects: blocks refunded (and marked, so they drop nothing — see {@link FreeBlocks}), faster climbing,
 * Quick Undo and the Bottomless Hod; and the marks kept true when a piston moves a block, an explosion takes it, fire
 * burns it or an enderman would carry it off.
 */
public final class BuildingListener implements ISkillsListener {

    private record Placed(Block block, Material material, long at) {
    }

    private static final int REMEMBERED = 32;

    private final Plugin plugin;
    private final SkillValues values;
    private final BuildingRule rule;
    private final RandomGenerator random;
    private final LongSupplier clock;
    private final Map<UUID, Deque<Placed>> recent = new ConcurrentHashMap<>();

    public BuildingListener(Plugin plugin, SkillValues values, BuildingRule rule, RandomGenerator random,
                            LongSupplier clock) {
        this.plugin = plugin;
        this.values = values;
        this.rule = rule;
        this.random = random;
        this.clock = clock;
    }

    @Override
    public void forget(UUID player) {
        recent.remove(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Block block = event.getBlockPlaced();
        Material material = block.getType();
        if (values.has(player, BuildingEffect.QUICK_UNDO)) {
            Deque<Placed> mine = recent.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
            synchronized (mine) {
                mine.addFirst(new Placed(block, material, clock.getAsLong()));
                while (mine.size() > REMEMBERED) {
                    mine.removeLast();
                }
            }
        }
        ItemStack hand = event.getItemInHand();
        EquipmentSlot slot = event.getHand();
        boolean lastOne = hand.getAmount() <= 1;
        boolean refunded = material.isItem() && rule.mayRefund(material.getKey().getKey(), material.isSolid(),
                material.isOccluding(), material.hasGravity(), block.getState(false) instanceof TileState)
                && values.roll(player, BuildingEffect.BLOCK_REFUND, random);
        if (refunded) {
            FreeBlocks.mark(block);
        }
        boolean refill = lastOne && !refunded && values.has(player, BuildingEffect.BOTTOMLESS_HOD);
        if (!refunded && !refill) {
            return;
        }
        ItemStack like = hand.clone();
        like.setAmount(1);
        Scheduling.entity(plugin, player, () -> {
            PlayerInventory inventory = player.getInventory();
            if (refunded) {
                ItemStack held = inventory.getItem(slot);
                if (held.isEmpty()) {
                    inventory.setItem(slot, like);
                } else if (held.isSimilar(like) && held.getAmount() < held.getMaxStackSize()) {
                    held.setAmount(held.getAmount() + 1);
                } else {
                    inventory.addItem(like).values()
                            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                }
                return;
            }
            refill(inventory, slot, like);
        });
    }

    /** The next stack of the same block moved into the hand that ran out. */
    private static void refill(PlayerInventory inventory, EquipmentSlot slot, ItemStack like) {
        if (!inventory.getItem(slot).isEmpty()) {
            return;
        }
        ItemStack[] storage = inventory.getStorageContents();
        int handIndex = slot == EquipmentSlot.HAND ? inventory.getHeldItemSlot() : -1;
        for (int index = 0; index < storage.length; index++) {
            ItemStack candidate = storage[index];
            if (index != handIndex && candidate != null && candidate.isSimilar(like)) {
                inventory.setItem(index, null);
                inventory.setItem(slot, candidate);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ Quick Undo

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null) {
            return;
        }
        Deque<Placed> mine = recent.get(event.getPlayer().getUniqueId());
        if (mine == null || !values.has(event.getPlayer(), BuildingEffect.QUICK_UNDO)) {
            return;
        }
        Block block = event.getClickedBlock();
        long now = clock.getAsLong();
        boolean found = false;
        synchronized (mine) {
            for (Iterator<Placed> each = mine.iterator(); each.hasNext(); ) {
                Placed placed = each.next();
                if (!rule.undoable(placed.at(), now)) {
                    each.remove();
                } else if (placed.block().equals(block) && placed.material() == block.getType()) {
                    each.remove();
                    found = true;
                }
            }
        }
        if (found) {
            event.setCancelled(true);
            // Broken as the player breaking it would: protection asked, the drop theirs (or nothing, if it was free).
            event.getPlayer().breakBlock(block);
        }
    }

    // ------------------------------------------------------------------ climbing

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo().getY() <= event.getFrom().getY()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking() || player.isFlying() || !player.isClimbing()) {
            return;
        }
        double faster = values.value(player, BuildingEffect.CLIMB_SPEED);
        if (faster <= 0) {
            return;
        }
        Vector velocity = player.getVelocity();
        double up = rule.climb(faster);
        if (velocity.getY() < up) {
            player.setVelocity(velocity.setY(up));
        }
    }

    // ------------------------------------------------------------------ free blocks stay free

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (FreeBlocks.unmark(event.getBlock())) {
            event.setDropItems(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        clearFree(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) {
        clearFree(event.blockList());
    }

    /** Free blocks in a blast go without a drop. */
    private static void clearFree(List<Block> blast) {
        for (Iterator<Block> each = blast.iterator(); each.hasNext(); ) {
            Block block = each.next();
            if (FreeBlocks.unmark(block)) {
                each.remove();
                block.setType(Material.AIR, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        FreeBlocks.unmark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCarry(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Enderman && FreeBlocks.isMarked(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPush(BlockPistonExtendEvent event) {
        move(event.getBlocks(), facing(event.getBlock(), event.getDirection()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPull(BlockPistonRetractEvent event) {
        move(event.getBlocks(), facing(event.getBlock(), event.getDirection()).getOppositeFace());
    }

    private static BlockFace facing(Block piston, BlockFace fallback) {
        return piston.getBlockData() instanceof Directional directional ? directional.getFacing() : fallback;
    }

    /** The marks go where the blocks go: all lifted first, so a row of free blocks does not overwrite itself. */
    private static void move(List<Block> moved, BlockFace towards) {
        List<Location> to = new ArrayList<>();
        for (Block block : moved) {
            if (FreeBlocks.unmark(block)) {
                to.add(block.getRelative(towards).getLocation());
            }
        }
        to.forEach(location -> FreeBlocks.mark(location.getBlock()));
    }
}
