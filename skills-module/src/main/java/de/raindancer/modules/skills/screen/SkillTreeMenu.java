package de.raindancer.modules.skills.screen;

import de.raindancer.core.platform.util.Times;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.tree.NodeState;
import de.raindancer.core.ui.tree.SkillTree;
import de.raindancer.core.ui.tree.TreeBranch;
import de.raindancer.core.ui.tree.TreeLook;
import de.raindancer.core.ui.tree.TreeMenu;
import de.raindancer.core.ui.tree.TreeNode;
import de.raindancer.modules.skills.SkillsServices;
import de.raindancer.modules.skills.model.Hero;
import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.model.SkillDef;
import de.raindancer.modules.skills.rules.SkillRule;
import de.raindancer.modules.skills.store.SkillsPack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * One skill's tree: a canvas the arrows scroll, a click takes the next rank of a node. The middle button turns to the
 * next branch; shift-clicked, it unlearns this skill's tree.
 */
public final class SkillTreeMenu extends TreeMenu implements ISkillsScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final SkillsServices services;
    private final SkillDef skill;
    private int branchShown;

    public SkillTreeMenu(SkillsServices services, Player viewer, Menu parent, SkillDef skill) {
        super(viewer, services.brand(), parent);
        this.services = services;
        this.skill = skill;
    }

    private SkillRule rule() {
        return services.perks();
    }

    private SkillTree tree() {
        return skill.tree();
    }

    private Hero hero() {
        return services.heroes().of(viewer.getUniqueId()).orElseGet(() -> Hero.fresh(viewer.getUniqueId()));
    }

    @Override
    protected int pages() {
        return 1;
    }

    @Override
    protected Page page(int index) {
        Hero hero = hero();
        Map<String, Integer> ranks = hero.nodesOf(skill.id());
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        for (TreeNode node : tree().nodes()) {
            NodeState state = tree().state(ranks, node);
            nodes.add(new Node(node.id(), node.column(), node.row(), state, icon(hero, ranks, node, state),
                    node.mastery()));
            for (String needed : node.requires()) {
                edges.add(new Edge(needed, node.id()));
            }
        }
        String scene = tree().branches().isEmpty() ? skill.id() : tree().branches().getFirst().id();
        return new Page(MINI.deserialize("<dark_gray>" + skill.title()), nodes, edges,
                SkillsPack.treeBackdrop(scene), summary(hero));
    }

    @Override
    protected TreeLook look() {
        return SkillsPack.TREE_LOOK;
    }

    private ItemStack icon(Hero hero, Map<String, Integer> ranks, TreeNode node, NodeState state) {
        int rank = ranks.getOrDefault(node.id(), 0);
        String colour = switch (state) {
            case MAXED -> "<gold>";
            case OWNED -> "<yellow>";
            case OPEN -> "<green>";
            case LOCKED -> "<gray>";
            case BARRED -> "<dark_red>";
        };
        List<String> lore = new ArrayList<>();
        String kind = node.mastery() ? "Mastery — earned by finishing the branch"
                : node.capstone() ? "Capstone" : node.choice() ? "Choice — shuts the other side" : null;
        if (kind != null) {
            lore.add("<gold>" + kind);
        }
        int shown = Math.max(1, Math.min(rank, node.ranks()));
        node.effects().forEach((id, value) -> {
            Effect effect = skill.effects().get(id);
            if (effect != null) {
                lore.add("<gray>" + effect.describe(value * shown));
            }
        });
        if (node.ranks() > 1 && rank < node.ranks()) {
            lore.add("<dark_gray>Each rank: " + String.join(", ", node.effects().entrySet().stream()
                    .filter(entry -> skill.effects().containsKey(entry.getKey()))
                    .map(entry -> skill.effects().get(entry.getKey()).describe(entry.getValue())
                            .toLowerCase(Locale.ROOT))
                    .toList()));
        }
        lore.add("");
        lore.add("<gray>Rank <white>" + rank + "<gray>/" + node.ranks() + (node.cost() == 0 ? " · <white>free"
                : " · <white>" + node.cost() + "<gray> point" + (node.cost() == 1 ? "" : "s") + " a rank"));
        if (rank < node.ranks() && node.levelFor(rank + 1) > 0) {
            lore.add("<gray>Needs adventurer level <white>" + node.levelFor(rank + 1));
        }
        Optional<SkillRule.Refusal> refusal = rule().refusal(hero, skill, node.id());
        lore.add(refusal.map(why -> "<red>" + reason(why, node, rank)).orElse("<yellow>Click to learn the next rank."));
        Material material = Optional.ofNullable(Material.matchMaterial(node.icon())).orElse(Material.PAPER);
        ItemStack icon = Icons.of(material, colour + node.title()
                + (node.ranks() > 1 ? " <white>" + rank + "<gray>/" + node.ranks() : ""), lore);
        if (rank > 0) {
            icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return icon;
    }

    static String reason(SkillRule.Refusal why, TreeNode node, int rank) {
        return switch (why) {
            case UNKNOWN -> "Not in the tree.";
            case MAXED -> "At its highest rank.";
            case BARRED -> "Shut: you took the other side of this choice.";
            case NEEDS -> "Needs a node next to it first.";
            case UNFINISHED -> "Finish every other node of the branch first.";
            case LEVEL -> "Needs adventurer level " + node.levelFor(rank + 1) + ".";
            case NO_POINTS -> "Not enough skill points.";
        };
    }

    private ItemStack summary(Hero hero) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + skill.blurb());
        lore.add("");
        lore.add("<gray>Adventurer level <white>" + hero.level());
        lore.add("<green>Skill points to spend: <white>" + rule().left(hero) + "<gray> of " + rule().points(hero)
                + (rule().points(hero) >= rule().cap() ? " <dark_gray>(the most)" : ""));
        lore.add("<gray>Spent in " + skill.title() + ": <white>" + rule().spentIn(hero, skill));
        int branches = rule().masteryBranches(skill).size();
        if (branches > 0) {
            lore.add("<gold>Mastered: <white>" + rule().mastered(hero, skill) + "<gray> of " + branches + " branch"
                    + (branches == 1 ? "" : "es"));
        }
        if (tree().branches().size() > 1) {
            lore.add("");
            lore.add("<yellow>Click<gray> for the next branch.");
        }
        if (!hero.nodesOf(skill.id()).isEmpty()) {
            long again = rule().unlearnAgainAt(hero) - System.currentTimeMillis();
            lore.add(again > 0 ? "<dark_gray>Unlearn again in " + Times.describe(Duration.ofMillis(again)) + "."
                    : "<yellow>Shift-click<gray> to unlearn " + skill.title() + ".");
        }
        return Icons.of(skill.icon(), "<gold>" + skill.title(), lore);
    }

    @Override
    protected void onNode(Node node, InventoryClickEvent event) {
        Optional<SkillRule.Refusal> refused = services.heroes().learn(viewer, skill, node.id());
        String title = tree().node(node.id()).map(TreeNode::title).orElse(node.id());
        if (refused.isPresent()) {
            services.effects().play(viewer.getUniqueId(), Cues.NO);
            int rank = hero().rank(skill.key(node.id()));
            services.messages().send(viewer, "dnd.tree.refused", "node", title,
                    "reason", tree().node(node.id()).map(found -> reason(refused.get(), found, rank)).orElse(""));
            return;
        }
        boolean badge = tree().node(node.id()).map(TreeNode::mastery).orElse(false);
        services.effects().play(viewer.getUniqueId(), badge ? Cues.EARNED : Cues.OK);
        services.messages().send(viewer, badge ? "dnd.tree.mastered" : "dnd.tree.learnt", "node", title,
                "skill", skill.title(), "rank", String.valueOf(hero().rank(skill.key(node.id()))));
        reopen();
    }

    @Override
    protected void onSummary(InventoryClickEvent event) {
        Hero hero = hero();
        if (!event.isShiftClick()) {
            List<TreeBranch> branches = tree().branches();
            if (branches.size() > 1) {
                branchShown = (branchShown + 1) % branches.size();
                int[] start = start(branches.get(branchShown).id());
                showAt(start[0], start[1]);
            }
            return;
        }
        if (hero.nodesOf(skill.id()).isEmpty()) {
            return;
        }
        if (!rule().mayUnlearn(hero, skill, System.currentTimeMillis())) {
            services.effects().play(viewer.getUniqueId(), Cues.NO);
            return;
        }
        new ConfirmScreen(viewer, services.brand(), this, "<red>Unlearn " + skill.title() + "?",
                List.of("<gray>Every point spent in it comes back.",
                        "<gray>Once every " + services.config().unlearnHours() + " hour(s), whichever skill."),
                () -> {
                    if (services.heroes().unlearn(viewer, skill, System.currentTimeMillis())) {
                        services.messages().send(viewer, "dnd.tree.unlearnt", "skill", skill.title());
                    }
                    reopen();
                }).open();
    }

    /** A branch's first node: its leftmost, topmost. */
    private int[] start(String branch) {
        int column = Integer.MAX_VALUE;
        int row = Integer.MAX_VALUE;
        for (TreeNode node : tree().nodesOf(branch)) {
            if (node.column() < column || (node.column() == column && node.row() < row)) {
                column = node.column();
                row = node.row();
            }
        }
        return new int[]{column == Integer.MAX_VALUE ? 0 : column, row == Integer.MAX_VALUE ? 0 : row};
    }

    @Override
    public String breadcrumb() {
        return skill.title();
    }

    @Override
    protected List<String> helpLines() {
        return List.of(
                "The arrows below scroll the tree; shift-click",
                "them for a whole window. Click a node to learn its",
                "next rank: a node needs one next to it first, a",
                "choice shuts its other side. A branch ends in a",
                "mastery badge, free once the rest of it is done.",
                "",
                "A skill point every adventurer level, up to the",
                "most, for any skill's tree — choose what you are.",
                "Shift-click the middle button to unlearn this skill.");
    }

    @Override
    public String describe() {
        return "one skill's tree and the points to spend on it";
    }
}
