package de.raindancer.modules.skills.skill.foraging;

import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.skill.EffectText;

/** What a node of the Foraging tree does, per rank. */
public enum ForagingEffect implements Effect {

    BERRIES("forage_berries", "%s%% more berries picked", 250),
    WILD("forage_wild", "%s%% chance a flower or mushroom the world grew drops twice", 100),
    RARE("forage_rare", "%s%% chance of a rare find while foraging", 25),
    HONEY("forage_honey", "%s%% chance shearing a hive gives another honeycomb", 100),
    BONE_MEAL("forage_bone_meal", "%s%% chance bone meal is not used up", 50),
    BRAMBLE("forage_bramble", "Bramble Kin: berry bushes never prick you, and one in four you pick is ripe again at once", 1),
    BLOOM("forage_bloom", "Bloom: bone meal grows a sapling, a berry bush or a stem all the way at once", 1);

    private final String id;
    private final String text;
    private final int cap;

    ForagingEffect(String id, String text, int cap) {
        this.id = id;
        this.text = text;
        this.cap = cap;
    }

    @Override
    public String id() {
        return id;
    }

    /** The most all ranks together may add up to; a feat's is 1: had or not. */
    @Override
    public int cap() {
        return cap;
    }

    @Override
    public String describe(double value) {
        return EffectText.describe(text, value);
    }
}
