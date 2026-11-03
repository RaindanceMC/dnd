package de.raindancer.modules.dnd.screen;

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
import de.raindancer.modules.dnd.DndServices;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.Perk;
import de.raindancer.modules.dnd.rules.PerkRule;
import de.raindancer.modules.dnd.store.DndPack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The adventurer's skill tree: one page per branch, a click takes the next rank of a node. */
public final class SkillTreeMenu extends TreeMenu implements IDndScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final DndServices services;

    public SkillTreeMenu(DndServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    private PerkRule rule() {
        return services.perks();
    }

    private SkillTree tree() {
        return rule().tree();
    }

    private Hero hero() {
        return services.heroes().of(viewer.getUniqueId()).orElseGet(() -> Hero.fresh(viewer.getUniqueId()));
    }

    @Override
    protected int pages() {
        return tree().branches().size();
    }

    @Override
    protected Page page(int index) {
        TreeBranch branch = tree().branches().get(index);
        Hero hero = hero();
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        for (TreeNode node : tree().nodesOf(branch.id())) {
            NodeState state = tree().state(hero.nodes(), node);
            nodes.add(new Node(node.id(), node.column(), node.row(), state, icon(hero, node, state)));
            for (String needed : node.requires()) {
                edges.add(new Edge(needed, node.id()));
            }
        }
        return new Page(MINI.deserialize("<dark_gray>" + branch.title()), nodes, edges,
                DndPack.treeBackdrop(branch.id()), summary(hero, branch));
    }

    @Override
    protected TreeLook look() {
        return DndPack.TREE_LOOK;
    }

    private ItemStack icon(Hero hero, TreeNode node, NodeState state) {
        int rank = hero.rank(node.id());
        String colour = switch (state) {
            case MAXED -> "<gold>";
            case OWNED -> "<yellow>";
            case OPEN -> "<green>";
            case LOCKED -> "<gray>";
            case BARRED -> "<dark_red>";
        };
        List<String> lore = new ArrayList<>();
        String kind = node.capstone() ? "Capstone" : node.choice() ? "Choice — shuts the other side" : null;
        if (kind != null) {
            lore.add("<gold>" + kind);
        }
        int shown = Math.max(1, Math.min(rank, node.ranks()));
        node.effects().forEach((id, value) -> Perk.parse(id).ifPresent(perk ->
                lore.add("<gray>" + perk.describe(value * shown))));
        if (node.ranks() > 1 && rank < node.ranks()) {
            lore.add("<dark_gray>Each rank: " + String.join(", ", node.effects().entrySet().stream()
                    .flatMap(entry -> Perk.parse(entry.getKey()).stream()
                            .map(perk -> perk.describe(entry.getValue()).toLowerCase(Locale.ROOT)))
                    .toList()));
        }
        lore.add("");
        lore.add("<gray>Rank <white>" + rank + "<gray>/" + node.ranks() + " · <white>" + node.cost()
                + "<gray> point" + (node.cost() == 1 ? "" : "s") + " a rank");
        if (rank < node.ranks() && node.levelFor(rank + 1) > 0) {
            lore.add("<gray>Needs adventurer level <white>" + node.levelFor(rank + 1));
        }
        Optional<PerkRule.Refusal> refusal = rule().refusal(hero, node.id());
        lore.add(refusal.map(why -> "<red>" + reason(why, node, rank)).orElse("<yellow>Click to learn the next rank."));
        Material material = Optional.ofNullable(Material.matchMaterial(node.icon())).orElse(Material.PAPER);
        ItemStack icon = Icons.of(material, colour + node.title()
                + (node.ranks() > 1 ? " <white>" + rank + "<gray>/" + node.ranks() : ""), lore);
        if (rank > 0) {
            icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return icon;
    }

    private static String reason(PerkRule.Refusal why, TreeNode node, int rank) {
        return switch (why) {
            case UNKNOWN -> "Not in the tree.";
            case MAXED -> "At its highest rank.";
            case BARRED -> "Shut: you took the other side of this choice.";
            case NEEDS -> "Needs a node next to it first.";
            case LEVEL -> "Needs adventurer level " + node.levelFor(rank + 1) + ".";
            case NO_POINTS -> "Not enough skill points.";
        };
    }

    private ItemStack summary(Hero hero, TreeBranch branch) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + branch.blurb());
        lore.add("");
        lore.add("<gray>Adventurer level <white>" + hero.level());
        lore.add("<green>Skill points to spend: <white>" + rule().left(hero) + "<gray> of " + rule().points(hero));
        lore.add("<gray>A point every level; levels come");
        lore.add("<gray>from defeating monsters.");
        lore.add("<gray>Spent in this branch: <white>" + tree().spentIn(hero.nodes(), branch.id()));
        if (!hero.nodes().isEmpty()) {
            lore.add("");
            long again = rule().unlearnAgainAt(hero) - System.currentTimeMillis();
            lore.add(again > 0 ? "<dark_gray>Unlearn again in " + Times.describe(Duration.ofMillis(again)) + "."
                    : "<yellow>Shift-click<gray> to unlearn the whole tree.");
        }
        Material icon = Optional.ofNullable(Material.matchMaterial(branch.icon())).orElse(Material.PAPER);
        return Icons.of(icon, "<gold>" + branch.title(), lore);
    }

    @Override
    protected void onNode(Node node, InventoryClickEvent event) {
        Optional<PerkRule.Refusal> refused = services.heroes().learn(viewer, node.id());
        String title = tree().node(node.id()).map(TreeNode::title).orElse(node.id());
        if (refused.isPresent()) {
            services.effects().play(viewer.getUniqueId(), Cues.NO);
            int rank = hero().rank(node.id());
            services.messages().send(viewer, "dnd.tree.refused", "node", title,
                    "reason", tree().node(node.id()).map(found -> reason(refused.get(), found, rank)).orElse(""));
            return;
        }
        services.effects().play(viewer.getUniqueId(), Cues.OK);
        services.messages().send(viewer, "dnd.tree.learnt", "node", title,
                "rank", String.valueOf(hero().rank(node.id())));
        reopen();
    }

    @Override
    protected void onSummary(InventoryClickEvent event) {
        Hero hero = hero();
        if (!event.isShiftClick() || hero.nodes().isEmpty()) {
            return;
        }
        if (!rule().mayUnlearn(hero, System.currentTimeMillis())) {
            services.effects().play(viewer.getUniqueId(), Cues.NO);
            return;
        }
        new ConfirmScreen(viewer, services.brand(), this, "<red>Unlearn the whole skill tree?",
                List.of("<gray>Every point comes back to spend again.",
                        "<gray>Once every " + services.config().unlearnHours() + " hour(s)."),
                () -> {
                    if (services.heroes().unlearn(viewer, System.currentTimeMillis())) {
                        services.messages().send(viewer, "dnd.tree.unlearnt");
                    }
                    reopen();
                }).open();
    }

    @Override
    public String breadcrumb() {
        return "Skill tree";
    }

    @Override
    protected List<String> helpLines() {
        return List.of(
                "Five branches; the arrows below turn between them.",
                "Click a node to learn its next rank. A node needs",
                "one next to it first; a choice shuts its other side.",
                "",
                "A skill point every adventurer level; levels come",
                "from defeating monsters.",
                "Shift-click the middle button to unlearn everything.");
    }

    @Override
    public String describe() {
        return "the adventurer's skill tree and the points to spend on it";
    }
}
