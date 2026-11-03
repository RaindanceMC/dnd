package de.raindancer.modules.dnd;

import de.raindancer.core.data.settings.Describe;
import de.raindancer.core.data.settings.In;
import de.raindancer.core.data.settings.Key;
import de.raindancer.core.data.settings.Range;
import de.raindancer.core.data.settings.Settings;
import de.raindancer.core.data.settings.Title;
import de.raindancer.core.data.settings.Topic;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/** What an owner can decide about adventurers. The record is the schema: file, comments and screens come from it. */
@Settings(id = "dnd", topics = {
        @Topic(path = "dnd", title = "Adventurers", icon = Material.ENCHANTED_BOOK),
})
public record DndSettings(

        @In("dnd") @Title("Worlds where adventurers work")
        @Describe("World names, separated by commas. Empty means every world. Outside these the skill tree gives "
                + "nothing and nothing earns experience — the hero is kept, not lost.")
        @Key("worlds")
        List<String> worlds,

        @In("dnd") @Title("Experience earned") @Range(min = 0, max = 1000)
        @Describe("Percent of what playing earns: minutes played, natural ores mined, grown crops harvested, fish, "
                + "breeding, trades, enchanting, advancements, raids and bosses. 200 levels twice as fast; 0 stops "
                + "levelling. Ordinary monsters never earn anything, so a mob farm does not pay.")
        @Key("xp-percent")
        int xpPercent,

        @In("dnd") @Title("Magic item drops") @Range(min = 0, max = 1000)
        @Describe("Percent of the normal chance that a monster drops a magic item. 0 switches drops off; "
                + "bosses always drop one unless this is 0. Never from a spawner's mobs or for a player who is "
                + "away, and at most four an hour per player.")
        @Key("loot-percent")
        int lootPercent,

        @In("dnd") @Title("Skill tree against players") @Range(min = 0, max = 100)
        @Describe("Percent of the tree's damage, dodging and damage taken that counts when players fight each other. "
                + "100 is all of it; 0 keeps fights between players as vanilla as the tree's hearts and armour allow.")
        @Key("pvp-percent")
        int pvpPercent,

        @In("dnd") @Title("Hours between unlearning the tree") @Range(min = 0, max = 720)
        @Describe("How long a player waits after unlearning the whole skill tree before they may do it again.")
        @Key("unlearn-hours")
        int unlearnHours,

        @In("dnd") @Title("Potion of Mach Fuck, blocks a second") @Range(min = 50, max = 1000)
        @Describe("How fast the Potion of Mach Fuck flies a gliding player, along where they look. The land cannot "
                + "load that fast: the faster, the more of the flight goes through empty sky, and the harder the "
                + "server works generating what is ahead.")
        @Key("mach.blocks-per-second")
        int machBlocksPerSecond,

        @In("dnd") @Title("Potion of Mach Fuck, seconds") @Range(min = 3, max = 120)
        @Describe("How long one Potion of Mach Fuck lasts. Wall crashes and falls are forgiven while it works "
                + "and for five seconds after.")
        @Key("mach.seconds")
        int machSeconds) {

    public static final DndSettings DEFAULTS = new DndSettings(List.of(), 100, 100, 50, 24, 600, 20);

    public boolean worksIn(String world) {
        if (worlds == null || worlds.isEmpty()) {
            return true;
        }
        String wanted = world == null ? "" : world.toLowerCase(Locale.ROOT);
        return worlds.stream().anyMatch(name -> name != null && name.trim().toLowerCase(Locale.ROOT).equals(wanted));
    }

    public DndSettings withWorlds(List<String> value) {
        return new DndSettings(value, xpPercent, lootPercent, pvpPercent, unlearnHours, machBlocksPerSecond,
                machSeconds);
    }

    public DndSettings withXpPercent(int value) {
        return new DndSettings(worlds, value, lootPercent, pvpPercent, unlearnHours, machBlocksPerSecond, machSeconds);
    }

    public DndSettings withLootPercent(int value) {
        return new DndSettings(worlds, xpPercent, value, pvpPercent, unlearnHours, machBlocksPerSecond, machSeconds);
    }

    public DndSettings withPvpPercent(int value) {
        return new DndSettings(worlds, xpPercent, lootPercent, value, unlearnHours, machBlocksPerSecond, machSeconds);
    }

    public DndSettings withUnlearnHours(int value) {
        return new DndSettings(worlds, xpPercent, lootPercent, pvpPercent, value, machBlocksPerSecond, machSeconds);
    }

    public DndSettings withMachBlocksPerSecond(int value) {
        return new DndSettings(worlds, xpPercent, lootPercent, pvpPercent, unlearnHours, value, machSeconds);
    }

    public DndSettings withMachSeconds(int value) {
        return new DndSettings(worlds, xpPercent, lootPercent, pvpPercent, unlearnHours, machBlocksPerSecond, value);
    }
}
