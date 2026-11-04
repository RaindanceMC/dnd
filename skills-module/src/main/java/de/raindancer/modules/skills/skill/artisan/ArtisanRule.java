package de.raindancer.modules.skills.skill.artisan;

import de.raindancer.modules.skills.rules.ISkillsRule;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * The Artisan's numbers and its dupe guards: which crafts may give an extra or a refund, how much tougher a crafted
 * tool is, how much faster a furnace smelts, which smelts may come out double and what auto-smelt makes of an ore.
 * Item ids are vanilla's keys without the namespace ({@code iron_ingot}).
 */
public final class ArtisanRule implements ISkillsRule {

    /** Extras and refunds a player may get in a minute, however fast they click. */
    public static final int MOST_A_MINUTE = 64;

    /**
     * Results that are only something else recoloured, or put back: dyed one way and bleached the other, a bonus on
     * each turn would make wool, beds and the rest out of nothing.
     */
    private static final List<String> RECOLOURED = List.of("_wool", "_carpet", "_bed", "_terracotta",
            "_concrete_powder", "_stained_glass", "_stained_glass_pane", "_candle", "_banner", "_shulker_box", "_dye",
            "_bundle", "_harness");

    /** Ingredients that leave something behind in the grid — a bucket, a bottle — are never refunded. */
    private static final List<String> LEAVE_SOMETHING = List.of("_bucket", "honey_bottle", "potion", "dragon_breath");

    /** A wet sponge dries into a sponge, and water wets it again: a double would be sponges for ever. */
    private static final Set<String> NEVER_DOUBLED = Set.of("sponge");

    private static final Map<String, String> SMELTED = Map.of("raw_iron", "iron_ingot", "raw_gold", "gold_ingot",
            "raw_copper", "copper_ingot", "ancient_debris", "netherite_scrap");

    /** What a furnace gives in experience for one of these. */
    private static final Map<String, Double> EXPERIENCE = Map.of("iron_ingot", 0.7, "gold_ingot", 1.0,
            "copper_ingot", 0.7, "netherite_scrap", 2.0);

    /**
     * Whether a craft may give an extra or a refund: two kinds of ingredient at least (as Core's Crafts counts them),
     * not a tool (that is made tougher instead), not recoloured, and not a copy of one of its own ingredients.
     */
    public boolean mayBonus(String result, List<String> ingredients, boolean damageable) {
        Set<String> kinds = new HashSet<>(ingredients);
        if (kinds.size() < 2 || damageable || kinds.contains(result)) {
            return false;
        }
        return RECOLOURED.stream().noneMatch(result::endsWith);
    }

    /** One of the ingredients to give back, picked by {@code index}; none when every one leaves something behind. */
    public Optional<String> refund(List<String> ingredients, int index) {
        List<String> plain = ingredients.stream()
                .filter(item -> LEAVE_SOMETHING.stream().noneMatch(item::endsWith)).toList();
        return plain.isEmpty() ? Optional.empty() : Optional.of(plain.get(Math.floorMod(index, plain.size())));
    }

    /** How many of {@code crafts} crafts come up, at {@code percent} each. */
    public int bonus(int crafts, double percent, RandomGenerator random) {
        if (percent <= 0) {
            return 0;
        }
        int won = 0;
        for (int craft = 0; craft < crafts; craft++) {
            if (random.nextDouble() * 100 < percent) {
                won++;
            }
        }
        return won;
    }

    /** Of {@code wanted} extras, what still fits in the minute when {@code used} were given already. */
    public int allowed(int used, int wanted) {
        return Math.max(0, Math.min(wanted, MOST_A_MINUTE - used));
    }

    /** "Lasts X% longer": the chance a point of wear is spared, so X% more points are needed to wear it out. */
    public double spareWear(double percent) {
        return percent <= 0 ? 0 : percent / (100 + percent);
    }

    public int cookTime(int base, double faster) {
        if (faster <= 0) {
            return base;
        }
        return Math.max(1, (int) Math.round(base * 100 / (100 + faster)));
    }

    public int burnTime(int base, double longer) {
        if (longer <= 0) {
            return base;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(base * (100 + longer) / 100));
    }

    /** Whether a smelt may come out double: not food — that is Cooking's — and only where both fit. */
    public boolean mayDouble(String result, boolean edible, int alreadyOut, int maxStack) {
        return !edible && !NEVER_DOUBLED.contains(result) && alreadyOut + 2 <= maxStack;
    }

    /** What auto-smelt makes of a dropped item, if it is a raw ore. */
    public Optional<String> smelted(String item) {
        return Optional.ofNullable(SMELTED.get(item));
    }

    public double experience(String smelted) {
        return EXPERIENCE.getOrDefault(smelted, 0.0);
    }

    /** A furnace's experience for {@code count} items: the whole part, and the rest by chance — as a furnace does. */
    public int orbs(int count, double each, RandomGenerator random) {
        double total = count * each;
        int whole = (int) Math.floor(total);
        return whole + (random.nextDouble() < total - whole ? 1 : 0);
    }

    @Override
    public String describe() {
        return "which crafts and smelts an artisan gets more from, and how much";
    }
}
