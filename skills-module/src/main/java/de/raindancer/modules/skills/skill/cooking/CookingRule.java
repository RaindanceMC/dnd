package de.raindancer.modules.skills.skill.cooking;

import de.raindancer.modules.skills.rules.ISkillsRule;
import de.raindancer.modules.skills.skill.Chances;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Cooking's numbers: how much more a meal fills, what a cooked meal blesses with, how fast a furnace cooks. */
public final class CookingRule implements ISkillsRule {

    /** Master Chef: a second. */
    public static final int MASTER_CHEF_TICKS = 20;
    private static final int QUICKEST = 20;
    public static final int FEAST_RADIUS = 8;

    /**
     * A cooked meal's blessing: the game's effect by key, at level {@code amplifier} + 1, for {@code share} of the
     * tree's seconds.
     */
    public record Blessing(String effect, int amplifier, double share) {

        public int ticks(double seconds) {
            return (int) Math.round(seconds * share * 20);
        }
    }

    private static final Blessing STURDY = new Blessing("resistance", 0, 1);
    private static final Blessing SEAFARER = new Blessing("water_breathing", 0, 1);
    private static final Blessing BAKER = new Blessing("haste", 0, 1);
    private static final Blessing BROTH = new Blessing("regeneration", 0, 1 / 3.0);
    private static final Blessing SWEET = new Blessing("speed", 0, 1);

    private static final Map<Material, Blessing> BLESSINGS = new EnumMap<>(Material.class);

    static {
        for (Material meat : Set.of(Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.COOKED_MUTTON,
                Material.COOKED_CHICKEN, Material.COOKED_RABBIT)) {
            BLESSINGS.put(meat, STURDY);
        }
        for (Material fish : Set.of(Material.COOKED_COD, Material.COOKED_SALMON, Material.DRIED_KELP)) {
            BLESSINGS.put(fish, SEAFARER);
        }
        BLESSINGS.put(Material.BREAD, BAKER);
        BLESSINGS.put(Material.BAKED_POTATO, BAKER);
        for (Material stew : Set.of(Material.MUSHROOM_STEW, Material.RABBIT_STEW, Material.BEETROOT_SOUP)) {
            BLESSINGS.put(stew, BROTH);
        }
        BLESSINGS.put(Material.PUMPKIN_PIE, SWEET);
        BLESSINGS.put(Material.COOKIE, SWEET);
    }

    private static final Set<String> QUEASY = Set.of("hunger", "poison", "nausea");

    public int moreFood(int nutrition, double percent, double roll) {
        return nutrition <= 0 ? 0 : Chances.times(nutrition * percent, roll);
    }

    public float moreSaturation(float saturation, double percent) {
        return percent <= 0 ? 0 : (float) (saturation * percent / 100);
    }

    /** What a cooked meal blesses with; raw food and golden food — which has its own — none. */
    public Optional<Blessing> blessing(Material food) {
        return Optional.ofNullable(BLESSINGS.get(food));
    }

    public int cookTime(int ticks, double percent, boolean masterChef) {
        if (masterChef) {
            return Math.min(ticks, MASTER_CHEF_TICKS);
        }
        if (percent <= 0) {
            return ticks;
        }
        return Math.min(ticks, Math.max(QUICKEST, (int) Math.round(ticks * (1 - percent / 100))));
    }

    public int burnTime(int ticks, double percent) {
        return percent <= 0 ? ticks : (int) Math.round(ticks * (1 + percent / 100));
    }

    /** What Iron Stomach shrugs off from food, by the effect's key. */
    public boolean queasy(String effect) {
        return QUEASY.contains(effect);
    }

    /** What a Feast gives each player around: half of the meal's hunger. */
    public int shared(int nutrition) {
        return Math.max(0, nutrition / 2);
    }

    @Override
    public String describe() {
        return "Cooking: how much more a meal fills, a cooked meal's blessing, how fast a furnace cooks";
    }
}
