package de.raindancer.modules.skills.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One player's adventurer: how far they have come, and what they took in the skill tree.
 *
 * @param nodes    the rank of each skill tree node taken, as {@code <skill>:<node>}; missing means 0
 * @param respecAt when they last unlearnt the tree, epoch millis; 0 for never
 */
public record Hero(UUID player, int level, long xp, Map<String, Integer> nodes, long respecAt) {

    public static final int MOST_LEVEL = 100;

    public Hero {
        level = Math.max(1, Math.min(MOST_LEVEL, level));
        xp = Math.max(0, xp);
        Map<String, Integer> copy = new HashMap<>();
        if (nodes != null) {
            nodes.forEach((node, rank) -> {
                if (node != null && rank != null && rank > 0) {
                    copy.put(node, rank);
                }
            });
        }
        nodes = Map.copyOf(copy);
        respecAt = Math.max(0, respecAt);
    }

    /** A level 1 adventurer, with nothing taken. */
    public static Hero fresh(UUID player) {
        return new Hero(player, 1, 0, Map.of(), 0);
    }

    /** A rank by its full key, {@code melee:brawler}. */
    public int rank(String key) {
        return nodes.getOrDefault(key, 0);
    }

    /** The ranks of one skill's nodes, by node id. */
    public Map<String, Integer> nodesOf(String skill) {
        String prefix = skill + ':';
        Map<String, Integer> mine = new HashMap<>();
        nodes.forEach((key, rank) -> {
            if (key.startsWith(prefix)) {
                mine.put(key.substring(prefix.length()), rank);
            }
        });
        return mine;
    }

    /** The hero without any rank in one skill's nodes. */
    public Hero without(String skill) {
        String prefix = skill + ':';
        Map<String, Integer> rest = new HashMap<>(nodes);
        rest.keySet().removeIf(key -> key.startsWith(prefix));
        return new Hero(player, level, xp, rest, respecAt);
    }

    public Hero withLevel(int newLevel, long newXp) {
        return new Hero(player, newLevel, newXp, nodes, respecAt);
    }

    public Hero withRank(String node, int rank) {
        Map<String, Integer> next = new HashMap<>(nodes);
        next.put(node, rank);
        return new Hero(player, level, xp, next, respecAt);
    }

    public Hero unlearnt(long now) {
        return new Hero(player, level, xp, Map.of(), now);
    }

    /** One skill unlearnt, the time of it kept for the wait. */
    public Hero unlearnt(String skill, long now) {
        Hero rest = without(skill);
        return new Hero(player, level, xp, rest.nodes(), now);
    }
}
