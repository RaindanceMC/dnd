package de.raindancer.modules.skills.skill.excavation;

import de.raindancer.core.world.activity.ActivityKind;
import de.raindancer.core.world.activity.Terrain;
import de.raindancer.modules.skills.rules.ISkillsRule;
import de.raindancer.modules.skills.skill.gathering.GatherRule;
import de.raindancer.modules.skills.skill.gathering.Loot;
import de.raindancer.modules.skills.skill.gathering.Spot;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The Excavation skill's sums: what soft ground is, what a treasure is, the three by three, a brushed find. */
public final class ExcavationRule implements ISkillsRule {

    /** What dirt and sand hide: little things, often. */
    public static final List<Loot> COMMON = List.of(new Loot(Material.GOLD_NUGGET, 3),
            new Loot(Material.IRON_NUGGET, 4), new Loot(Material.BONE, 2), new Loot(Material.STRING, 2),
            new Loot(Material.COAL, 2), new Loot(Material.FLINT, 2), new Loot(Material.CLAY_BALL, 3),
            new Loot(Material.GLOWSTONE_DUST, 2), new Loot(Material.ARROW, 3), new Loot(Material.BOWL, 1),
            new Loot(Material.COPPER_INGOT, 1), new Loot(Material.RAW_GOLD, 1));
    /** With the treasure hunter's mastery, one treasure in ten is one of these. */
    public static final List<Loot> RARE = List.of(new Loot(Material.DIAMOND, 1), new Loot(Material.EMERALD, 2),
            new Loot(Material.GOLDEN_APPLE, 1), new Loot(Material.NAME_TAG, 1), new Loot(Material.MUSIC_DISC_13, 1),
            new Loot(Material.MUSIC_DISC_CAT, 1), new Loot(Material.GOLD_INGOT, 2));
    private static final double RARE_SHARE = 0.1;
    /** How long after a brush is put to a block its find may still pop out. */
    public static final long BRUSH_MILLIS = 10_000;
    private static final double FIND_REACH = 1.6;

    private final GatherRule gather = new GatherRule();

    public boolean soft(Material block) {
        return Terrain.kindOf(block).filter(kind -> kind == ActivityKind.EXCAVATE).isPresent();
    }

    public boolean suspicious(Material block) {
        return block == Material.SUSPICIOUS_SAND || block == Material.SUSPICIOUS_GRAVEL;
    }

    /** A treasure, if the chance is met: {@code pick} in [0, 1) chooses which. */
    public Optional<Loot> treasure(double percent, boolean rare, double roll, double pick) {
        if (!gather.chance(percent, roll)) {
            return Optional.empty();
        }
        if (rare && pick >= 1 - RARE_SHARE) {
            int index = (int) ((pick - (1 - RARE_SHARE)) / RARE_SHARE * RARE.size());
            return Optional.of(RARE.get(Math.min(RARE.size() - 1, index)));
        }
        double share = rare ? 1 - RARE_SHARE : 1;
        int index = (int) (pick / share * COMMON.size());
        return Optional.of(COMMON.get(Math.max(0, Math.min(COMMON.size() - 1, index))));
    }

    /**
     * The eight blocks around a dug one, across the way the player looks: flat when looking up or down, else
     * upright, facing the way they face (yaw 0 is south, along z).
     */
    public List<Spot> plane(Spot at, float pitch, float yaw) {
        List<Spot> spots = new ArrayList<>(8);
        boolean flat = Math.abs(pitch) > 45;
        double facing = ((yaw % 360) + 360) % 360;
        boolean alongZ = facing < 45 || facing >= 315 || (facing >= 135 && facing < 225);
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                spots.add(flat ? at.plus(a, 0, b) : alongZ ? at.plus(a, b, 0) : at.plus(0, b, a));
            }
        }
        return spots;
    }

    /**
     * Whether an item that appeared {@code dx, dy, dz} from a block's corner is that block's find: the block was
     * being brushed lately ({@code brushedAt}, 0 for never) and the item is at it.
     */
    public boolean brushFind(long brushedAt, long now, double dx, double dy, double dz) {
        if (brushedAt <= 0 || now - brushedAt > BRUSH_MILLIS || now < brushedAt) {
            return false;
        }
        double x = dx - 0.5;
        double y = dy - 0.5;
        double z = dz - 0.5;
        return x * x + y * y + z * z <= FIND_REACH * FIND_REACH;
    }

    @Override
    public String describe() {
        return "what soft ground is, what a treasure is, digging three by three and what a brushed find is";
    }
}
