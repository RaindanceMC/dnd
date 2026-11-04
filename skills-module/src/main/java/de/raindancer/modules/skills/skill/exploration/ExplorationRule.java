package de.raindancer.modules.skills.skill.exploration;

import de.raindancer.modules.skills.rules.ISkillsRule;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/** The explorer's numbers: how tired, which grid a map lies on, what a road is, what a compass looks for. */
public final class ExplorationRule implements ISkillsRule {

    /** Roadrunner's extra speed on a road. */
    public static final double ROAD_SPEED = 0.30;
    public static final int MOST_ZOOM = 4;

    private static final Set<String> ROADS = Set.of("dirt_path", "gravel", "coarse_dirt", "cobblestone",
            "mossy_cobblestone", "stone_bricks", "mossy_stone_bricks", "bricks", "mud_bricks", "packed_mud",
            "polished_andesite", "polished_granite", "polished_diorite", "deepslate_bricks", "deepslate_tiles",
            "cobbled_deepslate", "polished_blackstone_bricks", "nether_bricks", "smooth_stone", "tuff_bricks");

    private static final String[] POINTS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    public float exhaustion(float exhaustion, double less) {
        if (less <= 0) {
            return exhaustion;
        }
        return (float) Math.max(0, exhaustion * (100 - less) / 100);
    }

    /** Where a map of this zoom centres for a player standing at {@code at}: on the game's own grid. */
    public int centre(int at, int zoom) {
        int size = 128 << zoom;
        return Math.floorDiv(at + 64, size) * size + size / 2 - 64;
    }

    public int zoom(double wider) {
        return (int) Math.max(0, Math.min(MOST_ZOOM, Math.floor(wider)));
    }

    public boolean road(String block) {
        if (ROADS.contains(block)) {
            return true;
        }
        for (String part : List.of("_slab", "_stairs")) {
            if (block.endsWith(part)) {
                String base = block.substring(0, block.length() - part.length());
                if (ROADS.contains(base) || ROADS.contains(base + "s")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** What Treasure Sense looks for, by the kind of world. */
    public List<String> structures(World.Environment environment) {
        return switch (environment) {
            case NETHER -> List.of("fortress", "bastion_remnant", "ruined_portal_nether");
            case THE_END -> List.of("end_city");
            default -> List.of("village_plains", "village_desert", "village_savanna", "village_snowy",
                    "village_taiga", "shipwreck", "shipwreck_beached", "ruined_portal", "desert_pyramid",
                    "jungle_pyramid", "igloo", "ocean_ruin_cold", "ocean_ruin_warm", "trail_ruins",
                    "pillager_outpost", "swamp_hut");
        };
    }

    /** Whether a loot table fills a chest — not a mob's drop, not a fishing catch. */
    public boolean chestLoot(String table) {
        return table != null && table.substring(table.indexOf(':') + 1).startsWith("chests/");
    }

    /**
     * Which of a chest's stacks gets one more, by their largest stack sizes; -1 when none can take one — a sword or
     * a book is never doubled.
     */
    public int extraPick(List<Integer> maxStackSizes, RandomGenerator random) {
        List<Integer> stackable = new ArrayList<>();
        for (int index = 0; index < maxStackSizes.size(); index++) {
            if (maxStackSizes.get(index) > 1) {
                stackable.add(index);
            }
        }
        return stackable.isEmpty() ? -1 : stackable.get(random.nextInt(stackable.size()));
    }

    /** A yaw as one of eight compass points. */
    public String point(float yaw) {
        double turned = ((yaw % 360) + 360) % 360;
        return POINTS[(int) Math.round(turned / 45) % 8];
    }

    @Override
    public String describe() {
        return "how an explorer tires, where their maps lie and what their compass looks for";
    }
}
