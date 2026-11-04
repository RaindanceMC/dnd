package de.raindancer.modules.skills.skill.building;

import de.raindancer.core.world.safety.Spot;
import de.raindancer.modules.skills.rules.ISkillsRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * What a paste puts where, before a block moves: only on air (or, with Demolition, over what can be broken and holds
 * nothing), every block on land the player may build on — asked per block, as a block placed by hand would be — and
 * every one paid for from the inventory. One block refused, or one item short, and nothing is placed at all.
 */
public final class PasteRule implements ISkillsRule {

    private static final BlueprintRule BLUEPRINTS = new BlueprintRule();

    /** The world as a paste sees it. */
    public interface Site {

        /** The full block state there; {@code null} where nothing can be placed (unloaded, outside the world). */
        String stateAt(Spot spot);

        /** A chest's items, a sign's text: broken over, that would be gone. */
        boolean holdsMore(Spot spot);

        boolean unbreakable(Spot spot);
    }

    /** One block of the blueprint, already turned and put where it goes. */
    public record Placed(Spot spot, BlueprintRule.Piece piece) {
    }

    /**
     * @param demolish     Demolition taken: what stands in the way is cleared
     * @param thrift       the percent chance a block costs nothing
     * @param have         plain items in the inventory, by id
     */
    public record Request(List<Placed> pieces, Site site, Predicate<Spot> mayBuild, Predicate<Spot> mayBreak,
                          boolean demolish, double thrift, Map<String, Integer> have) {
    }

    /**
     * One block to place.
     *
     * @param paid     what it costs, given back by an undo
     * @param replaced what Demolition clears there, {@code null} for air
     * @param free     placed by Thrift for nothing — marked, so it drops nothing
     */
    public record Step(Spot spot, String state, Map<String, Integer> paid, String replaced, boolean free) {
    }

    /**
     * @param occupied blocks left out because something stands there
     * @param refused  blocks on land the player may not build on
     */
    public record Plan(List<Step> steps, Map<String, Integer> cost, Map<String, Integer> missing, int refused,
                       int occupied) {

        public boolean ok() {
            return refused == 0 && missing.isEmpty() && !steps.isEmpty();
        }
    }

    public Plan plan(Request request, RandomGenerator random) {
        List<Step> steps = new ArrayList<>();
        int refused = 0;
        int occupied = 0;
        Set<Spot> firstHalves = new HashSet<>();
        List<Placed> secondHalves = new ArrayList<>();
        for (Placed placed : request.pieces()) {
            if (second(placed.piece().state())) {
                secondHalves.add(placed);
                continue;
            }
            Verdict verdict = verdict(request, placed);
            switch (verdict) {
                case PLACE, CLEAR -> {
                    if (!allowed(request, placed.spot(), verdict == Verdict.CLEAR)) {
                        refused++;
                        continue;
                    }
                    boolean free = placed.piece().items().size() > 0 && !twoBlocks(placed.piece().state())
                            && random.nextDouble() * 100 < request.thrift();
                    steps.add(new Step(placed.spot(), placed.piece().state(), free ? Map.of() : placed.piece().items(),
                            verdict == Verdict.CLEAR ? request.site().stateAt(placed.spot()) : null, free));
                    if (twoBlocks(placed.piece().state())) {
                        firstHalves.add(placed.spot());
                    }
                }
                case OCCUPIED -> occupied++;
                case SKIP -> { }
            }
        }
        for (Placed placed : secondHalves) {
            Verdict verdict = verdict(request, placed);
            if (!firstHalves.contains(partner(placed.spot(), placed.piece().state()))
                    || (verdict != Verdict.PLACE && verdict != Verdict.CLEAR)) {
                if (verdict == Verdict.OCCUPIED) {
                    occupied++;
                }
                continue;
            }
            if (!allowed(request, placed.spot(), verdict == Verdict.CLEAR)) {
                refused++;
                continue;
            }
            steps.add(new Step(placed.spot(), placed.piece().state(), Map.of(),
                    verdict == Verdict.CLEAR ? request.site().stateAt(placed.spot()) : null, false));
        }
        Map<String, Integer> cost = new LinkedHashMap<>();
        for (Step step : steps) {
            step.paid().forEach((item, count) -> cost.merge(item, count, Integer::sum));
        }
        Map<String, Integer> missing = BLUEPRINTS.missing(cost, request.have());
        if (refused > 0 || !missing.isEmpty()) {
            return new Plan(List.of(), cost, missing, refused, occupied);
        }
        return new Plan(List.copyOf(steps), cost, missing, 0, occupied);
    }

    private enum Verdict { PLACE, CLEAR, OCCUPIED, SKIP }

    private static Verdict verdict(Request request, Placed placed) {
        String there = request.site().stateAt(placed.spot());
        if (there == null || there.equals(placed.piece().state())) {
            return Verdict.SKIP;
        }
        if (air(there)) {
            return Verdict.PLACE;
        }
        if (!request.demolish() || request.site().holdsMore(placed.spot())
                || request.site().unbreakable(placed.spot())) {
            return Verdict.OCCUPIED;
        }
        return Verdict.CLEAR;
    }

    private static boolean allowed(Request request, Spot spot, boolean clearing) {
        return request.mayBuild().test(spot) && (!clearing || request.mayBreak().test(spot));
    }

    static boolean air(String state) {
        String id = state.indexOf('[') < 0 ? state : state.substring(0, state.indexOf('['));
        return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
    }

    /** A door's upper half, a bed's head: placed only beside its first half, for nothing. */
    static boolean second(String state) {
        return state.contains("half=upper") || state.contains("part=head");
    }

    /** A door, a tall plant or a bed: never free by Thrift, since its drop comes from one half only. */
    static boolean twoBlocks(String state) {
        return state.contains("half=lower") || state.contains("half=upper") || state.contains("part=");
    }

    /** Where the first half of a second half stands: below an upper half; behind a bed's head, against its facing. */
    static Spot partner(Spot spot, String state) {
        if (state.contains("half=upper")) {
            return new Spot(spot.world(), spot.x(), spot.y() - 1, spot.z());
        }
        int dx = 0;
        int dz = 0;
        if (state.contains("facing=north")) {
            dz = 1;
        } else if (state.contains("facing=south")) {
            dz = -1;
        } else if (state.contains("facing=east")) {
            dx = -1;
        } else if (state.contains("facing=west")) {
            dx = 1;
        }
        return new Spot(spot.world(), spot.x() + dx, spot.y(), spot.z() + dz);
    }

    @Override
    public String describe() {
        return "where a paste may put its blocks, and whether the player can pay for them";
    }
}
