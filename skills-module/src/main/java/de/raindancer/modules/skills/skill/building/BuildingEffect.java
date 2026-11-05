package de.raindancer.modules.skills.skill.building;

import de.raindancer.modules.skills.skill.AttributeEffect;
import de.raindancer.modules.skills.skill.SkillText;

import java.util.Locale;

/** What a node of the Building tree does: the mason's hands, and the architect's blueprints. */
public enum BuildingEffect implements AttributeEffect {

    BLOCK_REACH("+%s block(s) of reach for placing and breaking", 3, "block_interaction_range", 1.0),
    BLOCK_REFUND("%s%% chance a block you place is not used up", 30),
    CLIMB_SPEED("Climb ladders, vines and scaffolding %s%% faster", 100),
    QUICK_UNDO("Quick Undo: a block you placed in the last five seconds breaks at one hit", 1),
    BOTTOMLESS_HOD("Bottomless Hod: when the stack in your hand runs out, the next one from your inventory takes its place", 1),
    BLUEPRINT_SIZE("Blueprints up to %s blocks a side", 32),
    BLUEPRINT_SLOTS("Keep %s blueprint(s)", 20),
    BLUEPRINT_THRIFT("%s%% chance a pasted block costs nothing", 25),
    DEMOLITION("Demolition: a paste clears what stands in its way, and gives it to you", 1),
    MASTER_ARCHITECT("Master Architect: turn blueprints as you paste them, and see a ghost of them first", 1);

    private final String text;
    private final int cap;
    private final String attribute;
    private final double scale;

    BuildingEffect(String text, int cap) {
        this(text, cap, null, 0);
    }

    BuildingEffect(String text, int cap, String attribute, double scale) {
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
        return this == QUICK_UNDO || this == BOTTOMLESS_HOD || this == DEMOLITION || this == MASTER_ARCHITECT;
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
