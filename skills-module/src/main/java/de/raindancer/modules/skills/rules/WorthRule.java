package de.raindancer.modules.skills.rules;

import de.raindancer.core.world.activity.Activity;
import de.raindancer.core.world.activity.ActivityKind;
import de.raindancer.core.world.activity.Ores;

import java.util.EnumMap;
import java.util.Map;

/**
 * What an activity is worth in adventurer experience, and how much of each kind counts in a minute. What a machine or
 * a farm could do without end — monsters, raw stone from a generator, blocks placed and broken, crafting, smelting,
 * walking — is capped per minute at about what a minute of real work earns; bosses, raids, advancements and a new
 * biome are worth a lot and never capped. Kinds this does not know are worth nothing.
 */
public final class WorthRule implements ISkillsRule {

    private static final Map<String, Long> BOSSES = Map.of("ender_dragon", 5_000L, "wither", 3_000L,
            "warden", 3_000L, "elder_guardian", 1_000L);
    private static final long LEAST_BOSS = 1_000;
    private static final Map<String, Long> TOUGH_MONSTERS = Map.ofEntries(
            Map.entry("creeper", 20L), Map.entry("witch", 20L), Map.entry("enderman", 20L),
            Map.entry("vindicator", 20L), Map.entry("ghast", 20L), Map.entry("creaking", 20L),
            Map.entry("blaze", 40L), Map.entry("wither_skeleton", 40L), Map.entry("piglin_brute", 40L),
            Map.entry("illusioner", 40L), Map.entry("evoker", 90L), Map.entry("ravager", 90L));
    private static final long MONSTER = 10;

    private static final Map<ActivityKind, Long> PER_MINUTE = new EnumMap<>(Map.ofEntries(
            Map.entry(ActivityKind.MONSTER, 200L), Map.entry(ActivityKind.DIG, 60L),
            Map.entry(ActivityKind.EXCAVATE, 60L), Map.entry(ActivityKind.CHOP, 150L),
            Map.entry(ActivityKind.BUILD, 60L), Map.entry(ActivityKind.CRAFT, 150L),
            Map.entry(ActivityKind.SMELT, 120L), Map.entry(ActivityKind.COOK, 120L),
            Map.entry(ActivityKind.TRAVEL, 60L), Map.entry(ActivityKind.FORAGE, 120L),
            Map.entry(ActivityKind.HARVEST, 120L), Map.entry(ActivityKind.MINE, 400L)));

    /** Before the owner's xp-percent, the hero's own bonus and the minute's cap. */
    public long worth(Activity activity) {
        long each = switch (activity.kind()) {
            case PLAY -> 5;
            case MONSTER -> TOUGH_MONSTERS.getOrDefault(activity.what(), MONSTER);
            case BOSS -> BOSSES.getOrDefault(activity.what(), LEAST_BOSS);
            case ADVANCEMENT -> 150;
            case MINE -> 5L * Ores.weight(activity.what());
            case FISH -> 15;
            case BREED, TRADE -> 10;
            case HARVEST -> 2;
            case ENCHANT -> 20;
            case RAID -> 1_000;
            case DIG, EXCAVATE, BUILD -> 1;
            case CHOP, FORAGE -> 3;
            case CRAFT, COOK -> 2;
            case SMELT -> 1;
            case BREW -> 15;
            case TAME -> 50;
            case EXPLORE -> 100;
            case REPAIR -> 10;
            // A tenth of the blocks travelled: a minute's walk is about 25.
            case TRAVEL -> 0;
            default -> 0;
        };
        if (activity.kind() == ActivityKind.TRAVEL) {
            return activity.amount() / 10;
        }
        return each * activity.amount();
    }

    /** The most of a kind that counts in a minute; 0 for no cap. */
    public long perMinute(ActivityKind kind) {
        return PER_MINUTE.getOrDefault(kind, 0L);
    }

    /** Of {@code wanted}, what still fits in this minute when {@code used} of it counted already. */
    public long allowed(ActivityKind kind, long used, long wanted) {
        long cap = perMinute(kind);
        if (cap <= 0) {
            return Math.max(0, wanted);
        }
        return Math.max(0, Math.min(wanted, cap - used));
    }

    @Override
    public String describe() {
        return "what each kind of play is worth, and how much of it counts in a minute";
    }
}
