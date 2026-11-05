package de.raindancer.modules.skills.skill.gathering;

import de.raindancer.modules.skills.rules.ISkillsRule;
import org.bukkit.Material;

/** The sums every gathering skill does alike: chances, drops several times over, a bonus on a count, tools. */
public final class GatherRule implements ISkillsRule {

    public enum Tool { PICKAXE, SHOVEL, AXE, BRUSH, NONE }

    /** A chance in percent, against a roll in [0, 1). */
    public boolean chance(double percent, double roll) {
        return percent > 0 && roll < percent / 100.0;
    }

    /** How many times over a drop comes: three times first, then twice, else once. */
    public int times(double twicePercent, double thricePercent, double thriceRoll, double twiceRoll) {
        if (chance(thricePercent, thriceRoll)) {
            return 3;
        }
        return chance(twicePercent, twiceRoll) ? 2 : 1;
    }

    /** {@code base} with {@code percent} more; the fraction of one by chance. */
    public int scaled(int base, double percent, double roll) {
        if (base <= 0 || percent <= 0) {
            return Math.max(0, base);
        }
        double exact = base * (1 + percent / 100.0);
        int whole = (int) Math.floor(exact);
        return whole + (roll < exact - whole ? 1 : 0);
    }

    public Tool tool(Material material) {
        if (material == null) {
            return Tool.NONE;
        }
        String name = material.name();
        if (name.endsWith("_PICKAXE")) {
            return Tool.PICKAXE;
        }
        if (name.endsWith("_SHOVEL")) {
            return Tool.SHOVEL;
        }
        if (name.endsWith("_AXE")) {
            return Tool.AXE;
        }
        return material == Material.BRUSH ? Tool.BRUSH : Tool.NONE;
    }

    /** Silk touch keeps a block whole: what it drops is the block, and a block is never multiplied. */
    public boolean multiplies(boolean silkTouch) {
        return !silkTouch;
    }

    @Override
    public String describe() {
        return "chances, drops several times over and bonuses on counts for the gathering skills";
    }
}
