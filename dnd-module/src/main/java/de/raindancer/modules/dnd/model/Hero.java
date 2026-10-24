package de.raindancer.modules.dnd.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One player's adventurer, as it stands: who they are, how far they have come, and what they have spent since their
 * last rest.
 *
 * <p>Spent slots may go below zero: a sorcerer who turns sorcery points into a slot has one more of that level than
 * the table gives, and a long rest puts it back to the table.
 *
 * @param improvements ability points earned and not yet placed, two per Ability Score Improvement
 * @param loadout      what sits on hotbar slots 1 to 8 of the casting stance, by id; empty strings are empty slots
 * @param slotsSpent   by slot level, index 0 unused
 * @param longRestAt   epoch milliseconds of the last long rest, 0 for never
 */
public record Hero(UUID player, HeroClass heroClass, int level, long xp, AbilityScores scores, int improvements,
                   List<String> loadout, List<Integer> slotsSpent, int sorceryPoints, int hitDiceSpent,
                   Map<Feature, Integer> used, long longRestAt) {

    public static final int MOST_LEVEL = 20;
    public static final int LOADOUT_SIZE = 8;
    public static final int SLOT_LEVELS = 9;

    public Hero {
        level = Math.max(1, Math.min(MOST_LEVEL, level));
        xp = Math.max(0, xp);
        improvements = Math.max(0, improvements);
        loadout = sized(loadout);
        slotsSpent = slots(slotsSpent);
        sorceryPoints = Math.max(0, sorceryPoints);
        hitDiceSpent = Math.max(0, Math.min(level, hitDiceSpent));
        Map<Feature, Integer> copy = new EnumMap<>(Feature.class);
        if (used != null) {
            used.forEach((feature, count) -> {
                if (feature != null && count != null && count > 0) {
                    copy.put(feature, count);
                }
            });
        }
        used = Collections.unmodifiableMap(copy);
    }

    private static List<String> sized(List<String> loadout) {
        List<String> fixed = new ArrayList<>(LOADOUT_SIZE);
        for (int slot = 0; slot < LOADOUT_SIZE; slot++) {
            String id = loadout != null && slot < loadout.size() ? loadout.get(slot) : "";
            fixed.add(id == null ? "" : id);
        }
        return List.copyOf(fixed);
    }

    private static List<Integer> slots(List<Integer> spent) {
        List<Integer> fixed = new ArrayList<>(SLOT_LEVELS + 1);
        for (int level = 0; level <= SLOT_LEVELS; level++) {
            Integer value = spent != null && level < spent.size() ? spent.get(level) : null;
            fixed.add(level == 0 || value == null ? 0 : value);
        }
        return List.copyOf(fixed);
    }

    /** A level 1 adventurer of that class, rested and with nothing spent. */
    public static Hero fresh(UUID player, HeroClass heroClass, List<String> loadout) {
        return new Hero(player, heroClass, 1, 0, heroClass.startingScores(), 0, loadout, List.of(), 0, 0, Map.of(),
                0);
    }

    public int modifier(Ability ability) {
        return scores.modifier(ability);
    }

    public int spentSlots(int slotLevel) {
        return slotLevel < 1 || slotLevel > SLOT_LEVELS ? 0 : slotsSpent.get(slotLevel);
    }

    public int usedOf(Feature feature) {
        return used.getOrDefault(feature.pool(), 0);
    }

    public Hero withLevel(int newLevel, long newXp, int newImprovements) {
        return new Hero(player, heroClass, newLevel, newXp, scores, newImprovements, loadout, slotsSpent,
                sorceryPoints, hitDiceSpent, used, longRestAt);
    }

    public Hero withXp(long newXp) {
        return new Hero(player, heroClass, level, newXp, scores, improvements, loadout, slotsSpent, sorceryPoints,
                hitDiceSpent, used, longRestAt);
    }

    public Hero withScores(AbilityScores newScores, int newImprovements) {
        return new Hero(player, heroClass, level, xp, newScores, newImprovements, loadout, slotsSpent, sorceryPoints,
                hitDiceSpent, used, longRestAt);
    }

    public Hero withLoadout(List<String> newLoadout) {
        return new Hero(player, heroClass, level, xp, scores, improvements, newLoadout, slotsSpent, sorceryPoints,
                hitDiceSpent, used, longRestAt);
    }

    public Hero withSlotSpent(int slotLevel, int spent) {
        List<Integer> next = new ArrayList<>(slotsSpent);
        if (slotLevel >= 1 && slotLevel <= SLOT_LEVELS) {
            next.set(slotLevel, spent);
        }
        return new Hero(player, heroClass, level, xp, scores, improvements, loadout, next, sorceryPoints,
                hitDiceSpent, used, longRestAt);
    }

    public Hero withSlotsSpent(List<Integer> spent) {
        return new Hero(player, heroClass, level, xp, scores, improvements, loadout, spent, sorceryPoints,
                hitDiceSpent, used, longRestAt);
    }

    public Hero withSorceryPoints(int points) {
        return new Hero(player, heroClass, level, xp, scores, improvements, loadout, slotsSpent, points,
                hitDiceSpent, used, longRestAt);
    }

    public Hero withHitDiceSpent(int spent) {
        return new Hero(player, heroClass, level, xp, scores, improvements, loadout, slotsSpent, sorceryPoints,
                spent, used, longRestAt);
    }

    public Hero withUsed(Feature feature, int count) {
        Map<Feature, Integer> next = new EnumMap<>(Feature.class);
        next.putAll(used);
        next.put(feature.pool(), count);
        return new Hero(player, heroClass, level, xp, scores, improvements, loadout, slotsSpent, sorceryPoints,
                hitDiceSpent, next, longRestAt);
    }

    public Hero withUsed(Map<Feature, Integer> all) {
        return new Hero(player, heroClass, level, xp, scores, improvements, loadout, slotsSpent, sorceryPoints,
                hitDiceSpent, all, longRestAt);
    }

    public Hero withLongRestAt(long at) {
        return new Hero(player, heroClass, level, xp, scores, improvements, loadout, slotsSpent, sorceryPoints,
                hitDiceSpent, used, at);
    }
}
