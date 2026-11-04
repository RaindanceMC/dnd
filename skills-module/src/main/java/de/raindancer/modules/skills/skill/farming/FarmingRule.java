package de.raindancer.modules.skills.skill.farming;

import de.raindancer.modules.skills.rules.ISkillsRule;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Farming's numbers: what of a grown crop comes twice and what it is planted from, which of an animal's drops count,
 * how often a crop around a farmer grows a stage. Whether a crop was the world's own and grown is Core's to say
 * (Activities' HARVEST), never this rule's.
 */
public final class FarmingRule implements ISkillsRule {

    /**
     * A stage's chance per crop per pulse at 100%: the game grows a planted crop a stage about every 200 seconds on
     * wet farmland, so 0.025 every five seconds doubles that.
     */
    public static final double BASE_STAGE_CHANCE = 0.025;
    public static final long PULSE_TICKS = 100;
    public static final int GROWTH_RADIUS = 6;
    public static final int GROWTH_HEIGHT = 2;
    /** Reaper reaches one block around the crop broken. */
    public static final int REAP_RADIUS = 1;

    private static final Map<Material, Material> PRODUCE = new EnumMap<>(Map.of(
            Material.WHEAT, Material.WHEAT, Material.CARROTS, Material.CARROT, Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT, Material.NETHER_WART, Material.NETHER_WART,
            Material.COCOA, Material.COCOA_BEANS, Material.MELON, Material.MELON_SLICE, Material.PUMPKIN, Material.PUMPKIN,
            Material.TORCHFLOWER_CROP, Material.TORCHFLOWER, Material.PITCHER_CROP, Material.PITCHER_PLANT));

    private static final Map<Material, Material> SEEDS = new EnumMap<>(Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS, Material.CARROTS, Material.CARROT, Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS, Material.NETHER_WART, Material.NETHER_WART,
            Material.COCOA, Material.COCOA_BEANS));

    private static final Set<Material> ANIMAL = EnumSet.of(Material.BEEF, Material.COOKED_BEEF, Material.PORKCHOP,
            Material.COOKED_PORKCHOP, Material.CHICKEN, Material.COOKED_CHICKEN, Material.MUTTON,
            Material.COOKED_MUTTON, Material.RABBIT, Material.COOKED_RABBIT, Material.RABBIT_HIDE, Material.LEATHER,
            Material.FEATHER);

    /** The crop item a grown crop gives twice: never its seeds, which would only be more seeds. */
    public Optional<Material> produce(Material crop) {
        return Optional.ofNullable(PRODUCE.get(crop));
    }

    /** What Green Thumb plants the crop again from, taken out of its own drops. */
    public Optional<Material> seed(Material crop) {
        return Optional.ofNullable(SEEDS.get(crop));
    }

    /** A crop with an age, broken grown, that Reaper may reap beside another. */
    public boolean reapable(Material crop) {
        return SEEDS.containsKey(crop);
    }

    /** Meat, leather, feathers and wool — never a saddle, armour or a chest's contents the animal carried. */
    public boolean animalProduce(Material item) {
        return ANIMAL.contains(item) || item.name().endsWith("_WOOL");
    }

    public boolean hoe(Material item) {
        return item.name().endsWith("_HOE");
    }

    public double stageChance(double growthPercent) {
        return growthPercent <= 0 ? 0 : BASE_STAGE_CHANCE * growthPercent / 100;
    }

    /** A baby's age (negative, counting up to 0) after the feeding, the rest of its wait cut short. */
    public int weaned(int age, double percent) {
        if (age >= 0 || percent <= 0) {
            return age;
        }
        return (int) Math.round(age * (1 - Math.min(100, percent) / 100));
    }

    @Override
    public String describe() {
        return "Farming: a grown crop's produce, its seeds, an animal's drops, growth around a farmer";
    }
}
