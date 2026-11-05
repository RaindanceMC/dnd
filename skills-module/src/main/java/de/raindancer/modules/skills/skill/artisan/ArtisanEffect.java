package de.raindancer.modules.skills.skill.artisan;

import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.skill.SkillText;

import java.util.Locale;

/** What a node of the Artisan tree does — crafting at the workbench, smelting at the furnace. */
public enum ArtisanEffect implements Effect {

    CRAFT_EXTRA("%s%% chance a craft makes one more of what it makes", 40),
    INGREDIENT_REFUND("%s%% chance a craft gives one of its ingredients back", 40),
    TOUGHER_TOOLS("Tools, weapons and armour you craft last %s%% longer", 150),
    MASTERWORK("Masterwork: tools, weapons and armour you craft come with Unbreaking I", 1),
    FURNACE_SPEED("Furnaces you load smelt %s%% faster", 150),
    FUEL_LAST("Fuel burns %s%% longer in furnaces you load", 100),
    DOUBLE_SMELT("%s%% chance a furnace you load smelts double", 35),
    AUTO_SMELT("Auto-smelt: ores you mine come out smelted (sneak to keep them raw)", 1),
    FORGE_HEART("Forge Heart: auto-smelted ores give a furnace's experience, and one in four comes out twice", 1);

    private final String text;
    private final int cap;

    ArtisanEffect(String text, int cap) {
        this.text = text;
        this.cap = cap;
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
        return this == MASTERWORK || this == AUTO_SMELT || this == FORGE_HEART;
    }

    @Override
    public String describe(double value) {
        return SkillText.describe(text, feat(), value);
    }
}
