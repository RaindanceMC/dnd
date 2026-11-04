package de.raindancer.modules.skills.skill;

import de.raindancer.core.ui.tree.TreeBranch;
import de.raindancer.core.ui.tree.TreeNode;
import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.model.SkillCatalog;
import de.raindancer.modules.skills.model.SkillDef;
import de.raindancer.modules.skills.store.SkillsPack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Artisan, Building, Trading, Enchanting and Exploration: big enough trees of the same shape as every other skill. */
class CraftTreesTest {

    private static SkillDef skill(String id) {
        return SkillCatalog.byId(id).orElseThrow(() -> new AssertionError(id + " is not in the catalog"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"artisan", "building", "trading", "enchanting", "exploration"})
    @DisplayName("20 nodes or more in two or three branches stacked on the canvas, a level 30 at the most")
    void shape(String id) {
        SkillDef skill = skill(id);
        List<TreeBranch> branches = skill.tree().branches();
        assertThat(skill.tree().nodes()).as(id).hasSizeGreaterThanOrEqualTo(20);
        assertThat(branches.size()).as(id).isBetween(2, 3);
        for (TreeNode node : skill.tree().nodes()) {
            int band = branches.indexOf(branches.stream().filter(b -> b.id().equals(node.branch())).findFirst()
                    .orElseThrow());
            assertThat(node.row()).as(node.id() + " sits in its branch's three rows").isBetween(band * 3, band * 3 + 2);
            assertThat(node.levelFor(node.ranks())).as(node.id()).isLessThanOrEqualTo(30);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"artisan", "building", "trading", "enchanting", "exploration"})
    @DisplayName("every branch: one choice pair that shuts each other, one capstone, one free badge doing something")
    void everyBranch(String id) {
        SkillDef skill = skill(id);
        for (TreeBranch branch : skill.tree().branches()) {
            List<TreeNode> nodes = skill.tree().nodes().stream().filter(n -> n.branch().equals(branch.id())).toList();
            List<TreeNode> choices = nodes.stream().filter(TreeNode::choice).toList();
            assertThat(choices).as(branch.id() + " choices").hasSize(2);
            assertThat(choices.get(0).excludes()).containsExactly(choices.get(1).id());
            assertThat(choices.get(1).excludes()).containsExactly(choices.get(0).id());
            assertThat(nodes.stream().filter(TreeNode::capstone)).as(branch.id() + " capstone").hasSize(1);
            List<TreeNode> badges = skill.tree().masteries(branch.id());
            assertThat(badges).as(branch.id()).hasSize(1);
            assertThat(badges.getFirst().cost()).isZero();
            assertThat(badges.getFirst().effects()).as("a mastery brings a feature").isNotEmpty();
            assertThat(SkillsPack.treeBackdrop(branch.id())).as(branch.id() + " has a painting").isNotNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"artisan", "building", "trading", "enchanting", "exploration"})
    @DisplayName("every effect of the skill is used by a node, and reads as a line")
    void everyEffectUsed(String id) {
        SkillDef skill = skill(id);
        Set<String> used = new HashSet<>();
        skill.tree().nodes().forEach(node -> used.addAll(node.effects().keySet()));
        assertThat(used).containsAll(skill.effects().keySet());
        for (Effect effect : skill.effects().values()) {
            assertThat(effect.describe(5)).as(effect.id()).doesNotContain("%s").doesNotContain("%%");
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName("no effect id is claimed by two kinds of effect")
    void effectIdsUnique() {
        Map<String, Class<?>> owners = new HashMap<>();
        for (SkillDef skill : SkillCatalog.all()) {
            for (Effect effect : skill.effects().values()) {
                Class<?> before = owners.putIfAbsent(effect.id(), effect.getClass());
                assertThat(before == null || before == effect.getClass())
                        .as(effect.id() + " is " + before + " and " + effect.getClass()).isTrue();
            }
        }
    }
}
