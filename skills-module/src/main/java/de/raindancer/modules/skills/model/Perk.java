package de.raindancer.modules.skills.model;

import java.util.Locale;
import java.util.Optional;

/**
 * What a node of the five fighting skills' trees — Defense, Melee, Archery, Agility, Magic — does, per rank. Ranks of one effect add up, to its {@link #cap()}.
 *
 * <p>An effect with an {@link #attribute()} is the game's own: it is lent to the player as an attribute modifier
 * (value × {@link #scale()}, added, or multiplied when {@link #multiplies()}). The others are worked in this module's
 * listeners: damage dealt and taken, dodging, arrows, experience, loot, magic items, the feats — among them every
 * branch's mastery feature.
 */
public enum Perk implements Effect {

    HEARTS("+%s heart(s)", 20, "max_health", 2.0, false),
    ARMOUR("+%s armour", 20, "armor", 1.0, false),
    TOUGHNESS("+%s armour toughness", 12, "armor_toughness", 1.0, false),
    KNOCKBACK("%s%% less knockback", 100, "knockback_resistance", 0.01, false),
    ATTACK_SPEED("%s%% faster attacks", 60, "attack_speed", 0.01, true),
    REACH("+%s block(s) of reach", 2, "entity_interaction_range", 1.0, false),
    SPEED("%s%% faster on foot", 40, "movement_speed", 0.01, true),
    SAFE_FALL("Falls of %s more block(s) do not hurt", 12, "safe_fall_distance", 1.0, false),
    JUMP("%s%% higher jumps", 50, "jump_strength", 0.01, true),
    STEP("Step up %s block(s) higher", 1, "step_height", 1.0, false),
    LUCK("+%s luck", 4, "luck", 1.0, false),
    MINING_SPEED("%s%% faster mining", 60, "block_break_speed", 0.01, true),
    SWIM("%s%% faster in water", 100, "water_movement_efficiency", 0.01, false),
    BREATH("%s%% longer breath under water", 300, "oxygen_bonus", 0.01, false),
    SNEAK_SPEED("%s%% faster while sneaking", 100, "sneaking_speed", 0.01, true),

    HUNGER_SAVE("%s%% chance hunger does not go down", 75),
    REGEN("Heal %s half-heart(s) every five seconds out of a fight", 6),
    DAMAGE_TAKEN("%s%% less damage taken", 40),
    MELEE_DAMAGE("%s%% more melee damage", 0),
    CRIT_DAMAGE("%s%% more damage on a critical hit", 0),
    SWEEP_DAMAGE("%s%% more damage from sweeping blows", 0),
    LIFESTEAL("Heal %s%% of the melee damage you deal", 15),
    TWO_HANDED("%s%% more melee damage with nothing in your off hand", 50),
    RAGE("%s%% more melee damage below half health", 60),
    UNDEAD_DAMAGE("%s%% more damage to the undead", 0),
    RANGED_DAMAGE("%s%% more arrow damage", 0),
    LONG_SHOT("%s%% more arrow damage from 20 blocks away and further", 0),
    ARROW_SAVE("%s%% chance an arrow is not used up", 75),
    VOLLEY("%s%% chance a shot looses a second arrow", 50),
    DODGE("%s%% chance to dodge a blow or an arrow", 30),
    BACKSTAB("%s%% more damage striking from behind", 0),
    XP("%s%% more adventurer experience", 0),
    VANILLA_XP("%s%% more experience from orbs", 0),
    LOOT("Magic items drop %s%% more often", 0),
    WAND_RECHARGE("Wands recharge %s%% faster", 100),
    SCROLL_SAVE("%s%% chance a scroll is not used up", 50),
    SCROLL_POWER("Scrolls and wands are %s%% stronger", 0),
    POTION_POWER("Magic potions heal %s%% more", 0),

