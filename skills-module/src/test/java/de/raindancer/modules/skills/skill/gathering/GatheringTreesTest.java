package de.raindancer.modules.skills.skill.gathering;

import de.raindancer.core.ui.tree.TreeBranch;
import de.raindancer.core.ui.tree.TreeNode;
import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.model.Perk;
import de.raindancer.modules.skills.model.SkillCatalog;
import de.raindancer.modules.skills.model.SkillDef;
import de.raindancer.modules.skills.store.SkillsPack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The four gathering skills' trees: big enough, stacked on the canvas, each branch a whole branch. */
class GatheringTreesTest {

    private static SkillDef skill(String id) {
        return SkillCatalog.byId(id).orElseThrow(() -> new AssertionError(id + " is not in the catalog"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"mining", "excavation", "woodcutting", "smithing"})
    @DisplayName("twenty nodes or more in two or three branches, stacked three rows each")
    void shape(String id) {
        SkillDef skill = skill(id);
        assertThat(skill.tree().nodes()).as(id).hasSizeGreaterThanOrEqualTo(20);
        List<TreeBranch> branches = skill.tree().branches();
        assertThat(branches).as(id).hasSizeBetween(2, 3);
        for (int index = 0; index < branches.size(); index++) {
            String branch = branches.get(index).id();
            List<TreeNode> nodes = skill.tree().nodes().stream().filter(node -> node.branch().equals(branch))
                    .toList();
            for (TreeNode node : nodes) {
                assertThat(node.row()).as(node.id() + " of branch " + index).isBetween(index * 3, index * 3 + 2);
                assertThat(node.levelFor(node.ranks())).as(node.id() + "'s last rank").isLessThanOrEqualTo(30);
            }
            List<TreeNode> choices = nodes.stream().filter(TreeNode::choice).toList();
            assertThat(choices).as(branch + "'s choice").hasSize(2);
            assertThat(choices.get(0).excludes()).contains(choices.get(1).id());
            assertThat(choices.get(1).excludes()).contains(choices.get(0).id());
            assertThat(nodes.stream().filter(TreeNode::capstone)).as(branch + "'s capstone").hasSize(1);
            List<TreeNode> badges = skill.tree().masteries(branch);
            assertThat(badges).as(branch + "'s badge").hasSize(1);
            assertThat(badges.getFirst().cost()).isZero();
            assertThat(SkillsPack.treeBackdrop(branch)).as(branch + " has a painting").isNotNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"mining", "excavation", "woodcutting", "smithing"})
    @DisplayName("every effect of the skill is in its tree, and every mastery badge does something of its own")
    void everyEffectIsUsed(String id) {
        SkillDef skill = skill(id);
        Set<String> used = new HashSet<>();
        skill.tree().nodes().forEach(node -> used.addAll(node.effects().keySet()));
        assertThat(used).containsAll(skill.effects().keySet());
        for (TreeBranch branch : skill.tree().branches()) {
            TreeNode badge = skill.tree().masteries(branch.id()).getFirst();
            Set<String> elsewhere = new HashSet<>();
            skill.tree().nodes().stream().filter(node -> !node.mastery())
                    .forEach(node -> elsewhere.addAll(node.effects().keySet()));
            assertThat(badge.effects().keySet()).as(badge.id() + " brings a feature no other node has")
                    .anyMatch(effect -> !elsewhere.contains(effect));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"mining", "excavation", "woodcutting", "smithing"})
    @DisplayName("effect ids are the skill's own, never another skill's or a fighting perk's")
    void ownEffectIds(String id) {
        SkillDef skill = skill(id);
        for (Effect effect : skill.effects().values()) {
            assertThat(Perk.known(effect.id())).as(effect.id()).isFalse();
            for (SkillDef other : SkillCatalog.all()) {
                if (other != skill) {
                    assertThat(other.effects()).as(effect.id() + " also in " + other.id())
                            .doesNotContainKey(effect.id());
                }
            }
            assertThat(effect.describe(effect.cap() <= 1 ? 1 : 5)).doesNotContain("%s");
        }
    }
}
