package de.raindancer.modules.skills.skill.trading;

import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.skill.SkillText;

import java.util.Locale;

/** What a node of the Trading tree does: haggling villagers down, and the merchant's restocks and wanderers. */
public enum TradingEffect implements Effect {

    DISCOUNT("Villagers' prices %s%% lower for you", 40),
    EMERALD_BONUS("%s%% chance a trade for emeralds gives one more", 30),
    SILVER_TONGUE("Silver Tongue: what villagers ask of you never goes up with demand", 1),
    RESTOCK("A villager's trade you used up comes back by itself after 20 minutes, %s%% sooner", 200),
    WANDERER_DISCOUNT("Wandering traders' prices %s%% lower for you", 60),
    WANDERER_STOCK("Wandering traders' trades last %s more use(s) for you", 8),
    MERCHANT_PRINCE("Merchant Prince: villagers learn twice as much from your trades", 1);

    private final String text;
    private final int cap;

    TradingEffect(String text, int cap) {
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
        return this == SILVER_TONGUE || this == MERCHANT_PRINCE;
    }

    @Override
    public String describe(double value) {
        return SkillText.describe(text, feat(), value);
    }
}
