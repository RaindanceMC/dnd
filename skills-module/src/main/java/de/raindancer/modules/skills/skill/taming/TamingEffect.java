package de.raindancer.modules.skills.skill.taming;

import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.skill.EffectText;

/** What a node of the Taming tree does, per rank. */
public enum TamingEffect implements Effect {

    QUICK("tame_quick", "%s%% chance an animal trusts you at the first try", 100),
    FANGS("tame_damage", "Your wolves and cats hit %s%% harder", 100),
    HEARTS("tame_hearts", "Your pets have %s more heart(s)", 10),
    GUARD("tame_guard", "Your pets take %s%% less damage", 50),
    MEND("tame_mend", "Your pets near you heal %s half-heart(s) every five seconds", 6),
    HORSE_SPEED("tame_horse_speed", "Horses you ride run %s%% faster", 50),
    HORSE_JUMP("tame_horse_jump", "Horses you ride jump %s%% higher", 60),
    HORSE_GUARD("tame_horse_guard", "Horses you ride take %s%% less damage", 60),
    LOYAL("tame_loyal", "Loyal to the End: a pet of yours that would die keeps a heart and runs to you, once every five minutes", 1),
    WINDRIDER("tame_windrider", "Windrider: horses you ride never take fall damage", 1);

    private final String id;
    private final String text;
    private final int cap;

    TamingEffect(String id, String text, int cap) {
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
