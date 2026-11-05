package de.raindancer.modules.skills.skill.exploration;

import de.raindancer.modules.skills.skill.AttributeEffect;
import de.raindancer.modules.skills.skill.SkillText;

import java.util.Locale;

/** What a node of the Exploration tree does: the wayfarer on the road, the cartographer with maps and compass. */
public enum ExplorationEffect implements AttributeEffect {

    SPRINT_SPEED("%s%% faster while sprinting", 30),
    SPRINT_HUNGER("Sprinting and jumping tire you %s%% less", 60),
    SWIM_SPEED("Swim %s%% faster", 50, "water_movement_efficiency", 0.01),
    MOUNT_SPEED("Horses and other mounts you ride are %s%% faster", 40),
    ROAD_RUNNER("Roadrunner: 30% faster on paths, gravel, cobble and bricks", 1),
    EXPLORE_XP("%s%% more adventurer experience from a biome seen for the first time", 100),
    WIDE_MAPS("Maps you start show %s zoom level(s) further out", 4),
    TREASURE_SENSE("Sneak and use a compass: it points to the nearest village, ruin, wreck or temple within %s chunks — once every five minutes", 100),
    STRUCTURE_LOOT("%s%% chance a structure's chest you open first holds one more of something", 60),
    COMPASS_READING("Compass Reading: with a compass or a map in hand you see where you are, which way you face and the biome", 1);

    private final String text;
    private final int cap;
    private final String attribute;
    private final double scale;

    ExplorationEffect(String text, int cap) {
        this(text, cap, null, 0);
    }

    ExplorationEffect(String text, int cap, String attribute, double scale) {
        this.text = text;
        this.cap = cap;
        this.attribute = attribute;
        this.scale = scale;
    }

    @Override
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public int cap() {
        return cap;
    }

    public boolean feat() {
        return this == ROAD_RUNNER || this == COMPASS_READING;
    }

    @Override
    public String attribute() {
        return attribute;
    }

    @Override
    public double scale() {
        return scale;
    }

    @Override
    public boolean multiplies() {
        return false;
    }

    @Override
    public String describe(double value) {
        return SkillText.describe(text, feat(), value);
    }
}
