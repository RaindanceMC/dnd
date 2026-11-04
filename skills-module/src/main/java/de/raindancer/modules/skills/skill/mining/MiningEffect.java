package de.raindancer.modules.skills.skill.mining;

import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.skill.gathering.EffectText;

import java.util.Locale;

/** What a node of the Mining tree does, per rank; worked in {@link MiningSkill}. */
public enum MiningEffect implements Effect {

    MINE_SPEED("%s%% faster mining with a pickaxe", 80),
    MINE_ORE_DOUBLE("%s%% chance an ore drops twice over", 60),
    MINE_ORE_XP("%s%% more experience from ores", 150),
    MINE_HASTE("Haste for %s second(s) after mining an ore", 12),
    MINE_WEAR("%s%% chance a pickaxe is not worn by a block", 60),
    MINE_VEIN("Vein Miner: crouch to mine up to %s more ores of the vein at once", 32),
    MINE_VEIN_WEAR("%s%% chance a vein's further ores do not wear the pickaxe", 75),
    MINE_MOTHERLODE("Motherlode: %s%% chance an ore drops three times over", 10),
    MINE_DEEP_SIGHT("Deep Sight: night vision below height 0", 1, true),
    MINE_HASTE_TWO("Haste II instead of Haste I after an ore", 1, true),
    MINE_VEIN_MAGNET("Lodestone: a vein's drops and experience come straight to you", 1, true);

    private final String text;
    private final int cap;
    private final boolean feat;

    MiningEffect(String text, int cap) {
        this(text, cap, false);
    }

    MiningEffect(String text, int cap, boolean feat) {
        this.text = text;
        this.cap = cap;
        this.feat = feat;
    }

    @Override
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public int cap() {
        return cap;
    }

    @Override
    public String describe(double value) {
        return EffectText.describe(text, value, feat);
    }
}
