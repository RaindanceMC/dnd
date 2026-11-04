package de.raindancer.modules.skills.skill.woodcutting;

import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.skill.gathering.EffectText;

import java.util.Locale;

/** What a node of the Woodcutting tree does, per rank; worked in {@link WoodcuttingSkill}. */
public enum WoodcuttingEffect implements Effect {

    CHOP_SPEED("%s%% faster chopping with an axe", 80),
    CHOP_WEAR("%s%% chance an axe is not worn by a block", 60),
    CHOP_LOG_DOUBLE("%s%% chance a log drops twice over", 20),
    CHOP_SAPLING("%s%% chance leaves drop an extra sapling", 50),
    CHOP_APPLE("%s%% chance oak and dark oak leaves drop an apple", 25),
    CHOP_STICKS("%s%% chance leaves drop sticks", 50),
    CHOP_TIMBER("Timber: crouch while chopping to fell up to %s more logs of the tree", 96),
    CHOP_TIMBER_WEAR("%s%% chance a felled tree's further logs do not wear the axe", 75),
    CHOP_LOG_TRIPLE("Heartwood: %s%% chance a log drops three times over", 3),
    CHOP_REPLANT("Replanter: chopping a tree's lowest log plants a sapling from your bag in its place", 1, true),
    CHOP_CLEAR_CANOPY("Clear Canopy: a felled tree's leaves come down with it", 1, true);

    private final String text;
    private final int cap;
    private final boolean feat;

    WoodcuttingEffect(String text, int cap) {
        this(text, cap, false);
    }

    WoodcuttingEffect(String text, int cap, boolean feat) {
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
