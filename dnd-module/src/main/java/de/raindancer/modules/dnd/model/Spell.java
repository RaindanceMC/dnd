package de.raindancer.modules.dnd.model;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Every spell there is, after the System Reference Document 5.1 (CC BY 4.0), with ranges turned into blocks
 * (five feet is a block and a half) and the long-lasting ones shortened to what matters in a fight.
 */
public enum Spell implements Castable {

    // ---- cantrips: grow at character levels 5, 11 and 17
    FIRE_BOLT("Fire Bolt", 0, Lists.ARCANE, SpellShape.BOLT, Material.BLAZE_POWDER,
            "A mote of fire hurled at a creature; sets it alight.",
            spec().damage(1, 10).kind(DamageKind.FIRE).range(36)),
    RAY_OF_FROST("Ray of Frost", 0, Lists.ARCANE, SpellShape.BOLT, Material.PRISMARINE_CRYSTALS,
            "A frigid beam that slows what it hits.",
            spec().damage(1, 8).kind(DamageKind.COLD).range(18).lasts(4)),
    SHOCKING_GRASP("Shocking Grasp", 0, Lists.ARCANE, SpellShape.TOUCH, Material.GLOWSTONE_DUST,
            "Lightning from your hand into a creature within reach; it is knocked back.",
            spec().damage(1, 8).kind(DamageKind.LIGHTNING).range(4)),
    SACRED_FLAME("Sacred Flame", 0, Lists.DIVINE, SpellShape.TARGET_SAVE, Material.GOLD_NUGGET,
            "Radiance descends on a creature you can see. Dexterity save; no cover helps.",
            spec().save(Ability.DEXTERITY).damage(1, 8).kind(DamageKind.RADIANT).range(18).none()),

    // ---- 1st level
    MAGIC_MISSILE("Magic Missile", 1, Lists.ARCANE, SpellShape.MISSILES, Material.AMETHYST_SHARD,
            "Three glowing darts that never miss, 1d4+1 each. One more dart per slot level above 1st.",
            spec().damage(1, 4, 1).kind(DamageKind.FORCE).range(36).count(3)),
    BURNING_HANDS("Burning Hands", 1, Lists.ARCANE, SpellShape.CONE, Material.MAGMA_CREAM,
            "A sheet of flame from your fingertips. Dexterity save for half.",
            spec().save(Ability.DEXTERITY).damage(3, 6).upcast(1, 6).kind(DamageKind.FIRE).range(5)),
    THUNDERWAVE("Thunderwave", 1, Lists.ARCANE, SpellShape.BURST_SELF, Material.FIREWORK_STAR,
            "A wave of thunder around you. Constitution save for half and no push.",
            spec().save(Ability.CONSTITUTION).damage(2, 8).upcast(1, 8).kind(DamageKind.THUNDER).radius(3.5)),
    SHIELD("Shield", 1, Lists.ARCANE, SpellShape.BUFF_SELF, Material.TURTLE_SCUTE,
            "An invisible barrier: for six seconds every blow against you is softened.",
            spec().lasts(6).bonus()),
    MAGE_ARMOR("Mage Armor", 1, Lists.ARCANE, SpellShape.BUFF_SELF, Material.LEATHER,
            "A protective force around you until your next long rest: three more armour.",
            spec().lasts(8 * 3600)),
    EXPEDITIOUS_RETREAT("Expeditious Retreat", 1, Lists.ARCANE, SpellShape.BUFF_SELF, Material.SUGAR,
            "You move at incredible speed for a minute. Concentration.",
            spec().lasts(60).concentration().bonus()),
    FEATHER_FALL("Feather Fall", 1, Lists.ARCANE, SpellShape.BUFF_SELF, Material.FEATHER,
            "You and the players around you sink gently for a minute.",
            spec().lasts(60).radius(6).bonus()),
    JUMP("Jump", 1, Lists.ARCANE, SpellShape.BUFF_SELF, Material.RABBIT_FOOT,
            "Your jumps are tripled in height for a minute.",
            spec().lasts(60)),
    CURE_WOUNDS("Cure Wounds", 1, Lists.DIVINE, SpellShape.HEAL, Material.GLISTERING_MELON_SLICE,
            "Heal a player within reach, or yourself, for 1d8 and your modifier.",
            spec().damage(1, 8).upcast(1, 8).kind(DamageKind.HEALING).range(4)),
    HEALING_WORD("Healing Word", 1, Lists.DIVINE, SpellShape.HEAL, Material.HONEYCOMB,
            "A word of power heals a player you can see, or yourself, for 1d4 and your modifier.",
            spec().damage(1, 4).upcast(1, 4).kind(DamageKind.HEALING).range(18).bonus()),
    GUIDING_BOLT("Guiding Bolt", 1, Lists.DIVINE, SpellShape.BOLT, Material.GLOW_INK_SAC,
            "A flash of light; the target glows so everyone can find it.",
            spec().damage(4, 6).upcast(1, 6).kind(DamageKind.RADIANT).range(36).lasts(6)),
    BLESS("Bless", 1, Lists.DIVINE, SpellShape.BUFF_GROUP, Material.WHITE_DYE,
            "You and up to two players near you strike harder for a minute. Concentration.",
            spec().lasts(60).radius(9).count(3).concentration()),
    SHIELD_OF_FAITH("Shield of Faith", 1, Lists.DIVINE, SpellShape.BUFF_ONE, Material.IRON_INGOT,
            "A shimmering field around a player or yourself: blows land softer. Concentration.",
            spec().lasts(60).range(18).concentration().bonus()),
    INFLICT_WOUNDS("Inflict Wounds", 1, Lists.DIVINE, SpellShape.TOUCH, Material.FERMENTED_SPIDER_EYE,
            "A touch filled with necrotic energy.",
            spec().damage(3, 10).upcast(1, 10).kind(DamageKind.NECROTIC).range(4)),

