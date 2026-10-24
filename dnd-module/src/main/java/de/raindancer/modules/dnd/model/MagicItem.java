package de.raindancer.modules.dnd.model;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Optional;

/** The magic items adventurers find, after the System Reference Document 5.1. */
public enum MagicItem {
    POTION_OF_HEALING("Potion of Healing", Rarity.COMMON, Material.POTION, new Dice(2, 4, 2),
            "Drink it to regain 2d4+2 hit points."),
    POTION_OF_GREATER_HEALING("Potion of Greater Healing", Rarity.UNCOMMON, Material.POTION, new Dice(4, 4, 4),
            "Drink it to regain 4d4+4 hit points."),
    POTION_OF_SUPERIOR_HEALING("Potion of Superior Healing", Rarity.RARE, Material.POTION, new Dice(8, 4, 8),
            "Drink it to regain 8d4+8 hit points."),
    POTION_OF_SUPREME_HEALING("Potion of Supreme Healing", Rarity.VERY_RARE, Material.POTION, new Dice(10, 4, 20),
            "Drink it to regain 10d4+20 hit points."),
    POTION_OF_HEROISM("Potion of Heroism", Rarity.RARE, Material.POTION, Dice.flat(10),
            "Ten temporary hit points and a minute of blessed strength."),
    POTION_OF_SPEED("Potion of Speed", Rarity.VERY_RARE, Material.POTION, Dice.NONE,
            "A minute of haste: faster legs, faster hands, harder skin."),
    SPELL_SCROLL("Spell Scroll", Rarity.COMMON, Material.PAPER, Dice.NONE,
            "Read it to cast the spell written on it, if it is on your class's list. It crumbles afterwards."),
    WEAPON_PLUS_ONE("+1 Weapon", Rarity.UNCOMMON, Material.IRON_SWORD, Dice.flat(1),
            "One more damage on every blow or arrow."),
    WEAPON_PLUS_TWO("+2 Weapon", Rarity.RARE, Material.DIAMOND_SWORD, Dice.flat(2),
            "Two more damage on every blow or arrow."),
    WEAPON_PLUS_THREE("+3 Weapon", Rarity.VERY_RARE, Material.NETHERITE_SWORD, Dice.flat(3),
            "Three more damage on every blow or arrow."),
    WAND_OF_MAGIC_MISSILES("Wand of Magic Missiles", Rarity.UNCOMMON, Material.BREEZE_ROD, Dice.NONE,
            "Seven charges of Magic Missile, one back every few minutes. Sneak to spend three at once."),
    RING_OF_PROTECTION("Ring of Protection", Rarity.RARE, Material.GOLD_NUGGET, Dice.flat(1),
            "Held in your off hand: more armour, and +1 to every saving throw."),
    AMULET_OF_HEALTH("Amulet of Health", Rarity.RARE, Material.HEART_OF_THE_SEA, Dice.flat(19),
            "Held in your off hand: your Constitution counts as 19."),
    BOOTS_OF_STRIDING("Boots of Striding and Springing", Rarity.UNCOMMON, Material.LEATHER_BOOTS, Dice.NONE,
            "Worn: you walk faster and jump three times as high."),
    BAG_OF_HOLDING("Bag of Holding", Rarity.UNCOMMON, Material.LEATHER, Dice.NONE,
            "Opens onto your ender chest, wherever you are.");

    /** Wand charges: the most it holds, and how long one takes to come back. */
    public static final int WAND_CHARGES = 7;
    public static final long WAND_RECHARGE_MILLIS = 3 * 60_000L;

    private final String title;
    private final Rarity rarity;
    private final Material material;
    private final Dice amount;
    private final String summary;

    MagicItem(String title, Rarity rarity, Material material, Dice amount, String summary) {
        this.title = title;
        this.rarity = rarity;
        this.material = material;
        this.amount = amount;
        this.summary = summary;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String title() {
        return title;
    }

    /** A scroll's rarity is its spell's; see {@link Rarity#ofSpellLevel}. */
    public Rarity rarity() {
        return rarity;
    }

    public Material material() {
        return material;
    }

    /** Healing for a potion, the bonus for a weapon, the score for the amulet. */
    public Dice amount() {
        return amount;
    }

    public String summary() {
        return summary;
    }

    public boolean isPotion() {
        return material == Material.POTION;
    }

    public boolean isWeapon() {
        return this == WEAPON_PLUS_ONE || this == WEAPON_PLUS_TWO || this == WEAPON_PLUS_THREE;
    }

    public static Optional<MagicItem> byId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(id.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}
