package de.raindancer.modules.skills.skill;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who last worked a furnace, a smoker or a brewing stand: the block itself has no player, so its cooking or brewing
 * is the last worker's — as Core's Activities counts a brew. Forgotten when the worker leaves.
 */
public final class Workers {

    private final Map<String, UUID> last = new ConcurrentHashMap<>();

    private static String spot(Location at) {
        return at.getWorld().getName() + ':' + at.getBlockX() + ':' + at.getBlockY() + ':' + at.getBlockZ();
    }

    public void worked(Location at, UUID player) {
        if (at != null && at.getWorld() != null) {
            last.put(spot(at), player);
        }
    }

    /** The online player who last worked this block, or {@code null}. */
    public Player of(Server server, Block block) {
        UUID who = last.get(spot(block.getLocation()));
        return who == null ? null : server.getPlayer(who);
    }

    public void forget(UUID player) {
        last.values().removeIf(player::equals);
    }
}