    // ---- 2nd level
    SCORCHING_RAY("Scorching Ray", 2, Lists.ARCANE, SpellShape.BOLT, Material.NETHER_BRICK,
            "Three rays of fire, each its own attack. One more ray per slot level above 2nd.",
            spec().damage(2, 6).kind(DamageKind.FIRE).range(36).count(3)),
    MISTY_STEP("Misty Step", 2, Lists.ARCANE, SpellShape.TELEPORT, Material.POPPED_CHORUS_FRUIT,
            "Silver mist, and you are up to nine blocks away where you were looking.",
            spec().range(9).bonus()),
    SHATTER("Shatter", 2, Lists.ARCANE, SpellShape.BURST_AT, Material.QUARTZ,
            "A painful ringing at a point you choose. Constitution save for half.",
            spec().save(Ability.CONSTITUTION).damage(3, 8).upcast(1, 8).kind(DamageKind.THUNDER).range(18)
                    .radius(3)),
    HOLD_PERSON("Hold Person", 2, Lists.BOTH, SpellShape.TARGET_SAVE, Material.STRING,
            "A humanoid you can see is paralysed for a while. Wisdom save. Concentration.",
            spec().save(Ability.WISDOM).range(18).lasts(10).concentration().none()),
    INVISIBILITY("Invisibility", 2, Lists.ARCANE, SpellShape.BUFF_SELF, Material.GRAY_DYE,
            "You vanish for a minute, until you attack or cast. Concentration.",
            spec().lasts(60).concentration()),
    LESSER_RESTORATION("Lesser Restoration", 2, Lists.DIVINE, SpellShape.CLEANSE, Material.LIGHT_BLUE_DYE,
            "Ends poison, blindness, nausea and darkness on a player or yourself.",
            spec().range(4).bonus()),
    AID("Aid", 2, Lists.DIVINE, SpellShape.BUFF_GROUP, Material.RABBIT_HIDE,
            "You and up to two players near you gain five hit points, and that much more maximum, until a long rest.",
            spec().damage(0, 0, 5).upcast(0, 0, 5).lasts(8 * 3600).radius(9).count(3)),
    PRAYER_OF_HEALING("Prayer of Healing", 2, Lists.DIVINE, SpellShape.HEAL_GROUP, Material.PINK_DYE,
            "Up to six players near you, yourself included, heal 2d8 and your modifier.",
            spec().damage(2, 8).upcast(1, 8).kind(DamageKind.HEALING).radius(9).count(6)),