    THORNS("A foe's blow hurts it back for %s%% of what it did", 50),
    RIPOSTE("%s%% more damage on your next blow after your shield stopped one", 0),
    FIRE_WARD("%s%% less damage from fire and lava", 80),
    BLAST_WARD("%s%% less damage from explosions", 80),
    ARROW_WARD("%s%% less damage from arrows and other missiles", 60),
    MAGIC_WARD("%s%% less damage from magic, poison and wither", 60),
    SWORD_DAMAGE("%s%% more damage with a sword", 0),
    AXE_DAMAGE("%s%% more damage with an axe", 0),
    BLEED("%s%% chance a blow makes the foe bleed two hearts away", 50),
    STUN("%s%% chance a critical blow stuns the foe for two seconds", 50),
    MOMENTUM("%s%% more melee damage for every kill in a row, up to five", 10),
    HEADSHOT("%s%% more arrow damage on a hit to the head", 0),
    CLOSE_SHOT("%s%% more arrow damage from 6 blocks away or nearer", 0),
    VELOCITY("Arrows fly %s%% faster — and so hit harder", 50),
    IGNITE("%s%% chance an arrow sets its target alight", 100),
    PIN("%s%% chance an arrow pins its target in place for two seconds", 60),
    FALL_DAMAGE("%s%% less damage from falling", 80),
    VENOM("%s%% chance a blow poisons the foe", 50),
    SNEAK_ATTACK("%s%% more melee damage while sneaking", 0),
    POTION_DURATION("Potions you drink last %s%% longer", 100),
    EFFECT_SHED("Harmful effects on you wear off %s%% sooner", 60),
    MANA_SHIELD("%s%% of a hurt is paid with experience points instead, two a half-heart", 50),
    HEX("%s%% chance a blow or an arrow weakens the foe", 50),

    DUAL_WIELD("Dual Wielder: a weapon in your off hand strikes too, for half its damage", 1),
    SECOND_WIND("Second Wind: below a third of your health, heal a third — once every two minutes", 1),
    UNDYING("Undying: a blow that would kill you leaves you one heart — once every ten minutes", 1),

    PURE_BLOOD("Pure Blood: poison and wither do you no harm", 1),
    AEGIS("Aegis: your shield never wears out, and a blow it stops throws the striker back", 1),
    FIREPROOF("Fireproof: you never catch fire — flames and lava hurt only while you stand in them", 1),
    EXECUTE("Executioner: a blow that leaves a monster below a fifth of its health finishes it — not a boss", 1),
    SUNDER("Sunder: your blows hit 1% harder for every point of the foe's armour", 1),
    BLOODLUST("Bloodlust: every kill heals you two hearts", 1),
    RETRIEVAL("Retrieval: an arrow that hits comes back to your quiver", 1),
    SKEWER("Skewer: every arrow flies on through two more foes", 1),
    HUNTERS_MARK("Hunter's Mark: a foe your arrow hits glows for ten seconds and takes 15% more from all you do", 1),
    VANISH("Vanish: dodging a blow turns you invisible for three seconds", 1),
    DASH("Dash: crouch while sprinting to dash ahead — once every four seconds", 1),
    SMOKE_BOMB("Smoke Bomb: below a quarter of your health you vanish in smoke and foes lose you — once a minute", 1),
    ARCANE_SURGE("Arcane Surge: Magic Missile — from a scroll or a wand — looses twice the darts", 1),
    ARCANE_BARRIER("Arcane Barrier: out of a fight, a barrier of four hearts gathers around you", 1),
    SPELLSTRIKE("Spellstrike: a critical blow also strikes for 1d4+1 force", 1);

    private final String text;
    private final int cap;
    private final String attribute;
    private final double scale;
    private final boolean multiplies;

    Perk(String text, int cap) {
        this(text, cap, null, 0, false);
    }

    Perk(String text, int cap, String attribute, double scale, boolean multiplies) {
        this.text = text;
        this.cap = cap;
        this.attribute = attribute;
        this.scale = scale;
        this.multiplies = multiplies;
    }

    @Override
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The most all ranks together may add up to; 0 for no cap. A feat's cap is 1: had or not. */
    @Override
    public int cap() {
        return cap;
    }

    /** A feat: had or not, and no number in its line. */
    public boolean feat() {
        return !text.contains("%s");
    }

    /** The game's attribute this one lends, by its key ({@code max_health}), or {@code null}. */
    public String attribute() {
        return attribute;
    }

    /** What one point of the value is worth on the attribute. */
    public double scale() {
        return scale;
    }

    /** Lent as a multiplier of the attribute's base rather than added to it. */
    public boolean multiplies() {
        return multiplies;
    }

    /** "+2 hearts", "12% more melee damage", "Dual Wielder: …". */
    @Override
    public String describe(double value) {
        if (feat()) {
            return text;
        }
        String number = value == Math.rint(value) ? String.valueOf(Math.round(value)) : String.valueOf(value);
        return String.format(Locale.ROOT, text, number);
    }

    public static Optional<Perk> parse(String id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(id.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    public static boolean known(String id) {
        return parse(id).isPresent();
    }
}
