package de.raindancer.modules.skills.skill.smithing;

import de.raindancer.modules.skills.rules.ISkillsRule;
import de.raindancer.modules.skills.skill.gathering.Loot;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Smithing skill's sums: what anvil work costs, how much prior work it adds, how far a repair mends, what an item
 * salvages to — never more than went into it — and what counts as gear.
 */
public final class SmithingRule implements ISkillsRule {

    /** The most levels anvil work may cost before the game calls it too expensive, less one. */
    public static final int MOST_LEVELS = 39;

    private record Tier(String prefix, Material material, int perUnit) {
    }

    private static final List<Tier> TIERS = List.of(new Tier("IRON_", Material.IRON_INGOT, 1),
            new Tier("GOLDEN_", Material.GOLD_INGOT, 1), new Tier("DIAMOND_", Material.DIAMOND, 1),
            new Tier("COPPER_", Material.COPPER_INGOT, 1), new Tier("CHAINMAIL_", Material.IRON_NUGGET, 1),
            new Tier("LEATHER_", Material.LEATHER, 1), new Tier("NETHERITE_", Material.DIAMOND, 1));
    private static final Map<String, Integer> PIECES = Map.of("_SWORD", 2, "_PICKAXE", 3, "_AXE", 3,
            "_SHOVEL", 1, "_HOE", 2, "_HELMET", 5, "_CHESTPLATE", 8, "_LEGGINGS", 7, "_BOOTS", 4);
    /** A netherite item is its diamond one and an ingot: four scrap and four gold — the scrap is what is kept. */
    private static final int NETHERITE_SCRAP = 4;

    public int cost(int levels, double discount, boolean noLimit) {
        if (levels <= 0) {
            return 0;
        }
        int cost = Math.max(1, (int) Math.ceil(levels * (1 - Math.min(100, Math.max(0, discount)) / 100.0) - 1e-9));
        return noLimit ? Math.min(MOST_LEVELS, cost) : cost;
    }

    /** The result's prior work: what the items brought, plus less of what the game would add. */
    public int priorWork(int brought, int result, double rework) {
        if (result <= brought) {
            return result;
        }
        double added = (result - brought) * (1 - Math.min(100, Math.max(0, rework)) / 100.0);
        return brought + (int) Math.round(added);
    }

    /** The result's damage when a repair with materials mends {@code percent} more than the game did. */
    public int mended(int before, int after, double percent) {
        if (after >= before || percent <= 0) {
            return after;
        }
        int more = (int) Math.round((before - after) * percent / 100.0);
        return Math.max(0, after - more);
    }

    private static Optional<Tier> tier(String name) {
        return TIERS.stream().filter(tier -> name.startsWith(tier.prefix())).findFirst();
    }

    private static Optional<Integer> pieces(String name) {
        return PIECES.entrySet().stream().filter(piece -> name.endsWith(piece.getKey()))
                .filter(piece -> !(piece.getKey().equals("_AXE") && name.endsWith("_PICKAXE")))
                .map(Map.Entry::getValue).findFirst();
    }

    public boolean salvageable(Material item) {
        if (item == null) {
            return false;
        }
        String name = item.name();
        return tier(name).isPresent() && pieces(name).isPresent();
    }

    /**
     * What an item salvages to: its pieces of material, times the share of its durability left, times the skill's
     * share; the fraction of one by chance. Never more than the item took.
     */
    public List<Loot> salvage(Material item, int damage, int maxDurability, double percent, double roll) {
        List<Loot> back = new ArrayList<>();
        if (!salvageable(item) || maxDurability <= 0 || percent <= 0) {
            return back;
        }
        String name = item.name();
        Tier tier = tier(name).orElseThrow();
        double share = Math.max(0, maxDurability - damage) / (double) maxDurability * Math.min(100, percent) / 100.0;
        add(back, tier.material(), pieces(name).orElseThrow() * tier.perUnit(), share, roll);
        if (tier.prefix().equals("NETHERITE_")) {
            add(back, Material.NETHERITE_SCRAP, NETHERITE_SCRAP, share, roll);
        }
        return back;
    }

    private static void add(List<Loot> back, Material material, int whole, double share, double roll) {
        double exact = whole * share;
        int amount = (int) Math.floor(exact + 1e-9);
        if (roll < exact - amount) {
            amount++;
        }
        if (amount > 0) {
            back.add(new Loot(material, Math.min(whole, amount)));
        }
    }

    /** The enchantment the salvaged book keeps: the highest level, then the first by name. */
    public Optional<String> best(Map<String, Integer> enchantments) {
        return enchantments.entrySet().stream()
                .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .map(Map.Entry::getKey);
    }

    /** Armour and weapons; tools belong to the gathering skills. */
    public boolean gear(Material item) {
        if (item == null) {
            return false;
        }
        String name = item.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS") || name.endsWith("_SWORD") || name.endsWith("_SPEAR")
                || item == Material.SHIELD || item == Material.BOW || item == Material.CROSSBOW
                || item == Material.TRIDENT || item == Material.MACE || item == Material.ELYTRA;
    }

    public boolean netheriteUpgrade(Material template, Material result) {
        return template == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE && result != null
                && result.name().startsWith("NETHERITE_");
    }

    /** Whether one template went into the work: the player had one fewer after it, wherever they keep them. */
    public boolean templateTaken(int before, int after) {
        return before - after == 1;
    }

    @Override
    public String describe() {
        return "what anvil work costs and adds, how far a repair mends, what an item salvages to and what gear is";
    }
}
