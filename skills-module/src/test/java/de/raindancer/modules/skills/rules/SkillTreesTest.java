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
}
