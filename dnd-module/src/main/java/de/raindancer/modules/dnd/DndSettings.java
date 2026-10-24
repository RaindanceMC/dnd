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
        @Topic(path = "dnd/combat", title = "Spells and combat", icon = Material.BLAZE_POWDER),
        @Topic(path = "dnd/rest", title = "Resting", icon = Material.RED_BED),
        @Topic(path = "dnd/stance", title = "Casting stance", icon = Material.AMETHYST_SHARD),
})
public record DndSettings(

        @In("dnd") @Title("Worlds where adventurers work")
        @Describe("World names, separated by commas. Empty means every world. Outside these, classes give no "
                + "health, spells cannot be cast and nothing earns experience — the hero is kept, not lost.")
        @Key("worlds")
        List<String> worlds,

        @In("dnd") @Title("Experience earned") @Range(min = 0, max = 1000)
        @Describe("Percent of the table's experience a kill gives. 100 is the book; 200 levels twice as fast; "
                + "0 stops levelling from kills altogether.")
        @Key("xp-percent")
        int xpPercent,

        @In("dnd") @Title("Health per level") @Range(min = 0, max = 100)
        @Describe("Percent of the table's hit points added per level above the first. 25 makes a level 20 "
                + "fighter about 58 health (29 hearts); 100 would be well over a hundred.")
        @Key("health-percent")
        int healthPercent,


        @In("dnd/combat") @Title("Spell damage") @Range(min = 0, max = 1000)
        @Describe("Percent of what the dice roll that a spell actually does. Monsters have about the hit "
                + "points the book gives them, so 100 plays as written.")
        @Key("combat.spell-damage-percent")
        int spellDamagePercent,

        @In("dnd/combat") @Title("Spell damage against players") @Range(min = 0, max = 100)
        @Describe("Percent of a spell's damage that lands on a player, on top of the above. Players have far "
                + "fewer hit points than the book assumes. Whether players can hurt each other at all is the "
                + "server's PvP rule, not this.")
        @Key("combat.player-damage-percent")
        int playerDamagePercent,

        @In("dnd/combat") @Title("Show the dice")
        @Describe("Tell the caster every d20 roll: attacks against armour class, and each saving throw.")
        @Key("combat.show-rolls")
        boolean showRolls,

        @In("dnd/combat") @Title("Wait after an action") @Range(min = 0, max = 10000)
        @Describe("Milliseconds before another spell or feature, after one that takes an action.")
        @Key("combat.action-millis")
        int actionMillis,

        @In("dnd/combat") @Title("Wait after a bonus action") @Range(min = 0, max = 10000)
        @Describe("Milliseconds before another, after a bonus action or a Quickened spell.")
        @Key("combat.bonus-action-millis")
        int bonusActionMillis,

        @In("dnd/rest") @Title("Short rest length") @Range(min = 1, max = 600)
        @Describe("Seconds a player has to stand still, unhurt, for a short rest.")
        @Key("rest.short-seconds")
        int shortRestSeconds,

        @In("dnd/rest") @Title("Time between long rests") @Range(min = 0, max = 1440)
        @Describe("Minutes. Sleeping through the night is a long rest at most this often; 0 makes every night "
                + "one. A Minecraft day is twenty minutes.")
        @Key("rest.long-minutes")
        int longRestMinutes,

        @In("dnd/stance") @Title("Sneak with F to enter the stance")
        @Describe("Off: F (swap hands) enters and leaves the casting stance for anybody with a class. On: only "
                + "sneak and F does, and plain F swaps hands as usual.")
        @Key("stance.sneak-to-enter")
        boolean stanceNeedsSneak,

        @In("dnd/combat") @Title("Wild magic surge chance") @Range(min = 0, max = 100)
        @Describe("Percent chance that a Wild Magic sorcerer's spell from a slot sets off a surge. The book "
                + "says 5 (a 1 on a d20); 0 leaves only Tides of Chaos to set one off.")
        @Key("combat.wild-surge-percent")
        int wildSurgePercent,

        @In("dnd") @Title("Magic item drops") @Range(min = 0, max = 1000)
        @Describe("Percent of the normal chance that a monster drops a magic item. 0 switches drops off; "
                + "bosses always drop one unless this is 0.")
        @Key("loot-percent")
        int lootPercent) {

    public static final DndSettings DEFAULTS = new DndSettings(List.of(), 100, 25, 100, 50, true, 1200, 500,
            30, 15, false, 5, 100);

    public boolean worksIn(String world) {
        if (worlds == null || worlds.isEmpty()) {
            return true;
        }
        String wanted = world == null ? "" : world.toLowerCase(Locale.ROOT);
        return worlds.stream().anyMatch(name -> name != null && name.trim().toLowerCase(Locale.ROOT).equals(wanted));
    }

    public DndSettings withWorlds(List<String> value) {
        return new DndSettings(value, xpPercent, healthPercent, spellDamagePercent,
                playerDamagePercent, showRolls, actionMillis, bonusActionMillis, shortRestSeconds, longRestMinutes,
                stanceNeedsSneak, wildSurgePercent, lootPercent);
    }

    public DndSettings withXpPercent(int value) {
        return new DndSettings(worlds, value, healthPercent, spellDamagePercent,
                playerDamagePercent, showRolls, actionMillis, bonusActionMillis, shortRestSeconds, longRestMinutes,
                stanceNeedsSneak, wildSurgePercent, lootPercent);
    }

    public DndSettings withHealthPercent(int value) {
        return new DndSettings(worlds, xpPercent, value, spellDamagePercent,
                playerDamagePercent, showRolls, actionMillis, bonusActionMillis, shortRestSeconds, longRestMinutes,
                stanceNeedsSneak, wildSurgePercent, lootPercent);
    }


    public DndSettings withSpellDamagePercent(int value) {
        return new DndSettings(worlds, xpPercent, healthPercent, value, playerDamagePercent,
                showRolls, actionMillis, bonusActionMillis, shortRestSeconds, longRestMinutes, stanceNeedsSneak, wildSurgePercent, lootPercent);
    }

    public DndSettings withPlayerDamagePercent(int value) {
        return new DndSettings(worlds, xpPercent, healthPercent, spellDamagePercent, value,
                showRolls, actionMillis, bonusActionMillis, shortRestSeconds, longRestMinutes, stanceNeedsSneak, wildSurgePercent, lootPercent);
    }

    public DndSettings withShowRolls(boolean value) {
        return new DndSettings(worlds, xpPercent, healthPercent, spellDamagePercent,
                playerDamagePercent, value, actionMillis, bonusActionMillis, shortRestSeconds, longRestMinutes,
                stanceNeedsSneak, wildSurgePercent, lootPercent);
    }

    public DndSettings withActionMillis(int value) {
        return new DndSettings(worlds, xpPercent, healthPercent, spellDamagePercent,
                playerDamagePercent, showRolls, value, bonusActionMillis, shortRestSeconds, longRestMinutes,
                stanceNeedsSneak, wildSurgePercent, lootPercent);
    }

    public DndSettings withBonusActionMillis(int value) {
        return new DndSettings(worlds, xpPercent, healthPercent, spellDamagePercent,
                playerDamagePercent, showRolls, actionMillis, value, shortRestSeconds, longRestMinutes,
                stanceNeedsSneak, wildSurgePercent, lootPercent);
    }

    public DndSettings withShortRestSeconds(int value) {
        return new DndSettings(worlds, xpPercent, healthPercent, spellDamagePercent,
                playerDamagePercent, showRolls, actionMillis, bonusActionMillis, value, longRestMinutes,
                stanceNeedsSneak, wildSurgePercent, lootPercent);
    }

    public DndSettings withLongRestMinutes(int value) {
        return new DndSettings(worlds, xpPercent, healthPercent, spellDamagePercent,
                playerDamagePercent, showRolls, actionMillis, bonusActionMillis, shortRestSeconds, value,
                stanceNeedsSneak, wildSurgePercent, lootPercent);
    }

    public DndSettings withWildSurgePercent(int value) {
        return new DndSettings(worlds, xpPercent, healthPercent, spellDamagePercent,
                playerDamagePercent, showRolls, actionMillis, bonusActionMillis, shortRestSeconds, longRestMinutes,
                stanceNeedsSneak, value, lootPercent);
    }

    public DndSettings withLootPercent(int value) {
        return new DndSettings(worlds, xpPercent, healthPercent, spellDamagePercent,
                playerDamagePercent, showRolls, actionMillis, bonusActionMillis, shortRestSeconds, longRestMinutes,
                stanceNeedsSneak, wildSurgePercent, value);
    }

    public DndSettings withStanceNeedsSneak(boolean value) {
        return new DndSettings(worlds, xpPercent, healthPercent, spellDamagePercent,
                playerDamagePercent, showRolls, actionMillis, bonusActionMillis, shortRestSeconds, longRestMinutes,
                value, wildSurgePercent, lootPercent);
    }
}
