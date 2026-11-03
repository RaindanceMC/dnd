package de.raindancer.modules.dnd.rules;

import de.raindancer.core.world.activity.Activity;
import de.raindancer.core.world.activity.Ores;

import java.util.Map;

/**
 * What an activity is worth in adventurer experience. Ordinary monsters are worth nothing — a mob farm would pay
 * out without end — but bosses, raids and advancements are worth a lot, and every kind of play counts a little.
 */
public final class WorthRule implements IDndRule {

    private static final Map<String, Long> BOSSES = Map.of("ender_dragon", 5_000L, "wither", 3_000L,
            "warden", 3_000L, "elder_guardian", 1_000L);
    private static final long LEAST_BOSS = 1_000;

    /** Before the owner's xp-percent and the hero's own bonus. */
    public long worth(Activity activity) {
        long each = switch (activity.kind()) {
            case PLAY -> 10;
            case MONSTER -> 0;
            case BOSS -> BOSSES.getOrDefault(activity.what(), LEAST_BOSS);
            case ADVANCEMENT -> 150;
            case MINE -> 5L * Ores.weight(activity.what());
            case FISH -> 15;
            case BREED, TRADE -> 10;
            case HARVEST -> 2;
            case ENCHANT -> 20;
            case RAID -> 1_000;
        };
        return each * activity.amount();
    }

    @Override
    public String describe() {
        return "what playing, mining, farming, fishing, trading, enchanting, advancements, raids and bosses are worth";
    }
}
