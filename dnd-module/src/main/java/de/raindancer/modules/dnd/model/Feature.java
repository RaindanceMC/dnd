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


    TURN_UNDEAD("Channel Divinity: Turn Undead", HeroClass.CLERIC, 2, true, Recharge.SHORT_REST, Material.BONE_MEAL,
            "Undead near you must make a Wisdom save or flee, weakened; from level 5 the weakest are destroyed.", false),
    PRESERVE_LIFE("Channel Divinity: Preserve Life", Subclass.LIFE, 2, true, Recharge.SHORT_REST,
            Material.GLISTERING_MELON_SLICE,
            "Five times your level in healing, shared among the players near you, up to half their health each.", false),

    // ---- subclasses
    IMPROVED_CRITICAL("Improved Critical", Subclass.CHAMPION, 3, false, Recharge.NONE, Material.GOLDEN_SWORD,
            "One melee blow in ten is a critical hit that does double damage.", false),
    REMARKABLE_ATHLETE("Remarkable Athlete", Subclass.CHAMPION, 7, false, Recharge.NONE, Material.LEATHER_BOOTS,
            "You run a little faster and jump a little higher.", false),
    DUELING("Additional Fighting Style: Dueling", Subclass.CHAMPION, 10, false, Recharge.NONE, Material.IRON_SWORD,
            "Two more damage on every melee blow.", false),
    SUPERIOR_CRITICAL("Superior Critical", Subclass.CHAMPION, 15, false, Recharge.NONE, Material.DIAMOND_SWORD,
            "Three melee blows in twenty are critical hits.", false),
    SURVIVOR("Survivor", Subclass.CHAMPION, 18, false, Recharge.NONE, Material.TOTEM_OF_UNDYING,
            "Below half health you regain health every few seconds.", false),

    FRENZY("Frenzy", Subclass.BERSERKER, 3, false, Recharge.NONE, Material.BLAZE_POWDER,
            "Raging, you swing much faster; when the rage ends you are exhausted for a while.", false),
    MINDLESS_RAGE("Mindless Rage", Subclass.BERSERKER, 6, false, Recharge.NONE, Material.WITHER_ROSE,
            "Raging, nothing blinds, confuses or darkens you.", false),
    INTIMIDATING_PRESENCE("Intimidating Presence", Subclass.BERSERKER, 10, true, Recharge.NONE, Material.SKELETON_SKULL,
            "The creature you glare at makes a Wisdom save or flees, weakened, for half a minute.", false),
    RETALIATION("Retaliation", Subclass.BERSERKER, 14, false, Recharge.NONE, Material.CACTUS,
            "Raging, whatever hits you in melee is hit straight back.", false),

    FAST_HANDS("Fast Hands", Subclass.THIEF, 3, false, Recharge.NONE, Material.LEAD,
            "Cunning Action waits half as long.", false),
    SECOND_STORY_WORK("Second-Story Work", Subclass.THIEF, 3, false, Recharge.NONE, Material.LADDER,
            "You jump higher and fall further unhurt.", false),
    SUPREME_SNEAK("Supreme Sneak", Subclass.THIEF, 9, false, Recharge.NONE, Material.BLACK_DYE,
            "Hiding lasts twice as long, and you move fast while hidden.", false),
    USE_MAGIC_DEVICE("Use Magic Device", Subclass.THIEF, 13, false, Recharge.NONE, Material.PAPER,
            "You can read any spell scroll, whatever class it was written for.", false),
    THIEFS_REFLEXES("Thief's Reflexes", Subclass.THIEF, 17, false, Recharge.NONE, Material.RABBIT_FOOT,
            "Every wait after an action is a quarter shorter.", false),

    SCULPT_SPELLS("Sculpt Spells", Subclass.EVOCATION, 2, false, Recharge.NONE, Material.SHIELD,
            "Your area spells pass harmlessly around other players.", false),
    POTENT_CANTRIP("Potent Cantrip", Subclass.EVOCATION, 6, false, Recharge.NONE, Material.BLAZE_POWDER,
            "A cantrip that misses still does half its damage.", false),
    EMPOWERED_EVOCATION("Empowered Evocation", Subclass.EVOCATION, 10, false, Recharge.NONE, Material.FIRE_CHARGE,
            "Your damaging spells add your Intelligence modifier.", false),
    OVERCHANNEL("Overchannel", Subclass.EVOCATION, 14, true, Recharge.LONG_REST, Material.END_CRYSTAL,
            "Your next 1st- to 5th-level spell deals the most its dice can.", true),

    WILD_MAGIC_SURGE("Wild Magic Surge", Subclass.WILD_MAGIC, 1, false, Recharge.NONE, Material.CHORUS_FLOWER,
            "Every spell you cast from a slot has a small chance to set off a wild magic surge.", false),
    TIDES_OF_CHAOS("Tides of Chaos", Subclass.WILD_MAGIC, 1, true, Recharge.LONG_REST, Material.HEART_OF_THE_SEA,
            "Advantage on your next spell attack. Your next spell from a slot then surges, and Tides return.", true),
    BEND_LUCK("Bend Luck", Subclass.WILD_MAGIC, 6, true, Recharge.NONE, Material.RABBIT_FOOT,
            "Two sorcery points: for six seconds, creatures saving against you subtract a d4.", true),
    CONTROLLED_CHAOS("Controlled Chaos", Subclass.WILD_MAGIC, 14, false, Recharge.NONE, Material.CHORUS_FRUIT,
            "A surge is rolled twice, and the kinder of the two happens.", false),
    SPELL_BOMBARDMENT("Spell Bombardment", Subclass.WILD_MAGIC, 18, false, Recharge.NONE, Material.TNT,
            "Once per spell, a damage die that rolls its highest is rolled again and added.", false),

    DRACONIC_RESILIENCE("Draconic Resilience", Subclass.DRACONIC, 1, false, Recharge.NONE, Material.TURTLE_SCUTE,
            "One more hit point per level, and dragon scales for armour without a chestplate.", false),
    ELEMENTAL_AFFINITY("Elemental Affinity", Subclass.DRACONIC, 6, false, Recharge.NONE, Material.MAGMA_CREAM,
            "Fire spells add your Charisma modifier, and fire does you half.", false),
    DRAGON_WINGS("Dragon Wings", Subclass.DRACONIC, 14, true, Recharge.NONE, Material.ELYTRA,
            "Spread your wings and fly; use it again to fold them.", true),
    DRACONIC_PRESENCE("Draconic Presence", Subclass.DRACONIC, 18, true, Recharge.NONE, Material.DRAGON_HEAD,
            "Five sorcery points: every hostile creature near you makes a Wisdom save or flees in terror.", false),

    DISCIPLE_OF_LIFE("Disciple of Life", Subclass.LIFE, 1, false, Recharge.NONE, Material.GOLDEN_APPLE,
            "Your healing spells heal 2 more, and one more per slot level.", false),
    BLESSED_HEALER("Blessed Healer", Subclass.LIFE, 6, false, Recharge.NONE, Material.GLISTERING_MELON_SLICE,
            "Healing somebody else heals you too: 2 plus the slot level.", false),
    DIVINE_STRIKE("Divine Strike", Subclass.LIFE, 8, false, Recharge.NONE, Material.GOLDEN_SWORD,
            "Once a second and a half, a melee blow adds 1d8 radiant; 2d8 from level 14.", false),
    SUPREME_HEALING("Supreme Healing", Subclass.LIFE, 17, false, Recharge.NONE, Material.ENCHANTED_GOLDEN_APPLE,
            "Your healing dice always roll their highest.", false);

    private final String title;
    private final HeroClass heroClass;
    private final Subclass subclass;
    private final int level;
    private final boolean active;
    private final Recharge recharge;
    private final Material icon;
    private final String summary;
    private final boolean bonusAction;

    Feature(String title, HeroClass heroClass, int level, boolean active, Recharge recharge, Material icon,
            String summary, boolean bonusAction) {
        this(title, heroClass, null, level, active, recharge, icon, summary, bonusAction);
    }

    Feature(String title, Subclass subclass, int level, boolean active, Recharge recharge, Material icon,
            String summary, boolean bonusAction) {
        this(title, subclass.heroClass(), subclass, level, active, recharge, icon, summary, bonusAction);
    }

    Feature(String title, HeroClass heroClass, Subclass subclass, int level, boolean active, Recharge recharge,
            Material icon, String summary, boolean bonusAction) {
        this.title = title;
        this.heroClass = heroClass;
        this.subclass = subclass;
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

    /** The subclass it belongs to, or empty for one every member of the class gets. */
    public java.util.Optional<Subclass> subclass() {
        return java.util.Optional.ofNullable(subclass);
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

    /** Whether the class alone gives it at that level; subclass features never count here. */
    public boolean has(HeroClass someone, int atLevel) {
        return subclass == null && someone == heroClass && atLevel >= level;
    }

    /** Whether this hero has it: their class, their level, and their subclass if it is one. */
    public boolean has(Hero hero) {
        if (hero == null || hero.heroClass() != heroClass || hero.level() < level) {
            return false;
        }
        return subclass == null || subclass == hero.subclass();
    }

    /** Every feature a class alone gives at that level. */
    public static List<Feature> of(HeroClass heroClass, int atLevel) {
        List<Feature> found = new ArrayList<>();
        for (Feature feature : values()) {
            if (feature.has(heroClass, atLevel)) {
                found.add(feature);
            }
        }
        return found;
    }

    /** Every feature this hero has, subclass included. */
    public static List<Feature> of(Hero hero) {
        List<Feature> found = new ArrayList<>();
        for (Feature feature : values()) {
            if (feature.has(hero)) {
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
