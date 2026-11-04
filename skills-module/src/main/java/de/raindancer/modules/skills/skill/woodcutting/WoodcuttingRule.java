package de.raindancer.modules.skills.skill.woodcutting;

import de.raindancer.core.world.activity.ActivityKind;
import de.raindancer.core.world.activity.Terrain;
import de.raindancer.modules.skills.rules.ISkillsRule;
import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The Woodcutting skill's sums: logs and leaves, each tree's sapling, where it grows, how far a tree reaches. */
public final class WoodcuttingRule implements ISkillsRule {

    /** A felled tree is followed no further than this from the cut — a giant spruce or jungle tree included. */
    public static final int TIMBER_REACH = 24;
    /** Leaves this close to a felled log are its canopy. */
    public static final int CANOPY_REACH = 4;
    public static final int MOST_LEAVES = 200;

    private static final Map<String, Material> ODD_SAPLINGS = Map.of(
            "MANGROVE", Material.MANGROVE_PROPAGULE, "AZALEA", Material.AZALEA,
            "FLOWERING_AZALEA", Material.FLOWERING_AZALEA, "CRIMSON", Material.CRIMSON_FUNGUS,
            "WARPED", Material.WARPED_FUNGUS);
    private static final Set<Material> SOIL = EnumSet.of(Material.DIRT, Material.GRASS_BLOCK, Material.PODZOL,
            Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.MYCELIUM, Material.MOSS_BLOCK, Material.MUD,
            Material.MUDDY_MANGROVE_ROOTS, Material.PALE_MOSS_BLOCK, Material.FARMLAND);

    public boolean log(Material block) {
        return Terrain.kindOf(block).filter(kind -> kind == ActivityKind.CHOP).isPresent();
    }

    public boolean leaves(Material block) {
        return block != null && block.name().endsWith("_LEAVES");
    }

    /** Its tree's sapling, from its leaves or its log. */
    public Optional<Material> saplingOf(Material block) {
        if (block == null) {
            return Optional.empty();
        }
        String name = block.name();
        String wood;
        if (name.endsWith("_LEAVES")) {
            wood = name.substring(0, name.length() - "_LEAVES".length());
        } else if (log(block)) {
            wood = name.substring(0, name.lastIndexOf('_'));
        } else {
            return Optional.empty();
        }
        Material odd = ODD_SAPLINGS.get(wood);
        if (odd != null) {
            return Optional.of(odd);
        }
        return Optional.ofNullable(Material.getMaterial(wood + "_SAPLING"));
    }

    public boolean dropsApples(Material leaves) {
        return leaves == Material.OAK_LEAVES || leaves == Material.DARK_OAK_LEAVES;
    }

    /** Whether a sapling — or a fungus — grows on this ground. */
    public boolean plantable(Material sapling, Material ground) {
        if (sapling == Material.CRIMSON_FUNGUS) {
            return ground == Material.CRIMSON_NYLIUM;
        }
        if (sapling == Material.WARPED_FUNGUS) {
            return ground == Material.WARPED_NYLIUM;
        }
        return SOIL.contains(ground);
    }

    public boolean sameTree(Material cut, Material other) {
        return cut == other && log(cut);
    }

    /** A tree is felled on only while the axe has more than a point or two left. */
    public boolean mayFellOn(int maxDurability, int damage) {
        return maxDurability <= 0 || maxDurability - damage > 2;
    }

    @Override
    public String describe() {
        return "logs and leaves, each tree's sapling and where it grows, and how far a felled tree reaches";
    }
}
