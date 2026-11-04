package de.raindancer.modules.skills.model;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Every skill this module has, in the order they are listed. */
public final class SkillCatalog {

    private static final List<Perk> FIGHTING = List.of(Perk.values());

    public static final SkillDef DEFENSE = new SkillDef("defense", "Defense", Material.SHIELD, 10, "🛡",
            "Hearts, armour, healing — and a body that will not die.", FIGHTING);
    public static final SkillDef MELEE = new SkillDef("melee", "Melee", Material.IRON_SWORD, 11, "⚔",
            "Harder, faster blows; two blades at once, or one great one.", FIGHTING);
    public static final SkillDef ARCHERY = new SkillDef("archery", "Archery", Material.BOW, 12, "🏹",
            "Arrows that hit harder, fly further and come back.", FIGHTING);
    public static final SkillDef AGILITY = new SkillDef("agility", "Agility", Material.LEATHER_BOOTS, 13, "✦",
            "Quick feet, blows that miss you, knives in the back.", FIGHTING);
    public static final SkillDef MAGIC = new SkillDef("magic", "Magic", Material.ENCHANTED_BOOK, 14, "✧",
            "Scrolls, wands and potions at their full strength — and more to find.", FIGHTING);

    public static final SkillDef MINING = new SkillDef("mining", "Mining", Material.IRON_PICKAXE, 20, "⛏",
            "A faster pickaxe, ores twice over, whole veins at once and eyes for the dark.",
            List.of(de.raindancer.modules.skills.skill.mining.MiningEffect.values()));
    public static final SkillDef EXCAVATION = new SkillDef("excavation", "Excavation", Material.IRON_SHOVEL, 21, "⚱",
            "A faster shovel, treasures in the dirt and luck under the brush.",
            List.of(de.raindancer.modules.skills.skill.excavation.ExcavationEffect.values()));
    public static final SkillDef WOODCUTTING = new SkillDef("woodcutting", "Woodcutting", Material.IRON_AXE, 22, "🪓",
            "A faster axe, whole trees felled, saplings and apples from the leaves.",
            List.of(de.raindancer.modules.skills.skill.woodcutting.WoodcuttingEffect.values()));
    public static final SkillDef SMITHING = new SkillDef("smithing", "Smithing", Material.ANVIL, 23, "⚒",
            "Cheaper anvil work, never too expensive; salvage, sturdier gear, templates kept.",
            List.of(de.raindancer.modules.skills.skill.smithing.SmithingEffect.values()));

    private static final List<SkillDef> ALL = List.of(DEFENSE, MELEE, ARCHERY, AGILITY, MAGIC, MINING, EXCAVATION,
            WOODCUTTING, SMITHING);

    /** Which skill holds each node id: for ranks kept before there were skills, by node id alone. */
    private static volatile Map<String, SkillDef> owners;

    private SkillCatalog() {
    }

    public static List<SkillDef> all() {
        return ALL;
    }

    public static Optional<SkillDef> byId(String id) {
        return ALL.stream().filter(skill -> skill.id().equals(id)).findFirst();
    }

    /** Every effect any skill's nodes may name, by id. */
    public static Map<String, Effect> effects() {
        Map<String, Effect> all = new HashMap<>();
        ALL.forEach(skill -> all.putAll(skill.effects()));
        return all;
    }

    /**
     * A rank's key as heroes keep it: {@code <skill>:<node>}. A bare node id — kept before there were skills — is
     * given the skill whose tree has it; one no tree has is kept as it is, and counts for nothing.
     */
    public static String qualify(String key) {
        if (key == null || key.indexOf(':') >= 0) {
            return key;
        }
        SkillDef owner = owners().get(key);
        return owner == null ? key : owner.key(key);
    }

    private static Map<String, SkillDef> owners() {
        Map<String, SkillDef> known = owners;
        if (known == null) {
            known = new HashMap<>();
            for (SkillDef skill : ALL) {
                for (var node : skill.tree().nodes()) {
                    known.put(node.id(), skill);
                }
            }
            owners = known;
        }
        return known;
    }
}
