package de.raindancer.modules.skills.skill.farming;

import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.skill.EffectText;

/** What a node of the Farming tree does, per rank. */
public enum FarmingEffect implements Effect {

    CROP_YIELD("farm_yield", "%s%% chance a grown crop drops twice", 100),
    HOE_SAVE("farm_hoe", "%s%% chance a hoe takes no wear", 75),
    GROWTH("farm_growth", "Crops around you grow %s%% faster", 100),
    TWINS("farm_twins", "%s%% chance a breeding brings twins", 50),
    BUTCHER("farm_animal_drops", "%s%% chance an animal you kill drops its meat, leather or wool twice", 100),
    FLEECE("farm_shear", "%s%% more wool when you shear", 100),
    WEANING("farm_grow_up", "Babies you feed grow up %s%% sooner", 75),
    REPLANT("farm_replant", "Green Thumb: a grown crop you break plants itself again from its own seeds", 1),
    REAPER("farm_reaper", "Reaper: breaking a grown crop with a hoe reaps the grown crops around it too", 1),
    PLENTY("farm_plenty", "Plenty: animals you breed may breed again at once", 1);

    private final String id;
    private final String text;
    private final int cap;

    FarmingEffect(String id, String text, int cap) {
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
