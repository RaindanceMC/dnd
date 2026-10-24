package de.raindancer.modules.dnd.model;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** A class's second path. A class with one is given it at its level; a sorcerer chooses their origin. */
public enum Subclass {
    CHAMPION("Champion", HeroClass.FIGHTER, 3, Material.GOLDEN_SWORD,
            "Raw physical power: more critical hits, and a body that refuses to fall."),
    BERSERKER("Path of the Berserker", HeroClass.BARBARIAN, 3, Material.NETHERITE_AXE,
            "Rage becomes frenzy: faster blows, fearless, and you strike back when struck."),
    THIEF("Thief", HeroClass.ROGUE, 3, Material.LEAD,
            "Quick hands and quicker feet: shorter waits, higher jumps, and scrolls of any kind."),
    EVOCATION("School of Evocation", HeroClass.WIZARD, 2, Material.FIRE_CHARGE,
            "Spells that hurt, shaped around your friends, and stronger than anyone's."),
    WILD_MAGIC("Wild Magic", HeroClass.SORCERER, 1, Material.CHORUS_FLOWER,
            "Chaos in the blood: any spell may set off a wild surge, for better or worse."),
    DRACONIC("Draconic Bloodline", HeroClass.SORCERER, 1, Material.DRAGON_HEAD,
            "A red dragon's blood: tougher skin, fiercer fire, and in time, wings."),
    LIFE("Life Domain", HeroClass.CLERIC, 1, Material.GOLDEN_APPLE,
            "The healer's path: every healing spell heals more, and heals you too.");

    private final String title;
    private final HeroClass heroClass;
    private final int level;
    private final Material icon;
    private final String summary;

    Subclass(String title, HeroClass heroClass, int level, Material icon, String summary) {
        this.title = title;
        this.heroClass = heroClass;
        this.level = level;
        this.icon = icon;
        this.summary = summary;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String title() {
        return title;
    }

    public HeroClass heroClass() {
        return heroClass;
    }

    /** The class level it is gained or chosen at. */
    public int level() {
        return level;
    }

    public Material icon() {
        return icon;
    }

    public String summary() {
        return summary;
    }

    public static List<Subclass> of(HeroClass heroClass) {
        List<Subclass> found = new ArrayList<>();
        for (Subclass subclass : values()) {
            if (subclass.heroClass == heroClass) {
                found.add(subclass);
            }
        }
        return found;
    }

    public static Optional<Subclass> byId(String id) {
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