    // ---- 3rd level
    FIREBALL("Fireball", 3, Lists.ARCANE, SpellShape.BURST_AT, Material.FIRE_CHARGE,
            "A bead of flame that blossoms into an explosion. Dexterity save for half.",
            spec().save(Ability.DEXTERITY).damage(8, 6).upcast(1, 6).kind(DamageKind.FIRE).range(36).radius(6)),
    LIGHTNING_BOLT("Lightning Bolt", 3, Lists.ARCANE, SpellShape.LINE, Material.YELLOW_DYE,
            "A stroke of lightning thirty blocks long. Dexterity save for half.",
            spec().save(Ability.DEXTERITY).damage(8, 6).upcast(1, 6).kind(DamageKind.LIGHTNING).range(30)
                    .radius(1.5)),
    HASTE("Haste", 3, Lists.ARCANE, SpellShape.BUFF_ONE, Material.CLOCK,
            "A player or you moves and swings faster, with more armour; a wave of lethargy follows. Concentration.",
            spec().lasts(60).range(18).concentration()),
    SPIRIT_GUARDIANS("Spirit Guardians", 3, Lists.DIVINE, SpellShape.AURA, Material.NETHER_STAR,
            "Spirits circle you, hurting and slowing every hostile creature near you. Wisdom save for half. Concentration.",
            spec().save(Ability.WISDOM).damage(3, 8).upcast(1, 8).kind(DamageKind.RADIANT).radius(5).lasts(60)
                    .concentration()),
    MASS_HEALING_WORD("Mass Healing Word", 3, Lists.DIVINE, SpellShape.HEAL_GROUP, Material.MAGENTA_DYE,
            "Up to six players you can see heal 1d4 and your modifier.",
            spec().damage(1, 4).upcast(1, 4).kind(DamageKind.HEALING).radius(18).count(6).bonus()),

    // ---- 4th level
    ICE_STORM("Ice Storm", 4, Lists.ARCANE, SpellShape.BURST_AT, Material.BLUE_DYE,
            "Hail and cold pound a circle and slow those inside. Dexterity save for half.",
            spec().save(Ability.DEXTERITY).damage(2, 8).extra(4, 6).upcast(1, 8).kind(DamageKind.COLD).range(36)
                    .radius(6).lasts(6)),
    GREATER_INVISIBILITY("Greater Invisibility", 4, Lists.ARCANE, SpellShape.BUFF_SELF, Material.PHANTOM_MEMBRANE,
            "You vanish for a minute, and attacking does not end it. Concentration.",
            spec().lasts(60).concentration()),
    DIMENSION_DOOR("Dimension Door", 4, Lists.ARCANE, SpellShape.TELEPORT, Material.ENDER_EYE,
            "You step through to where you look, up to sixty-four blocks away.",
            spec().range(64)),
    DEATH_WARD("Death Ward", 4, Lists.DIVINE, SpellShape.BUFF_ONE, Material.BONE,
            "The first blow that would drop a player or you leaves them on one hit point instead.",
            spec().lasts(8 * 3600).range(4)),

    // ---- 5th level
    CONE_OF_COLD("Cone of Cold", 5, Lists.ARCANE, SpellShape.CONE, Material.CYAN_DYE,
            "A blast of cold air eighteen blocks long. Constitution save for half.",
            spec().save(Ability.CONSTITUTION).damage(8, 8).upcast(1, 8).kind(DamageKind.COLD).range(18)),
    MASS_CURE_WOUNDS("Mass Cure Wounds", 5, Lists.DIVINE, SpellShape.HEAL_GROUP, Material.LIME_DYE,
            "Up to six players near you heal 3d8 and your modifier.",
            spec().damage(3, 8).upcast(1, 8).kind(DamageKind.HEALING).radius(9).count(6)),
    FLAME_STRIKE("Flame Strike", 5, Lists.DIVINE, SpellShape.BURST_AT, Material.BLAZE_ROD,
            "A column of divine fire where you look. Dexterity save for half.",
            spec().save(Ability.DEXTERITY).damage(4, 6).extra(4, 6).upcast(1, 6).kind(DamageKind.FIRE).range(18)
                    .radius(3)),
    GREATER_RESTORATION("Greater Restoration", 5, Lists.DIVINE, SpellShape.CLEANSE, Material.HEART_OF_THE_SEA,
            "Ends every harmful effect on a player or yourself, withering and weakness included.",
            spec().range(4)),

