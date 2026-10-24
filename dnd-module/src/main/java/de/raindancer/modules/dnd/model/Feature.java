package de.raindancer.modules.dnd.model;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What a class can do beside casting. An active one sits on the casting stance's hotbar like a spell; a passive one
 * simply applies from the level it is gained.
 */
public enum Feature implements Castable {

    SECOND_WIND("Second Wind", HeroClass.FIGHTER, 1, true, Recharge.SHORT_REST, Material.LIGHT_GRAY_DYE,
            "Catch your breath: heal 1d10 plus your fighter level.", true),
    FIGHTING_STYLE("Fighting Style: Defense", HeroClass.FIGHTER, 1, false, Recharge.NONE, Material.IRON_CHESTPLATE,
            "Two more armour while you wear a chestplate.", false),
    ACTION_SURGE("Action Surge", HeroClass.FIGHTER, 2, true, Recharge.SHORT_REST, Material.GOLD_INGOT,
            "Every wait is over, and for six seconds you move and swing faster and hit harder.", false),
    EXTRA_ATTACK("Extra Attack", HeroClass.FIGHTER, 5, false, Recharge.NONE, Material.DIAMOND_SWORD,
            "Your melee blows hit harder: more at levels 11 and 20.", false),
    INDOMITABLE("Indomitable", HeroClass.FIGHTER, 9, false, Recharge.LONG_REST, Material.OBSIDIAN,
            "A failed saving throw is rolled again, once per long rest.", false),

    RAGE("Rage", HeroClass.BARBARIAN, 1, true, Recharge.LONG_REST, Material.RED_DYE,
            "For a minute your melee blows hit harder and every blow against you does half.", true),
    UNARMORED_DEFENSE("Unarmored Defense", HeroClass.BARBARIAN, 1, false, Recharge.NONE, Material.LEATHER_CHESTPLATE,
            "Without a chestplate your toughness is your armour.", false),
    RECKLESS_ATTACK("Reckless Attack", HeroClass.BARBARIAN, 2, true, Recharge.NONE, Material.IRON_NUGGET,
            "Throw caution aside: eight seconds of much stronger blows, while those against you land harder.", true),
    FAST_MOVEMENT("Fast Movement", HeroClass.BARBARIAN, 5, false, Recharge.NONE, Material.LEATHER_BOOTS,
            "You run a tenth faster.", false),

    SNEAK_ATTACK("Sneak Attack", HeroClass.ROGUE, 1, false, Recharge.NONE, Material.IRON_SWORD,
            "Once a second and a half, extra dice on a melee hit while sneaking, unseen, or on a foe busy with someone else.", false),
    CUNNING_DASH("Cunning Action: Dash", HeroClass.ROGUE, 2, true, Recharge.NONE, Material.SUGAR,
            "A burst of speed for four seconds.", true),
    CUNNING_HIDE("Cunning Action: Hide", HeroClass.ROGUE, 2, true, Recharge.NONE, Material.GRAY_DYE,
            "Vanish for six seconds; whatever was hunting you loses you.", true),
    CUNNING_DISENGAGE("Cunning Action: Disengage", HeroClass.ROGUE, 2, true, Recharge.NONE, Material.FEATHER,
            "Leap back out of reach.", true),
    UNCANNY_DODGE("Uncanny Dodge", HeroClass.ROGUE, 5, false, Recharge.NONE, Material.LEATHER_HELMET,
            "Every six seconds, the first blow that lands on you does half.", false),
    EVASION("Evasion", HeroClass.ROGUE, 7, false, Recharge.NONE, Material.RABBIT_FOOT,
            "Explosions and area spells do half to you, and nothing when you make the save.", false),

    ARCANE_RECOVERY("Arcane Recovery", HeroClass.WIZARD, 1, true, Recharge.LONG_REST, Material.LAPIS_LAZULI,
            "Win back spent spell slots worth half your wizard level, once per long rest.", false),

    FONT_OF_MAGIC("Font of Magic", HeroClass.SORCERER, 2, false, Recharge.LONG_REST, Material.AMETHYST_CLUSTER,
            "Sorcery points equal to your level, turned into spell slots and back on your character sheet.", false),
    METAMAGIC("Metamagic", HeroClass.SORCERER, 3, false, Recharge.NONE, Material.BREWING_STAND,
            "Bend a spell with sorcery points: the ninth stance slot picks how.", false),

    DISCIPLE_OF_LIFE("Disciple of Life", HeroClass.CLERIC, 1, false, Recharge.NONE, Material.GOLDEN_APPLE,
            "Your healing spells heal 2 more, and one more per slot level.", false),
    TURN_UNDEAD("Channel Divinity: Turn Undead", HeroClass.CLERIC, 2, true, Recharge.SHORT_REST, Material.BONE_MEAL,
            "Undead near you must make a Wisdom save or flee, weakened; from level 5 the weakest are destroyed.", false),
    PRESERVE_LIFE("Channel Divinity: Preserve Life", HeroClass.CLERIC, 2, true, Recharge.SHORT_REST,
            Material.GLISTERING_MELON_SLICE,
            "Five times your level in healing, shared among the players near you, up to half their health each.", false);

    private final String title;
    private final HeroClass heroClass;
    private final int level;
    private final boolean active;
    private final Recharge recharge;
    private final Material icon;
    private final String summary;
    private final boolean bonusAction;

    Feature(String title, HeroClass heroClass, int level, boolean active, Recharge recharge, Material icon,
            String summary, boolean bonusAction) {
        this.title = title;
        this.heroClass = heroClass;
        this.level = level;
        this.active = active;
        this.recharge = recharge;
        this.icon = icon;
        this.summary = summary;
        this.bonusAction = bonusAction;
    }

    @Override
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String title() {
        return title;
    }

    public HeroClass heroClass() {
        return heroClass;
    }

    /** The class level it is gained at. */
    public int level() {
        return level;
    }

    /** Whether it is used like a spell, from the stance. */
    public boolean active() {
        return active;
    }

    public Recharge recharge() {
        return recharge;
    }

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public String summary() {
        return summary;
    }

    @Override
    public boolean bonusAction() {
        return bonusAction;
    }

    /** The two Channel Divinity options draw on one pool of uses. */
    public Feature pool() {
        return this == PRESERVE_LIFE ? TURN_UNDEAD : this;
    }

    public boolean has(HeroClass someone, int atLevel) {
        return someone == heroClass && atLevel >= level;
    }

    public static List<Feature> of(HeroClass heroClass, int atLevel) {
        List<Feature> found = new ArrayList<>();
        for (Feature feature : values()) {
            if (feature.has(heroClass, atLevel)) {
                found.add(feature);
            }
        }
        return found;
    }

    public static Optional<Feature> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(id.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}
