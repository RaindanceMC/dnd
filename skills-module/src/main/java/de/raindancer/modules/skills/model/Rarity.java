package de.raindancer.modules.skills.model;

/** How rare a magic item is, and the colour its name is written in. */
public enum Rarity {
    COMMON("Common", "<white>"),
    UNCOMMON("Uncommon", "<green>"),
    RARE("Rare", "<blue>"),
    VERY_RARE("Very rare", "<light_purple>"),
    LEGENDARY("Legendary", "<gold>");

    private final String title;
    private final String colour;

    Rarity(String title, String colour) {
        this.title = title;
        this.colour = colour;
    }

    public String title() {
        return title;
    }

    /** A MiniMessage colour tag, for an item's name. */
    public String colour() {
        return colour;
    }

}
