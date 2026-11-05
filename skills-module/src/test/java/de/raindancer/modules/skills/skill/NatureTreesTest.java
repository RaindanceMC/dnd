package de.raindancer.modules.skills.skill;

import de.raindancer.core.ui.tree.TreeBranch;
import de.raindancer.core.ui.tree.TreeNode;
import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.model.SkillCatalog;
import de.raindancer.modules.skills.model.SkillDef;
import de.raindancer.modules.skills.store.SkillsPack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Farming, Foraging, Cooking, Alchemy and Taming: their trees' shape, their effects, their pictures and pages. */
class NatureTreesTest {

    private static final List<SkillDef> NATURE = List.of(SkillCatalog.FARMING, SkillCatalog.FORAGING,
            SkillCatalog.COOKING, SkillCatalog.ALCHEMY, SkillCatalog.TAMING);

    @Test
    @DisplayName("the five are in the catalogue, each with an order and an id no other skill has")
    void listed() {
        assertThat(SkillCatalog.all()).containsAll(NATURE);
        Set<Integer> orders = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (SkillDef skill : SkillCatalog.all()) {
            assertThat(orders.add(skill.order())).as("order of " + skill.id()).isTrue();
            assertThat(ids.add(skill.id())).as(skill.id()).isTrue();
        }
        for (SkillDef skill : NATURE) {
            assertThat(SkillCatalog.byId(skill.id())).contains(skill);
            assertThat(skill.badge()).isNotBlank();
        }
    }

    @Test
    @DisplayName("20+ nodes, 2-3 branches stacked three rows each, a choice pair, a capstone and a free badge each")
    void shape() {
        for (SkillDef skill : NATURE) {
            var tree = skill.tree();
            assertThat(tree.nodes()).as(skill.id()).hasSizeGreaterThanOrEqualTo(20);
            assertThat(tree.branches()).as(skill.id()).hasSizeBetween(2, 3);
            for (int index = 0; index < tree.branches().size(); index++) {
                TreeBranch branch = tree.branches().get(index);
                String where = skill.id() + "/" + branch.id();
                List<TreeNode> nodes = tree.nodesOf(branch.id());
                for (TreeNode node : nodes) {
                    assertThat(node.row()).as(where + " " + node.id() + " row").isBetween(index * 3, index * 3 + 2);
                    assertThat(node.levelFor(node.ranks())).as(where + " " + node.id() + " last rank's level")
                            .isLessThanOrEqualTo(30);
                }
                List<TreeNode> choices = nodes.stream().filter(TreeNode::choice).toList();
                assertThat(choices).as(where + " choice pair").hasSize(2);
                assertThat(choices.get(0).excludes()).as(where).containsExactly(choices.get(1).id());
                assertThat(choices.get(1).excludes()).as(where).containsExactly(choices.get(0).id());
                assertThat(nodes.stream().filter(TreeNode::capstone)).as(where + " capstone").hasSize(1);
                List<TreeNode> badges = tree.masteries(branch.id());
                assertThat(badges).as(where + " mastery").hasSize(1);
                TreeNode badge = badges.getFirst();
                assertThat(badge.cost()).as(where + " badge is free").isZero();
                assertThat(badge.effects().keySet()).as(where + " badge brings a mastery feature of its own")
                        .anyMatch(id -> skill.effects().get(id).cap() == 1);
            }
        }
    }

    @Test
    @DisplayName("whichever side of a choice is taken, the rest of the branch can still be finished")
    void finishable() {
        for (SkillDef skill : NATURE) {
            var tree = skill.tree();
            for (TreeBranch branch : tree.branches()) {
                List<TreeNode> nodes = tree.nodesOf(branch.id());
                for (TreeNode side : nodes.stream().filter(TreeNode::choice).toList()) {
                    Set<String> shut = new HashSet<>(side.excludes());
                    Set<String> reached = new HashSet<>();
                    boolean grew = true;
                    while (grew) {
                        grew = false;
                        for (TreeNode node : nodes) {
                            if (node.mastery() || shut.contains(node.id()) || reached.contains(node.id())) {
                                continue;
                            }
                            if (node.requires().isEmpty() || node.requires().stream().anyMatch(reached::contains)) {
                                grew = reached.add(node.id());
                            }
                        }
                    }
                    for (TreeNode node : nodes) {
                        if (!node.mastery() && !shut.contains(node.id())) {
                            assertThat(reached).as(skill.id() + ": " + node.id() + " with " + side.id() + " taken")
                                    .contains(node.id());
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("every effect of a skill is in its tree, its ids are its own, a feat's line has no number")
    void effects() {
        for (SkillDef skill : NATURE) {
            Set<String> used = new HashSet<>();
            skill.tree().nodes().forEach(node -> used.addAll(node.effects().keySet()));
            assertThat(used).as(skill.id()).containsAll(skill.effects().keySet());
            for (SkillDef other : SkillCatalog.all()) {
                if (other != skill) {
                    assertThat(other.effects().keySet()).as(skill.id() + " ids in " + other.id())
                            .doesNotContainAnyElementsOf(skill.effects().keySet());
                }
            }
            for (Effect effect : skill.effects().values()) {
                String line = effect.describe(12);
                assertThat(line).as(effect.id()).doesNotContain("%s");
                if (effect.cap() != 1) {
                    assertThat(line).as(effect.id()).contains("12");
                }
            }
        }
    }

    @Test
    @DisplayName("a painting behind every branch, a help page and a package for every skill")
    void dressed() {
        for (SkillDef skill : NATURE) {
            for (TreeBranch branch : skill.tree().branches()) {
                assertThat(SkillsPack.treeBackdrop(branch.id())).as("painting for " + branch.id()).isNotNull();
            }
            assertThat(Path.of("src/main/resources/rains-help/skills-" + skill.id() + ".yml")).exists();
            Path code = Path.of("src/main/java/de/raindancer/modules/skills/skill/" + skill.id());
            assertThat(code).isDirectory();
            assertThat(Files.exists(code.resolve(Character.toUpperCase(skill.id().charAt(0)) + skill.id().substring(1)
                    + "Listener.java"))).as(skill.id() + " listener").isTrue();
        }
    }
}
