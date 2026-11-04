package de.raindancer.modules.skills.skill.taming;

import de.raindancer.modules.skills.rules.ISkillsRule;
import org.bukkit.Material;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** Taming's numbers: what tames whom, how a horse's temper rises, what a pet's blows and hurts become. */
public final class TamingRule implements ISkillsRule {

    public static final Duration LOYAL_EVERY = Duration.ofMinutes(5);
    /** How far a pet may be from its owner and still be mended, made hardier, counted. */
    public static final int NEAR = 24;
    public static final long PULSE_TICKS = 100;

    private static final Set<Material> PARROT_FOOD = Set.of(Material.WHEAT_SEEDS, Material.MELON_SEEDS,
            Material.PUMPKIN_SEEDS, Material.BEETROOT_SEEDS, Material.TORCHFLOWER_SEEDS, Material.PITCHER_POD);
    private static final Map<String, Set<Material>> FOOD = Map.of(
            "wolf", Set.of(Material.BONE),
            "cat", Set.of(Material.COD, Material.SALMON),
            "parrot", PARROT_FOOD);

    /** Whether {@code food} is what tames an animal of this type (by its key) when fed. */
    public boolean tamingFood(String type, Material food) {
        return FOOD.getOrDefault(type, Set.of()).contains(food);
    }

    /** A pet: what follows, sits and fights — wolves, cats, parrots. */
    public boolean pet(String type) {
        return FOOD.containsKey(type);
    }

    /** A horse's temper after a mount: a share of its most more, at most its most. */
    public int domestication(int current, int max, double percent) {
        if (percent <= 0) {
            return current;
        }
        return Math.min(max, current + (int) Math.ceil(max * percent / 100));
    }

    public double dealt(double damage, double percent) {
        return percent <= 0 ? damage : damage * (1 + percent / 100);
    }

    public double taken(double damage, double percent) {
        return percent <= 0 ? damage : damage * Math.max(0, 1 - percent / 100);
    }

    @Override
    public String describe() {
        return "Taming: what tames whom, a horse's temper, a pet's blows and hurts";
    }
}
