package de.raindancer.modules.skills.skill.smithing;

import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.skill.gathering.EffectText;

import java.util.Locale;

/** What a node of the Smithing tree does, per rank; worked in {@link SmithingSkill}. */
public enum SmithingEffect implements Effect {

    SMITH_DISCOUNT("Anvil work costs %s%% fewer levels", 60),
    SMITH_REWORK("Anvil work adds %s%% less to an item's prior work", 100),
    SMITH_MEND("A repair with materials mends %s%% more", 100),
    SMITH_SALVAGE("Salvage: crouch and use a tool, weapon or armour on an anvil to get back up to %s%% of its "
            + "materials", 80),
    SMITH_GEAR_WEAR("%s%% chance armour and weapons are not worn", 50),
    SMITH_TEMPLATE("%s%% chance a smithing template is not used up", 50),
    SMITH_NO_LIMIT("Master Smith: no anvil work ever costs more than 39 levels — never too expensive", 1, true),
    SMITH_ANVIL_SAVE("Anvils are never damaged by your work", 1, true),
    SMITH_SALVAGE_BOOK("Salvaging an enchanted item keeps its best enchantment as a book", 1, true),
    SMITH_NETHERITE_SCRAP("Forgemaster: an upgrade to netherite gives a netherite scrap back", 1, true);

    private final String text;
    private final int cap;
    private final boolean feat;

    SmithingEffect(String text, int cap) {
        this(text, cap, false);
    }

    SmithingEffect(String text, int cap, boolean feat) {
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
