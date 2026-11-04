package de.raindancer.modules.skills.skill.excavation;

import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.skill.gathering.EffectText;

import java.util.Locale;

/** What a node of the Excavation tree does, per rank; worked in {@link ExcavationSkill}. */
public enum ExcavationEffect implements Effect {

    DIG_SPEED("%s%% faster digging with a shovel", 80),
    DIG_WEAR("%s%% chance a shovel is not worn by a block", 60),
    DIG_TREASURE("%s%% chance to dig up a treasure from dirt, sand, gravel or clay", 6),
    DIG_FLINT("%s%% chance gravel gives a flint as well", 60),
    DIG_CLAY("%s%% chance clay gives two more clay balls", 60),
    DIG_BRUSH("%s%% chance a brushed find comes up twice", 50),
    DIG_BRUSH_WEAR("%s%% chance a brush is not worn", 60),
    DIG_EARTHMOVER("Earthmover: crouch to dig three by three of soft ground", 1, true),
    DIG_RARE_TREASURE("Rare treasures — diamonds, emeralds, music discs — can be dug up too", 1, true),
    DIG_KEEN_EYE("Keen Eye: crouch with a brush to see suspicious sand and gravel within 8 blocks", 1, true);

    private final String text;
    private final int cap;
    private final boolean feat;

    ExcavationEffect(String text, int cap) {
        this(text, cap, false);
    }

    ExcavationEffect(String text, int cap, boolean feat) {
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
