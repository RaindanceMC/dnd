package de.raindancer.modules.skills.model;

import de.raindancer.modules.skills.skill.alchemy.AlchemyEffect;
import de.raindancer.modules.skills.skill.cooking.CookingEffect;
import de.raindancer.modules.skills.skill.farming.FarmingEffect;
import de.raindancer.modules.skills.skill.foraging.ForagingEffect;
import de.raindancer.modules.skills.skill.taming.TamingEffect;
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
    public static final SkillDef FARMING = new SkillDef("farming", "Farming", Material.WHEAT, 18, "☘",
            "Fuller harvests, crops that plant and grow themselves, twins in the pasture.",
            List.of(FarmingEffect.values()));
    public static final SkillDef FORAGING = new SkillDef("foraging", "Foraging", Material.SWEET_BERRIES, 19, "✿",
            "Berries, flowers and mushrooms: more of them, rare finds, bone meal that goes further.",
            List.of(ForagingEffect.values()));
    public static final SkillDef ALCHEMY = new SkillDef("alchemy", "Alchemy", Material.BREWING_STAND, 24, "⚗",
            "Potions that last longer and hit harder, a spare from the stand, ingredients saved.",
            List.of(AlchemyEffect.values()));
    public static final SkillDef COOKING = new SkillDef("cooking", "Cooking", Material.CAMPFIRE, 25, "♨",
            "Meals that fill more and bless you, extra servings, a furnace that hurries.",
            List.of(CookingEffect.values()));
    public static final SkillDef TAMING = new SkillDef("taming", "Taming", Material.LEAD, 26, "♞",
            "Wolves and cats that bite harder and live longer, horses that run, leap and never fall.",
            List.of(TamingEffect.values()));

    private static final List<SkillDef> ALL = List.of(DEFENSE, MELEE, ARCHERY, AGILITY, MAGIC, FARMING, FORAGING,
            ALCHEMY, COOKING, TAMING);

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
