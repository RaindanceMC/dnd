package de.raindancer.modules.dnd.model;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** The classes a player can be. Starting scores are the standard array, laid out the way the class wants it. */
public enum HeroClass {

    FIGHTER("Fighter", 10, Ability.STRENGTH, null, Set.of(Ability.STRENGTH, Ability.CONSTITUTION),
            new AbilityScores(15, 13, 14, 8, 12, 10), Material.IRON_SWORD,
            List.of("A master of weapons and armour.", "Second Wind heals you, Action Surge buys",
                    "you a burst of speed and fury, and from", "level 5 every swing hits harder.")),
    BARBARIAN("Barbarian", 12, Ability.STRENGTH, null, Set.of(Ability.STRENGTH, Ability.CONSTITUTION),
            new AbilityScores(15, 13, 14, 8, 12, 10), Material.IRON_AXE,
            List.of("Fury made flesh. Rage to hit harder and", "shrug off half of every blow; without",
                    "a chestplate your toughness is armour.", "From level 5 you run faster.")),
    ROGUE("Rogue", 8, Ability.DEXTERITY, null, Set.of(Ability.DEXTERITY, Ability.INTELLIGENCE),
            new AbilityScores(8, 15, 14, 12, 13, 10), Material.SHEARS,
            List.of("Strike where it hurts. Sneak Attack adds", "dice to a hit on a distracted foe,",
                    "Cunning Action dashes, hides and leaps", "away, and you dodge what others cannot.")),
    WIZARD("Wizard", 6, Ability.INTELLIGENCE, Ability.INTELLIGENCE, Set.of(Ability.INTELLIGENCE, Ability.WISDOM),
            new AbilityScores(8, 13, 14, 15, 12, 10), Material.ENCHANTED_BOOK,
            List.of("A scholar of the arcane with the widest", "spell list of all. Arcane Recovery wins",
                    "back spell slots between long rests.")),
    SORCERER("Sorcerer", 6, Ability.CHARISMA, Ability.CHARISMA, Set.of(Ability.CONSTITUTION, Ability.CHARISMA),
            new AbilityScores(8, 13, 14, 10, 12, 15), Material.BLAZE_ROD,
            List.of("Magic in the blood. Sorcery points turn", "into spell slots and back, and metamagic",
                    "bends a spell: faster, twinned, stronger,", "longer, harder to resist.")),
    CLERIC("Cleric", 8, Ability.WISDOM, Ability.WISDOM, Set.of(Ability.WISDOM, Ability.CHARISMA),
            new AbilityScores(13, 12, 14, 8, 15, 10), Material.GOLDEN_APPLE,
            List.of("A healer of the Life domain. Your healing", "is stronger than anyone's, Channel",
                    "Divinity turns the undead and mends the", "whole party at once."));

    private final String title;
    private final int hitDie;
    private final Ability primary;
    private final Ability spellcasting;
    private final Set<Ability> savingThrows;
    private final AbilityScores startingScores;
    private final Material icon;
    private final List<String> pitch;

    HeroClass(String title, int hitDie, Ability primary, Ability spellcasting, Set<Ability> savingThrows,
              AbilityScores startingScores, Material icon, List<String> pitch) {
        this.title = title;
        this.hitDie = hitDie;
        this.primary = primary;
        this.spellcasting = spellcasting;
        this.savingThrows = savingThrows;
        this.startingScores = startingScores;
        this.icon = icon;
        this.pitch = pitch;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String title() {
        return title;
    }

    public int hitDie() {
        return hitDie;
    }

    public Ability primary() {
        return primary;
    }

    /** The ability spells are cast with, or empty for a class that casts none. */
    public Optional<Ability> spellcasting() {
        return Optional.ofNullable(spellcasting);
    }

    public boolean castsSpells() {
        return spellcasting != null;
    }

    public boolean proficientIn(Ability save) {
        return savingThrows.contains(save);
    }

    public Set<Ability> savingThrows() {
        return savingThrows;
    }

    public AbilityScores startingScores() {
        return startingScores;
    }

    public Material icon() {
        return icon;
    }

    /** What the class is, in a few plain lines. */
    public List<String> pitch() {
        return pitch;
    }

    public static Optional<HeroClass> named(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String wanted = text.trim().toLowerCase(Locale.ROOT);
        for (HeroClass heroClass : values()) {
            if (heroClass.id().equals(wanted) || heroClass.title.toLowerCase(Locale.ROOT).equals(wanted)) {
                return Optional.of(heroClass);
            }
        }
        return Optional.empty();
    }
}
