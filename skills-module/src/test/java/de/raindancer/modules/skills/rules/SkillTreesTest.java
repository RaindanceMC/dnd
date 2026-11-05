package de.raindancer.modules.skills.rules;

import de.raindancer.core.ui.tree.TreeGrid;
import de.raindancer.core.ui.tree.TreeNode;
import de.raindancer.modules.skills.model.Perk;
import de.raindancer.modules.skills.model.SkillCatalog;
import de.raindancer.modules.skills.model.SkillDef;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SkillTreesTest {

    @Test
    @DisplayName("every skill's tree reads, every branch ends in exactly one mastery badge that is free")
    void everyTreeReads() {
        for (SkillDef skill : SkillCatalog.all()) {
            assertThat(skill.tree().branches()).as(skill.id()).isNotEmpty();
            for (var branch : skill.tree().branches()) {
                var badges = skill.tree().masteries(branch.id());
                assertThat(badges).as(skill.id() + "/" + branch.id()).hasSize(1);
                assertThat(badges.getFirst().cost()).as("a badge is earned, not bought").isZero();
            }
        }
    }

    @Test
    @DisplayName("node ids are unique across every skill, so a rank kept by node id alone finds its skill")
    void uniqueIds() {
        Map<String, String> seen = new HashMap<>();
        for (SkillDef skill : SkillCatalog.all()) {
            for (TreeNode node : skill.tree().nodes()) {
                assertThat(seen.put(node.id(), skill.id())).as(node.id() + " is in " + seen.get(node.id())
                        + " and " + skill.id()).isNull();
            }
        }
        assertThat(SkillCatalog.qualify("brawler")).isEqualTo("melee:brawler");
        assertThat(SkillCatalog.qualify("toughness")).isEqualTo("defense:toughness");
        assertThat(SkillCatalog.qualify("melee:brawler")).isEqualTo("melee:brawler");
        assertThat(SkillCatalog.qualify("gone")).isEqualTo("gone");
    }

    @Test
    @DisplayName("no node is written twice in one tree — the second would silently replace the first")
    void noNodeTwice() throws java.io.IOException {
        for (SkillDef skill : SkillCatalog.all()) {
            String path = "/de/raindancer/modules/skills/trees/" + skill.id() + ".yml";
            try (var in = SkillTreesTest.class.getResourceAsStream(path)) {
                assertThat(in).as(path).isNotNull();
                long written = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines()
                        .filter(line -> line.matches("^  [a-z_]+: \\{.*")).count();
                assertThat(skill.tree().nodes()).as(skill.id()).hasSize((int) written);
            }
        }
    }

    @Test
    @DisplayName("every skill has a place of its own in the list and a badge of its own")
    void ownPlaceAndBadge() {
        assertThat(SkillCatalog.all()).extracting(SkillDef::order).doesNotHaveDuplicates();
        assertThat(SkillCatalog.all()).extracting(SkillDef::badge).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("no two lines of a skill share a slot; every icon is an item; every effect is used somewhere")
    void drawable() {
        Set<String> used = new HashSet<>();
        for (SkillDef skill : SkillCatalog.all()) {
            Set<Integer> lines = new HashSet<>();
            for (TreeNode node : skill.tree().nodes()) {
                assertThat(Material.matchMaterial(node.icon())).as(node.id() + " icon " + node.icon()).isNotNull();
                assertThat(node.effects()).as(node.id()).isNotEmpty();
                used.addAll(node.effects().keySet());
                for (String needed : node.requires()) {
                    TreeNode other = skill.tree().node(needed).orElseThrow();
                    // a line sits in the slot between its two nodes on the canvas: twice the columns and rows, plus one
                    TreeGrid.link(other.column(), other.row(), node.column(), node.row());
                    int key = (other.column() + node.column()) * 1_000 + (other.row() + node.row());
                    assertThat(lines.add(key)).as(skill.id() + ": " + needed + " to " + node.id()).isTrue();
                }
            }
        }
        for (Perk perk : Perk.values()) {
            assertThat(used).as(perk.id()).contains(perk.id());
        }
    }

    @Test
    @DisplayName("every effect id belongs to one kind of effect only")
    void effectIds() {
        assertThat(SkillCatalog.effects()).containsKeys("hearts", "dual_wield", "melee_damage");
    }

    /** Every node the five fighting skills had when each was one branch: heroes keep ranks in them. */
    private static final Map<SkillDef, List<String>> FIRST_NODES = Map.of(
            SkillCatalog.DEFENSE, List.of("toughness", "hearty_meals", "thick_skin", "mending", "stout", "hardened",
                    "big_heart", "second_wind", "stoneskin", "fast_healer", "giant_blood", "iron_hide", "bottomless",
                    "undying", "bulwark", "defense_mastery"),
            SkillCatalog.MELEE, List.of("brawler", "quick_hands", "long_arms", "crusher", "sweeper", "bloodthirst",
                    "veteran", "dual_wielder", "great_weapon", "flurry", "berserker", "executioner", "slayer",
                    "warlord", "vampire", "melee_mastery"),
            SkillCatalog.ARCHERY, List.of("steady_aim", "fletcher", "hawk_eye", "quiver", "hunter", "tracker",
                    "piercing", "volley", "sharpshooter", "rain_of_arrows", "marksmanship", "sniper",
                    "endless_quiver", "deadeye", "monster_hunter", "archery_mastery"),
            SkillCatalog.AGILITY, List.of("light_feet", "cat_fall", "sidestep", "springheel", "climber", "backstabber",
                    "evasion", "acrobat", "assassin", "windrunner", "blur", "shadowstep", "wind_walker", "phantom",
                    "nightblade", "agility_mastery"),
            SkillCatalog.MAGIC, List.of("studious", "orb_catcher", "lucky", "wandwright", "scribe", "alchemist",
                    "treasure_sense", "sage", "treasure_hunter", "spellbreaker", "prospector", "apothecary",
                    "loremaster", "archmage", "fortune", "magic_mastery"));

    @Test
    @DisplayName("the fighting skills keep every node they had, in the same skill, so no hero loses a rank")
    void keepsOldNodes() {
        FIRST_NODES.forEach((skill, ids) -> {
            for (String id : ids) {
                assertThat(skill.tree().node(id)).as(skill.id() + ":" + id).isPresent();
                assertThat(SkillCatalog.qualify(id)).isEqualTo(skill.key(id));
            }
        });
    }

    @Test
    @DisplayName("a fighting skill is a real tree: 20 nodes and more, branches stacked three rows each, each with a "
            + "choice pair, a capstone and a badge whose mastery feature is its own")
    void fightingTreesAreBig() {
        Set<Perk> features = new HashSet<>();
        for (SkillDef skill : FIRST_NODES.keySet()) {
            var tree = skill.tree();
            assertThat(tree.nodes()).as(skill.id()).hasSizeGreaterThanOrEqualTo(20);
            assertThat(tree.branches()).as(skill.id()).hasSizeBetween(2, 3);
            for (int index = 0; index < tree.branches().size(); index++) {
                String branch = tree.branches().get(index).id();
                var nodes = tree.nodesOf(branch);
                String where = skill.id() + "/" + branch;
                for (TreeNode node : nodes) {
                    assertThat(node.row()).as(where + " " + node.id() + " row").isBetween(index * 3, index * 3 + 2);
                }
                assertThat(nodes.stream().filter(TreeNode::capstone)).as(where + " capstone").hasSize(1);
                List<TreeNode> choices = nodes.stream().filter(TreeNode::choice).toList();
                assertThat(choices).as(where + " choice pair").hasSize(2);
                assertThat(choices.get(0).excludes()).contains(choices.get(1).id());
                assertThat(choices.get(1).excludes()).contains(choices.get(0).id());
                TreeNode badge = tree.masteries(branch).getFirst();
                List<Perk> feats = badge.effects().keySet().stream().map(Perk::parse).flatMap(java.util.Optional::stream)
                        .filter(Perk::feat).toList();
                assertThat(feats).as(where + " mastery feature").hasSize(1);
                assertThat(features.add(feats.getFirst())).as(where + ": " + feats.getFirst() + " is another badge's")
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("a feat reads without a number; every other effect has a number to show")
    void featsRead() {
        assertThat(Perk.AEGIS.feat()).isTrue();
        assertThat(Perk.AEGIS.describe(1)).startsWith("Aegis:");
        assertThat(Perk.THORNS.feat()).isFalse();
        assertThat(Perk.THORNS.describe(15)).isEqualTo("A foe's blow hurts it back for 15% of what it did");
    }
}
