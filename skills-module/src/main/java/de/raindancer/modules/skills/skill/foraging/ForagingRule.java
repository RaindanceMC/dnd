package de.raindancer.modules.skills.skill.foraging;

import de.raindancer.modules.skills.rules.ISkillsRule;
import de.raindancer.modules.skills.skill.Chances;
import org.bukkit.Material;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Foraging's numbers: berries, rare finds by what was foraged, what Bloom grows. Whether a flower or a mushroom was
 * the world's own is Core's to say (Activities' FORAGE); what bone meal grew is kept apart ({@link Fertilised}).
 */
public final class ForagingRule implements ISkillsRule {

    /** Bramble Kin: how often a picked bush is ripe again at once. */
    public static final double REGROW = 0.25;

    private record Find(Material item, double share) {
    }

    private static final Set<Material> BERRIES = EnumSet.of(Material.SWEET_BERRY_BUSH, Material.CAVE_VINES,
            Material.CAVE_VINES_PLANT);
    private static final Set<String> MUSHROOMS = Set.of("red_mushroom", "brown_mushroom", "crimson_fungus",
            "warped_fungus");
    private static final Set<String> BERRY_BLOCKS = Set.of("sweet_berry_bush", "cave_vines", "cave_vines_plant");
    private static final Set<String> FLOWERS = Set.of("dandelion", "poppy", "blue_orchid", "allium", "azure_bluet",
            "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley",
            "sunflower", "lilac", "rose_bush", "peony", "pink_petals");

    private static final List<Find> FROM_FLOWERS = List.of(new Find(Material.TORCHFLOWER_SEEDS, 0.4),
            new Find(Material.PITCHER_POD, 0.3), new Find(Material.HONEY_BOTTLE, 0.3));
    private static final List<Find> FROM_MUSHROOMS = List.of(new Find(Material.GLOW_LICHEN, 0.4),
            new Find(Material.SPORE_BLOSSOM, 0.3), new Find(Material.SHROOMLIGHT, 0.3));
    private static final List<Find> FROM_BERRIES = List.of(new Find(Material.GLOW_BERRIES, 0.4),
            new Find(Material.APPLE, 0.4), new Find(Material.HONEY_BOTTLE, 0.2));

    public boolean berries(Material block) {
        return BERRIES.contains(block);
    }

    /** More berries for {@code picked}: a share of them, the rest of a berry by chance. */
    public int moreBerries(int picked, double percent, double roll) {
        if (picked <= 0 || percent <= 0) {
            return 0;
        }
        return Chances.times(picked * percent, roll);
    }

    /** A rare find fitting what was foraged — by its block's key — or none for what has no table. */
    public Optional<Material> rareFind(String what, double roll) {
        List<Find> table = FLOWERS.contains(what) ? FROM_FLOWERS : MUSHROOMS.contains(what) ? FROM_MUSHROOMS
                : BERRY_BLOCKS.contains(what) ? FROM_BERRIES : List.of();
        double seen = 0;
        for (Find find : table) {
            seen += find.share();
            if (roll < seen) {
                return Optional.of(find.item());
            }
        }
        return table.isEmpty() ? Optional.empty() : Optional.of(table.getLast().item());
    }

    /**
     * What Bloom grows all the way: saplings, berry bushes and stems. Never a crop — a crop grown at once, doubled by
     * Farming and composted, with bone meal saved, would give back more bone meal than it took — nor grass, which
     * makes flowers.
     */
    public boolean blooms(Material block) {
        String name = block.name();
        return name.endsWith("_SAPLING") || name.endsWith("_PROPAGULE") || block == Material.SWEET_BERRY_BUSH
                || block == Material.MELON_STEM || block == Material.PUMPKIN_STEM;
    }

    public boolean prickly(Material block) {
        return block == Material.SWEET_BERRY_BUSH;
    }

    @Override
    public String describe() {
        return "Foraging: more berries, rare finds by what was foraged, what Bloom grows";
    }
}
