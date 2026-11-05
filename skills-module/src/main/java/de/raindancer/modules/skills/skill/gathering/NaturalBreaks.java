package de.raindancer.modules.skills.skill.gathering;

import de.raindancer.core.world.blocks.PlacedBlocks;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a broken block was the world's own — the farm guard every gathering skill asks before a bonus drop. Never
 * a block a player placed (Core's {@link PlacedBlocks}, read before it takes the mark off at MONITOR), never sand or
 * gravel that fell and landed ({@link FallenSpots}), never leaves a player placed (persistent ones).
 */
public final class NaturalBreaks implements Listener {

    private final Map<BlockBreakEvent, Boolean> natural = Collections.synchronizedMap(new WeakHashMap<>());
    /** Each player's last break, so the drop event that follows it knows what it was. */
    private final Map<UUID, String> lastNatural = new ConcurrentHashMap<>();
    private final FallenSpots fallen = new FallenSpots(8_192);
    /** The world tick of each player's last break: the wear their tool takes in that tick is the break's. */
    private final Map<UUID, Long> brokeAt = new ConcurrentHashMap<>();

    private static String key(Block block) {
        return block.getWorld().getName() + ':' + block.getX() + ':' + block.getY() + ':' + block.getZ();
    }

    /** Whether the block this break takes was the world's own. Ask it before MONITOR. */
    public boolean natural(BlockBreakEvent event) {
        return natural.computeIfAbsent(event, broken -> worldMade(broken.getBlock()));
    }

    private boolean worldMade(Block block) {
        if (PlacedBlocks.isPlaced(block)
                || fallen.contains(block.getWorld().getName(), block.getX(), block.getY(), block.getZ())) {
            return false;
        }
        return !(block.getBlockData() instanceof Leaves leaves) || !leaves.isPersistent();
    }

    /** Whether the drops of this broken block — a BlockDropItemEvent's state — come from the world's own block. */
    public boolean natural(Player player, BlockState broken) {
        String last = lastNatural.get(player.getUniqueId());
        return last != null && last.equals(key(broken.getBlock()));
    }

    /** Whether the player broke a block this very tick — so the wear their tool takes now is from a block. */
    public boolean justBroke(Player player) {
        Long at = brokeAt.get(player.getUniqueId());
        return at != null && at == player.getWorld().getGameTime();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void beforeBreak(BlockBreakEvent event) {
        natural(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        brokeAt.put(event.getPlayer().getUniqueId(), block.getWorld().getGameTime());
        if (natural(event)) {
            lastNatural.put(event.getPlayer().getUniqueId(), key(block));
        } else {
            lastNatural.remove(event.getPlayer().getUniqueId());
        }
        fallen.gone(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFall(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock)) {
            return;
        }
        Block block = event.getBlock();
        if (event.getTo() == Material.AIR) {
            fallen.gone(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        } else {
            fallen.landed(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastNatural.remove(event.getPlayer().getUniqueId());
        brokeAt.remove(event.getPlayer().getUniqueId());
    }
}
