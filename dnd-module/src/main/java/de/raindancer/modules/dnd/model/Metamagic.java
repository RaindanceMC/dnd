package de.raindancer.modules.dnd.model;

import org.bukkit.Material;

import java.util.Optional;

/** How a sorcerer bends a spell, paid in sorcery points. */
public enum Metamagic {
    QUICKENED("Quickened Spell", Material.FEATHER, "The spell takes only a bonus action: almost no wait after it."),
    TWINNED("Twinned Spell", Material.ENDER_EYE, "A spell aimed at one creature hits a second one as well."),
    EMPOWERED("Empowered Spell", Material.BLAZE_POWDER, "Damage dice below half are rolled again, once."),
    EXTENDED("Extended Spell", Material.CLOCK, "Anything the spell leaves behind lasts twice as long."),
    HEIGHTENED("Heightened Spell", Material.ENDER_PEARL, "The first target rolls its saving throw twice, keeping the worse."),
    CAREFUL("Careful Spell", Material.SHIELD, "Friendly players caught in the area are left unharmed.");

    private final String title;
    private final Material icon;
    private final String summary;

    Metamagic(String title, Material icon, String summary) {
        this.title = title;
        this.icon = icon;
        this.summary = summary;
    }

    public String title() {
        return title;
    }

    public Material icon() {
        return icon;
    }

    public String summary() {
        return summary;
    }

    /** Fixed cost in sorcery points; Twinned costs the spell's level instead, and a cantrip one. */
    public int cost(int spellLevel) {
        return switch (this) {
            case QUICKENED -> 2;
            case HEIGHTENED -> 3;
            case TWINNED -> Math.max(1, spellLevel);
            case EMPOWERED, EXTENDED, CAREFUL -> 1;
        };
    }

    /** The next one round the wheel, and nothing after the last. */
    public static Optional<Metamagic> after(Metamagic current) {
        if (current == null) {
            return Optional.of(values()[0]);
        }
        int next = current.ordinal() + 1;
        return next < values().length ? Optional.of(values()[next]) : Optional.empty();
    }
}