    // ---- 6th level
    CHAIN_LIGHTNING("Chain Lightning", 6, Lists.ARCANE, SpellShape.CHAIN, Material.COPPER_INGOT,
            "A bolt that leaps to three more targets. Dexterity save for half.",
            spec().save(Ability.DEXTERITY).damage(10, 8).kind(DamageKind.LIGHTNING).range(36).radius(9).count(4)),
    DISINTEGRATE("Disintegrate", 6, Lists.ARCANE, SpellShape.TARGET_SAVE, Material.GUNPOWDER,
            "A thin green ray. Dexterity save or take 10d6+40; nothing on a success.",
            spec().save(Ability.DEXTERITY).damage(10, 6, 40).upcast(3, 6).kind(DamageKind.FORCE).range(36).none()),
    HEAL("Heal", 6, Lists.DIVINE, SpellShape.HEAL, Material.NAUTILUS_SHELL,
            "Seventy hit points to a player you can see or yourself, and every harmful effect ends.",
            spec().damage(0, 0, 70).upcast(0, 0, 10).kind(DamageKind.HEALING).range(18)),
    HARM("Harm", 6, Lists.DIVINE, SpellShape.TARGET_SAVE, Material.INK_SAC,
            "A virulent disease. Constitution save for half; it never drops a creature below one hit point.",
            spec().save(Ability.CONSTITUTION).damage(14, 6).kind(DamageKind.NECROTIC).range(18)),

    // ---- 7th level
    FINGER_OF_DEATH("Finger of Death", 7, Lists.ARCANE, SpellShape.TARGET_SAVE, Material.BLACK_DYE,
            "Negative energy tears at a creature. Constitution save for half.",
            spec().save(Ability.CONSTITUTION).damage(7, 8, 30).kind(DamageKind.NECROTIC).range(18)),
    FIRE_STORM("Fire Storm", 7, Lists.BOTH, SpellShape.BURST_AT, Material.ORANGE_DYE,
            "A storm of roaring flame. Dexterity save for half.",
            spec().save(Ability.DEXTERITY).damage(7, 10).kind(DamageKind.FIRE).range(36).radius(7)),
    REGENERATE("Regenerate", 7, Lists.DIVINE, SpellShape.HEAL, Material.GHAST_TEAR,
            "4d8+15 hit points to a player within reach or yourself, and regeneration for a minute.",
            spec().damage(4, 8, 15).kind(DamageKind.HEALING).range(4).lasts(60)),

    // ---- 8th level
    SUNBURST("Sunburst", 8, Lists.ARCANE, SpellShape.BURST_AT, Material.RAW_GOLD,
            "Brilliant sunlight in a wide circle; those who fail are blinded. Constitution save for half.",
            spec().save(Ability.CONSTITUTION).damage(12, 6).kind(DamageKind.RADIANT).range(36).radius(12).lasts(6)),
    HOLY_AURA("Holy Aura", 8, Lists.DIVINE, SpellShape.BUFF_GROUP, Material.EMERALD,
            "Divine light around you and every player near you: blows land softer. Concentration.",
            spec().lasts(60).radius(9).count(12).concentration()),

    // ---- 9th level
    METEOR_SWARM("Meteor Swarm", 9, Lists.ARCANE, SpellShape.BURST_AT, Material.NETHERITE_SCRAP,
            "Four blazing meteors around the point you look at. Dexterity save for half.",
            spec().save(Ability.DEXTERITY).damage(20, 6).extra(20, 6).kind(DamageKind.FIRE).range(48).radius(6)
                    .count(4)),
    POWER_WORD_KILL("Power Word Kill", 9, Lists.ARCANE, SpellShape.TARGET_SAVE, Material.ECHO_SHARD,
            "A creature you can see with 100 hit points or fewer dies. No saving throw.",
            spec().damage(0, 0, 100).kind(DamageKind.NONE).range(36).none()),
    MASS_HEAL("Mass Heal", 9, Lists.DIVINE, SpellShape.HEAL_GROUP, Material.DIAMOND,
            "Seven hundred hit points shared among the players around you; every harmful effect ends.",
            spec().damage(0, 0, 700).kind(DamageKind.HEALING).radius(18).count(12));

    /** Who learns what. */
    private static final class Lists {
        static final Set<HeroClass> ARCANE = Set.of(HeroClass.WIZARD, HeroClass.SORCERER);
        static final Set<HeroClass> DIVINE = Set.of(HeroClass.CLERIC);
        static final Set<HeroClass> BOTH = Set.of(HeroClass.WIZARD, HeroClass.SORCERER, HeroClass.CLERIC);
    }

