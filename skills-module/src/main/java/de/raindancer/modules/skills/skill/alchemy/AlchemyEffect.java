package de.raindancer.modules.skills.skill.alchemy;

import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.skill.EffectText;

/** What a node of the Alchemy tree does, per rank. */
public enum AlchemyEffect implements Effect {

    DURATION("brew_duration", "Potions you brew last %s%% longer", 100),
    POTENCY("brew_potency", "%s%% chance a potion you brew comes out a level stronger", 50),
    EXTRA("brew_extra", "%s%% chance a brew fills an empty bottle slot with one more potion", 60),
    THRIFT("brew_thrift", "%s%% chance a brew does not use up its ingredient", 40),
    SPEED("brew_speed", "Stands you work brew %s%% faster", 90),
    BOTTOMLESS("brew_bottomless", "Bottomless Flask: one potion in three you drink is still full afterwards", 1),
    CATALYST("brew_catalyst", "Catalyst: a stand you work burns no blaze powder", 1);

    private final String id;
    private final String text;
    private final int cap;

    AlchemyEffect(String id, String text, int cap) {
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
