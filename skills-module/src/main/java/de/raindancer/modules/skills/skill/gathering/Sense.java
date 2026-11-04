package de.raindancer.modules.skills.skill.gathering;

import de.raindancer.core.platform.util.Scheduling;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.function.Predicate;

/** Finding the nearest block of a kind around a player, and showing it to that player alone. */
public final class Sense {

    private static final int SHOWS = 4;
    private static final long SHOW_EVERY = 10;

    private Sense() {
    }

    public static Optional<Block> nearest(Block centre, int radius, Predicate<Block> wanted) {
        Block best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance >= bestDistance || distance > radius * radius) {
                        continue;
                    }
                    Block block = centre.getRelative(dx, dy, dz);
                    if (wanted.test(block)) {
                        best = block;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Every block of a kind around, nearest first, no more than {@code most}. */
    public static java.util.List<Block> all(Block centre, int radius, Predicate<Block> wanted, int most) {
        java.util.List<Block> found = new java.util.ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                        Block block = centre.getRelative(dx, dy, dz);
                        if (wanted.test(block)) {
                            found.add(block);
                        }
                    }
                }
            }
        }
        found.sort(java.util.Comparator.comparingDouble(block -> block.getLocation().distanceSquared(
                centre.getLocation())));
        return found.size() > most ? found.subList(0, most) : found;
    }

    /** Sparks on the block for a couple of seconds, only the player sees them. */
    public static void show(Plugin plugin, Player player, Block block, Particle particle) {
        for (int show = 0; show < SHOWS; show++) {
            Scheduling.entityLater(plugin, player, 1 + show * SHOW_EVERY, () -> player.spawnParticle(particle,
                    block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5, 14, 0.35, 0.35, 0.35, 0));
        }
    }
}