    /** What happens when a save succeeds. */
    public enum OnSave {
        HALF, NONE
    }

    /** Everything about a spell beside its name, built one value at a time. */
    public static final class Spec {
        Ability save;
        Dice damage = Dice.NONE;
        Dice extra = Dice.NONE;
        Dice upcast = Dice.NONE;
        DamageKind kind = DamageKind.NONE;
        int range;
        double radius;
        int count = 1;
        int seconds;
        boolean concentration;
        boolean bonus;
        OnSave onSave = OnSave.HALF;

        Spec save(Ability ability) {
            this.save = ability;
            return this;
        }

        Spec damage(int count, int sides) {
            return damage(count, sides, 0);
        }

        Spec damage(int count, int sides, int bonus) {
            this.damage = new Dice(count, sides, bonus);
            return this;
        }

        Spec extra(int count, int sides) {
            this.extra = Dice.of(count, sides);
            return this;
        }

        Spec upcast(int count, int sides) {
            return upcast(count, sides, 0);
        }

        Spec upcast(int count, int sides, int bonus) {
            this.upcast = new Dice(count, sides, bonus);
            return this;
        }

        Spec kind(DamageKind kind) {
            this.kind = kind;
            return this;
        }

        Spec range(int blocks) {
            this.range = blocks;
            return this;
        }

        Spec radius(double blocks) {
            this.radius = blocks;
            return this;
        }

        Spec count(int count) {
            this.count = count;
            return this;
        }

        Spec lasts(int seconds) {
            this.seconds = seconds;
            return this;
        }

        Spec concentration() {
            this.concentration = true;
            return this;
        }

        Spec bonus() {
            this.bonus = true;
            return this;
        }

        Spec none() {
            this.onSave = OnSave.NONE;
            return this;
        }
    }

    private static Spec spec() {
        return new Spec();
    }

    private final String title;
    private final int level;
    private final Set<HeroClass> classes;
    private final SpellShape shape;
    private final Material icon;
    private final String summary;
    private final Spec spec;

    Spell(String title, int level, Set<HeroClass> classes, SpellShape shape, Material icon, String summary,
          Spec spec) {
        this.title = title;
        this.level = level;
        this.classes = classes;
        this.shape = shape;
        this.icon = icon;
        this.summary = summary;
        this.spec = spec;
    }

    @Override
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String title() {
        return title;
    }

    /** 0 for a cantrip. */
    public int level() {
        return level;
    }

    public boolean isCantrip() {
        return level == 0;
    }

    public boolean learnedBy(HeroClass heroClass) {
        return classes.contains(heroClass);
    }

    public SpellShape shape() {
        return shape;
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
        return spec.bonus;
    }

    /** The ability a target saves with, or empty for an attack roll, a heal or a buff. */
    public Optional<Ability> save() {
        return Optional.ofNullable(spec.save);
    }

    public OnSave onSave() {
        return spec.onSave;
    }

    public Dice damage() {
        return spec.damage;
    }

    /** A second kind of damage rolled beside the first: the cold of an ice storm, the radiance of a flame strike. */
    public Dice extra() {
        return spec.extra;
    }

    /** Added once per slot level above the spell's own. */
    public Dice upcast() {
        return spec.upcast;
    }

    public DamageKind kind() {
        return spec.kind;
    }

    public boolean heals() {
        return spec.kind == DamageKind.HEALING;
    }

    public int range() {
        return spec.range;
    }

    public double radius() {
        return spec.radius;
    }

    /** Darts, rays, targets, jumps or meteors, depending on the shape. */
    public int count() {
        return spec.count;
    }

    public int seconds() {
        return spec.seconds;
    }

    public boolean concentration() {
        return spec.concentration;
    }

    /** Whether Twinned Spell can be used on it: it aims at exactly one creature. */
    public boolean singleTarget() {
        return switch (shape) {
            case BOLT, TOUCH, TARGET_SAVE, HEAL, BUFF_ONE, CLEANSE -> spec.count == 1;
            default -> false;
        };
    }

    /** Whether it hurts what it touches. */
    public boolean harmful() {
        return switch (shape) {
            case BOLT, TOUCH, MISSILES, TARGET_SAVE, CONE, BURST_SELF, BURST_AT, LINE, CHAIN, AURA -> true;
            default -> false;
        };
    }

    public static Optional<Spell> byId(String id) {
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
