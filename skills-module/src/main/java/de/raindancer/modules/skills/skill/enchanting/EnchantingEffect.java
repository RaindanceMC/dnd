package de.raindancer.modules.skills.skill.enchanting;

import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.skill.SkillText;

import java.util.Locale;

/** What a node of the Enchanting tree does: the enchanting table, and the anvil. */
public enum EnchantingEffect implements Effect {

    LEVEL_REFUND("%s%% chance the levels an enchantment cost come back", 40),
    LAPIS_SAVE("%s%% chance the lapis an enchantment cost is not used up", 60),
    BOOKSHELF_POWER("Enchanting tables count %s more bookshelf(s) around them for you", 15),
    FRESH_OFFERS("Fresh Offers: sneak and right-click an enchanting table with an empty hand for new offers — a level, once a minute", 1),
    TREASURE_OFFERS("Arcane Mastery: the table may offer you treasure enchantments like Mending — never a curse", 1),
    ANVIL_DISCOUNT("Anvil work costs %s%% fewer levels", 50),
    ANVIL_SAVE("%s%% chance your anvil work does not wear the anvil", 100),
    NO_PRIOR_PENALTY("Smith's Mastery: your anvil work never makes an item dearer to work again", 1),
    ORB_XP("%s%% more experience from experience orbs", 50);

    private final String text;
    private final int cap;

    EnchantingEffect(String text, int cap) {
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
        return this == FRESH_OFFERS || this == TREASURE_OFFERS || this == NO_PRIOR_PENALTY;
    }

    @Override
    public String describe(double value) {
        return SkillText.describe(text, feat(), value);
    }
}
