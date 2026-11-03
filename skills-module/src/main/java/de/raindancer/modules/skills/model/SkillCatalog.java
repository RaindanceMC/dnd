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

    private static final List<SkillDef> ALL = List.of(DEFENSE, MELEE, ARCHERY, AGILITY, MAGIC);

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
