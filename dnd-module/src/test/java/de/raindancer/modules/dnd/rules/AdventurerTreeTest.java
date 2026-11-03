package de.raindancer.modules.dnd.rules;

import de.raindancer.core.ui.tree.SkillTree;
import de.raindancer.core.ui.tree.TreeGrid;
import de.raindancer.core.ui.tree.TreeNode;
import de.raindancer.modules.dnd.model.AdventurerTree;
import de.raindancer.modules.dnd.model.Perk;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdventurerTreeTest {

    private final SkillTree tree = AdventurerTree.shipped();

    @Test
    @DisplayName("five branches of fifteen nodes, one root, one choice pair and one capstone each")
    void shape() {
        assertThat(tree.branches()).extracting(b -> b.id())
                .containsExactly("vitality", "might", "marksman", "shadow", "arcana");
        for (var branch : tree.branches()) {
            List<TreeNode> nodes = tree.nodesOf(branch.id());
            assertThat(nodes).as(branch.id()).hasSize(15);
            assertThat(nodes.stream().filter(node -> node.requires().isEmpty())).as(branch.id()).hasSize(1);
            assertThat(nodes.stream().filter(TreeNode::capstone)).as(branch.id()).hasSize(1);
            assertThat(nodes.stream().filter(TreeNode::choice)).as(branch.id()).hasSize(2);
        }
    }

    @Test
    @DisplayName("no two lines of a branch share a slot")
    void linesNeverCross() {
        for (var branch : tree.branches()) {
            Set<Integer> used = new HashSet<>();
            for (TreeNode node : tree.nodesOf(branch.id())) {
                for (String needed : node.requires()) {
                    TreeNode other = tree.node(needed).orElseThrow();
                    var link = TreeGrid.link(other.column(), other.row(), node.column(), node.row());
                    assertThat(used.add(link.slotRow() * 9 + link.slotColumn())).as(needed + " to " + node.id()).isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("Dual Wielder is a choice in Might, shutting Great Weapon Master")
    void dualWielder() {
        TreeNode dual = tree.node("dual_wielder").orElseThrow();
        assertThat(dual.branch()).isEqualTo("might");
        assertThat(dual.choice()).isTrue();
        assertThat(dual.excludes()).containsExactly("great_weapon");
        assertThat(dual.effects()).containsKey(Perk.DUAL_WIELD.id());
    }

    @Test
    @DisplayName("every effect is used, every icon is an item, every node does something, nothing past level 30")
    void everythingUsed() {
        Set<String> used = new HashSet<>();
        for (TreeNode node : tree.nodes()) {
            assertThat(node.effects()).as(node.id()).isNotEmpty();
            used.addAll(node.effects().keySet());
            assertThat(Material.matchMaterial(node.icon())).as(node.id() + " icon " + node.icon()).isNotNull();
            assertThat(node.levelFor(node.ranks())).as(node.id()).isLessThanOrEqualTo(30);
        }
        for (Perk perk : Perk.values()) {
            assertThat(used).as(perk.id()).contains(perk.id());
        }
    }

    @Test
    @DisplayName("a branch costs about thirty-five points: a hundred levels fill about three of five")
    void branchCost() {
        for (var branch : tree.branches()) {
            Map<String, Integer> all = new HashMap<>();
            tree.nodesOf(branch.id()).forEach(node -> all.put(node.id(), node.ranks()));
            assertThat(tree.spent(all)).as(branch.id()).isBetween(30, 40);
        }
    }

    @Test
    @DisplayName("every effect reads as a sentence")
    void sentences() {
        assertThat(Perk.HEARTS.describe(2)).isEqualTo("+2 heart(s)");
        assertThat(Perk.MELEE_DAMAGE.describe(12)).isEqualTo("12% more melee damage");
        assertThat(Perk.REACH.describe(0.5)).isEqualTo("+0.5 block(s) of reach");
        assertThat(Perk.DUAL_WIELD.describe(1)).startsWith("Dual Wielder: ");
        for (Perk perk : Perk.values()) {
            assertThat(perk.describe(1)).as(perk.id()).doesNotContain("%s").doesNotContain("%%");
        }
    }
}
