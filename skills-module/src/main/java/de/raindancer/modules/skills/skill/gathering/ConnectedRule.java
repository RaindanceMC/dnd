package de.raindancer.modules.skills.skill.gathering;

import de.raindancer.modules.skills.rules.ISkillsRule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** The blocks joined to one — a vein of ore, a tree's trunk — corners included, nearest first. */
public final class ConnectedRule implements ISkillsRule {

    /**
     * @param joins    whether a spot belongs (the start is not asked, and never in the answer)
     * @param most     no more than this many
     * @param reach    no further from the start than this, on any axis
     * @param notBelow nothing below the start's height
     */
    public List<Spot> connected(Spot start, Predicate<Spot> joins, int most, int reach, boolean notBelow) {
        List<Spot> found = new ArrayList<>();
        if (most <= 0) {
            return found;
        }
        Set<Spot> seen = new HashSet<>();
        seen.add(start);
        ArrayDeque<Spot> next = new ArrayDeque<>();
        next.add(start);
        while (!next.isEmpty()) {
            Spot at = next.poll();
            for (int dy = 1; dy >= -1; dy--) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Spot near = at.plus(dx, dy, dz);
                        if (!seen.add(near) || Math.abs(near.x() - start.x()) > reach
                                || Math.abs(near.y() - start.y()) > reach || Math.abs(near.z() - start.z()) > reach
                                || (notBelow && near.y() < start.y()) || !joins.test(near)) {
                            continue;
                        }
                        found.add(near);
                        if (found.size() >= most) {
                            return found;
                        }
                        next.add(near);
                    }
                }
            }
        }
        return found;
    }

    @Override
    public String describe() {
        return "which blocks are joined to one: a vein, a trunk";
    }
}
