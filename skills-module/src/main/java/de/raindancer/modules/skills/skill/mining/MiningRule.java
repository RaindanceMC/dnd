package de.raindancer.modules.skills.skill.mining;

import de.raindancer.core.world.activity.Ores;
import de.raindancer.modules.skills.rules.ISkillsRule;
import org.bukkit.Material;

import java.util.Optional;

/** The Mining skill's sums: what a vein is, haste, night vision deep down, how far ore sense looks. */
public final class MiningRule implements ISkillsRule {

    /** Night vision lent deep down, in ticks; topped up before the game starts to flicker it (ten seconds). */
    public static final int NIGHT_VISION_TICKS = 400;
    private static final int NIGHT_VISION_TOP_UP = 220;
    /** A vein is followed no further than this from the first ore. */
    public static final int VEIN_REACH = 8;
    private static final int MOST_SENSE = 12;

    public boolean ore(Material block) {
        return Ores.oreOf(block).isPresent();
    }

    /** One ore group — iron for both iron ores — and nothing that is not ore. */
    public boolean sameVein(Material first, Material other) {
        Optional<String> group = Ores.oreOf(first);
        return group.isPresent() && group.equals(Ores.oreOf(other));
    }

    public int hasteTicks(double seconds) {
        return (int) Math.max(0, Math.round(seconds * 20));
    }

    public int hasteAmplifier(boolean hasteTwo) {
        return hasteTwo ? 1 : 0;
    }

    /** Whether ours goes on over the haste a player has ({@code -1} for none): stronger, or as strong and longer. */
    public boolean replacesHaste(int amplifier, int ticks, int ourAmplifier, int ourTicks) {
        return ourAmplifier > amplifier || (ourAmplifier == amplifier && ourTicks > ticks);
    }

    public boolean deep(int y, boolean overworld) {
        return overworld && y < 0;
    }

    /** Night vision a player has is ours — ambient, without particles, no longer than we lend it. */
    public boolean ours(boolean ambient, boolean particles, int ticks) {
        return ambient && !particles && ticks <= NIGHT_VISION_TICKS;
    }

    public boolean needsNightVision(int ticksLeft) {
        return ticksLeft < NIGHT_VISION_TOP_UP;
    }

    public int senseRadius(double value) {
        return (int) Math.max(0, Math.min(MOST_SENSE, Math.floor(value)));
    }

    /** A vein goes on only while the pickaxe has more than a point or two left. */
    public boolean mayVeinOn(int maxDurability, int damage) {
        return maxDurability <= 0 || maxDurability - damage > 2;
    }

    @Override
    public String describe() {
        return "what a vein is, haste after ore, night vision deep down and how far ore sense looks";
    }
}
