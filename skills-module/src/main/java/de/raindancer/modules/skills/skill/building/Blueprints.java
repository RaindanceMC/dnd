package de.raindancer.modules.skills.skill.building;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.core.world.build.BatchBuilder;
import de.raindancer.core.world.build.BukkitGround;
import de.raindancer.core.world.build.Ground;
import de.raindancer.core.world.build.Schematic;
import de.raindancer.core.world.build.SchematicCapture;
import de.raindancer.core.world.geometry.ColumnPolygon.Column;
import de.raindancer.core.world.protection.Land;
import de.raindancer.core.world.protection.LandAction;
import de.raindancer.core.world.protection.LockedWorlds;
import de.raindancer.core.world.safety.Spot;
import de.raindancer.core.world.selection.MarkingListener;
import de.raindancer.core.world.selection.MarkingSession;
import de.raindancer.core.world.selection.MarkingSessions;
import de.raindancer.core.world.selection.MarkingTool;
import de.raindancer.modules.skills.skill.SkillValues;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * Building's blueprints, by {@code /skills blueprint}: a box marked with Core's marking tool, read with
 * {@link SchematicCapture} and kept in the skills database; a paste turned into {@link BatchBuilder} placements and put
 * up a batch a tick, paid for from the player's inventory first (see {@link PasteRule}). Nothing pasted earns
 * experience: no block is placed through an event.
 *
 * <p>Not Core's {@code PasteJob}: that one places over whatever stands there and cannot tell which blocks went in.
 * A {@link BatchBuilder} on a ground that re-checks every spot as it places keeps "never remove what stands there"
 * true even when the world changed since the plan, and its snapshot is exactly what an undo gives back.
 */
public final class Blueprints implements MarkingListener.Callback {

    /** What the marker is for, written into it. */
    public static final String PURPOSE = "skills-blueprint";
    private static final int BATCH = 2_048;
    private static final int GHOST_SECONDS = 20;
    private static final int MOST_GHOST = 4_096;
    private static final long UNDO_MILLIS = 10 * 60_000L;
    private static final int UNDO_REACH = 48;

    private record Pending(String name, int quarters, Spot feet, long until) {
    }

    private record Done(Spot spot, String placed, String before, Map<String, Integer> paid, boolean free) {
    }

    private record Undo(String name, Spot origin, List<Done> done, long at) {
    }

    private final Plugin plugin;
    private final SkillValues values;
    private final BlueprintBook book;
    private final BlueprintRule rule;
    private final PasteRule paste;
    private final Messages messages;
    private final Supplier<Land> land;
    private final MarkingTool tool;
    private final MarkingSessions sessions;
    private final java.util.function.Predicate<UUID> mayChange;
    private final RandomGenerator random;
    private final LongSupplier clock;

    private final Map<UUID, BlueprintRule.Box> marked = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> ghosts = new ConcurrentHashMap<>();
    private final Map<UUID, Undo> undos = new ConcurrentHashMap<>();
    private final Set<UUID> pasting = ConcurrentHashMap.newKeySet();

    public Blueprints(Plugin plugin, SkillValues values, BlueprintBook book, BlueprintRule rule, PasteRule paste,
                      Messages messages, Supplier<Land> land, MarkingTool tool, MarkingSessions sessions,
                      java.util.function.Predicate<UUID> mayChange, RandomGenerator random, LongSupplier clock) {
        this.plugin = plugin;
        this.values = values;
        this.book = book;
        this.rule = rule;
        this.paste = paste;
        this.messages = messages;
        this.land = land;
        this.tool = tool;
        this.sessions = sessions;
        this.mayChange = mayChange;
        this.random = random;
        this.clock = clock;
    }

    public MarkingSessions sessions() {
        return sessions;
    }

    public void forget(UUID player) {
        marked.remove(player);
        ghosts.remove(player);
        undos.remove(player);
        sessions.clear(player);
    }

    private int edge(Player player) {
        return (int) Math.round(values.value(player, BuildingEffect.BLUEPRINT_SIZE));
    }

    private int slots(Player player) {
        return (int) Math.round(values.value(player, BuildingEffect.BLUEPRINT_SLOTS));
    }

    private static Spot feet(Player player) {
        Location at = player.getLocation();
        return new Spot(at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }

    // ------------------------------------------------------------------ marking

    public void mark(Player player) {
        if (edge(player) <= 0) {
            messages.send(player, "dnd.blueprint.no-draftsman");
            return;
        }
        sessions.begin(player.getUniqueId(), player.getWorld().getName(), MarkingSession.Mode.PATH);
        marked.remove(player.getUniqueId());
        tool.revoke(player);
        tool.give(player, tool.create(Material.GOLDEN_SHOVEL, PURPOSE, messages.raw("dnd.blueprint.tool-name"),
                List.of(messages.raw("dnd.blueprint.tool-lore"))));
        messages.send(player, "dnd.blueprint.marking", "edge", String.valueOf(edge(player)));
    }

    @Override
    public void onVertexAdded(Player player, MarkingSession session) {
        int count = session.pointCount();
        if (count < 2) {
            messages.send(player, "dnd.blueprint.corner");
            return;
        }
        List<Column> corners = session.vertices();
        Column a = corners.get(count - 2);
        Column b = corners.get(count - 1);
        int ay = session.clickedYAt(count - 2).orElse(player.getLocation().getBlockY());
        int by = session.clickedYAt(count - 1).orElse(player.getLocation().getBlockY());
        BlueprintRule.Box box = BlueprintRule.Box.of(new Spot(session.world(), a.x(), ay, a.z()),
                new Spot(session.world(), b.x(), by, b.z()));
        marked.put(player.getUniqueId(), box);
        int edge = edge(player);
        messages.send(player, box.longest() > edge ? "dnd.blueprint.marked-too-big" : "dnd.blueprint.marked",
                size(box.width(), box.height(), box.length(), "edge", String.valueOf(edge)));
    }

    @Override
    public void onVertexRemoved(Player player, MarkingSession session) {
        marked.remove(player.getUniqueId());
        messages.send(player, "dnd.blueprint.corner-removed");
    }

    @Override
    public void onEmpty(Player player) {
        messages.send(player, "dnd.blueprint.mark-help");
    }

    @Override
    public void onCancel(Player player, MarkingSession session) {
        sessions.clear(player.getUniqueId());
        marked.remove(player.getUniqueId());
        tool.revoke(player);
        messages.send(player, "dnd.blueprint.mark-cancelled");
    }

    @Override
    public void onFinish(Player player, MarkingSession session) {
        sessions.clear(player.getUniqueId());
        tool.revoke(player);
        messages.send(player, marked.containsKey(player.getUniqueId()) ? "dnd.blueprint.mark-done"
                : "dnd.blueprint.mark-help");
    }

    private static Object[] size(int width, int height, int length, Object... more) {
        List<Object> all = new ArrayList<>(List.of("width", String.valueOf(width), "height", String.valueOf(height),
                "length", String.valueOf(length)));
        all.addAll(List.of(more));
        return all.toArray();
    }

    // ------------------------------------------------------------------ saving, listing, deleting

    public void save(Player player, String name) {
        BlueprintRule.Box box = marked.get(player.getUniqueId());
        if (box == null) {
            messages.send(player, "dnd.blueprint.nothing-marked");
            return;
        }
        if (!box.world().equals(player.getWorld().getName())) {
            messages.send(player, "dnd.blueprint.other-world");
            return;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        Spot feet = feet(player);
        int edge = edge(player);
        int slots = slots(player);
        double away = box.distanceTo(feet.x(), feet.y(), feet.z());
        World world = player.getWorld();
        Scheduling.async(plugin, () -> {
            List<Blueprint.Summary> saved = book.list(player.getUniqueId());
            boolean replacing = saved.stream().anyMatch(each -> each.name().equals(lower));
            Optional<BlueprintRule.Refusal> refusal = rule.maySave(box, lower, edge, saved.size(), slots, replacing,
                    away);
            if (refusal.isPresent()) {
                Scheduling.entity(plugin, player, () -> refuse(player, refusal.get(), box.width(), box.height(),
                        box.length(), edge, slots));
                return;
            }
            Scheduling.entity(plugin, player, () -> messages.send(player, "dnd.blueprint.saving", "name", lower));
            SchematicCapture.capture(plugin, world, box.x(), box.y(), box.z(), box.width(), box.height(),
                    box.length(), schematic -> {
                        Blueprint blueprint = new Blueprint(lower, schematic, feet.x() - box.x(), feet.y() - box.y(),
                                feet.z() - box.z(), clock.getAsLong());
                        boolean written = book.save(player.getUniqueId(), blueprint);
                        Scheduling.entity(plugin, player, () -> {
                            if (written) {
                                marked.remove(player.getUniqueId());
                            }
                            messages.send(player, written ? "dnd.blueprint.saved" : "dnd.blueprint.unreadable",
                                    size(box.width(), box.height(), box.length(), "name", lower));
                        });
                    });
        });
    }

    private void refuse(Player player, BlueprintRule.Refusal refusal, int width, int height, int length, int edge,
                        int slots) {
        switch (refusal) {
            case NO_DRAFTSMAN -> messages.send(player, "dnd.blueprint.no-draftsman");
            case BAD_NAME -> messages.send(player, "dnd.blueprint.bad-name");
            case TOO_BIG -> messages.send(player, "dnd.blueprint.too-big",
                    size(width, height, length, "edge", String.valueOf(edge)));
            case NO_ROOM -> messages.send(player, "dnd.blueprint.no-room", "slots", String.valueOf(slots));
            case TOO_FAR -> messages.send(player, "dnd.blueprint.too-far",
                    "blocks", String.valueOf(BlueprintRule.MOST_AWAY));
        }
    }

    public void list(Player player) {
        int slots = slots(player);
        Scheduling.async(plugin, () -> {
            List<Blueprint.Summary> saved = book.list(player.getUniqueId());
            Scheduling.entity(plugin, player, () -> {
                if (saved.isEmpty()) {
                    messages.send(player, "dnd.blueprint.list-empty");
                    return;
                }
                messages.send(player, "dnd.blueprint.list-head", "count", String.valueOf(saved.size()),
                        "slots", String.valueOf(slots));
                for (Blueprint.Summary each : saved) {
                    messages.send(player, "dnd.blueprint.list-line",
                            size(each.width(), each.height(), each.length(), "name", each.name()));
                }
            });
        });
    }

    /** Names for tab completion, read on the side so typing never waits on the database. */
    public List<String> names(UUID player) {
        return book.list(player).stream().map(Blueprint.Summary::name).toList();
    }

    public void delete(Player player, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        Scheduling.async(plugin, () -> {
            boolean gone = book.delete(player.getUniqueId(), lower);
            Scheduling.entity(plugin, player, () -> messages.send(player,
                    gone ? "dnd.blueprint.deleted" : "dnd.blueprint.unknown", "name", lower));
        });
    }

    // ------------------------------------------------------------------ pasting

    public void paste(Player player, String name, int quarters) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (quarters != 0 && !values.has(player, BuildingEffect.MASTER_ARCHITECT)) {
            messages.send(player, "dnd.blueprint.turning-is-mastery");
            return;
        }
        if (pasting.contains(player.getUniqueId())) {
            messages.send(player, "dnd.blueprint.busy");
            return;
        }
        Scheduling.async(plugin, () -> {
            Optional<Blueprint> found = book.load(player.getUniqueId(), lower);
            Scheduling.entity(plugin, player, () -> found.ifPresentOrElse(
                    blueprint -> paste(player, blueprint, quarters),
                    () -> messages.send(player, "dnd.blueprint.unknown", "name", lower)));
        });
    }

    private void paste(Player player, Blueprint blueprint, int quarters) {
        Schematic blocks = blueprint.schematic();
        Optional<BlueprintRule.Refusal> refusal = rule.mayPaste(blocks.width(), blocks.height(), blocks.length(),
                edge(player));
        if (refusal.isPresent()) {
            refuse(player, refusal.get(), blocks.width(), blocks.height(), blocks.length(), edge(player), 0);
            return;
        }
        Spot feet = feet(player);
        World world = player.getWorld();
        List<PasteRule.Placed> pieces = pieces(blueprint, feet, quarters);
        long now = clock.getAsLong();
        if (values.has(player, BuildingEffect.MASTER_ARCHITECT)) {
            Pending pending = ghosts.get(player.getUniqueId());
            boolean confirmed = pending != null && pending.until() >= now && pending.name().equals(blueprint.name())
                    && pending.quarters() == quarters && pending.feet().equals(feet);
            if (!confirmed) {
                ghost(player, world, pieces);
                ghosts.put(player.getUniqueId(), new Pending(blueprint.name(), quarters, feet,
                        now + GHOST_SECONDS * 1_000L));
                messages.send(player, "dnd.blueprint.ghost", "name", blueprint.name(),
                        "seconds", String.valueOf(GHOST_SECONDS));
                return;
            }
            ghosts.remove(player.getUniqueId());
        }
        BukkitGround ground = BukkitGround.of(world);
        PasteRule.Site site = new PasteRule.Site() {
            @Override
            public String stateAt(Spot spot) {
                return ground.stateAt(spot);
            }

            @Override
            public boolean holdsMore(Spot spot) {
                return ground.holdsMoreThanItsState(spot);
            }

            @Override
            public boolean unbreakable(Spot spot) {
                return world.getBlockAt(spot.x(), spot.y(), spot.z()).getType().getHardness() < 0;
            }
        };
        // A frozen player builds nothing, by hand or by blueprint.
        boolean frozen = !mayChange.test(player.getUniqueId());
        PasteRule.Plan plan = paste.plan(new PasteRule.Request(pieces, site,
                spot -> !frozen && may(player, world, spot, LandAction.BUILD),
                spot -> may(player, world, spot, LandAction.BREAK),
                values.has(player, BuildingEffect.DEMOLITION), values.value(player, BuildingEffect.BLUEPRINT_THRIFT),
                inventory(player.getInventory())), random);
        if (plan.refused() > 0) {
            messages.send(player, "dnd.blueprint.protected", "name", blueprint.name(),
                    "count", String.valueOf(plan.refused()));
            return;
        }
        if (!plan.missing().isEmpty()) {
            messages.send(player, "dnd.blueprint.missing", "name", blueprint.name(), "list", items(plan.missing()));
            return;
        }
        if (plan.steps().isEmpty()) {
            messages.send(player, "dnd.blueprint.nothing-to-place", "name", blueprint.name());
            return;
        }
        if (!take(player.getInventory(), plan.cost())) {
            messages.send(player, "dnd.blueprint.missing", "name", blueprint.name(), "list", items(plan.cost()));
            return;
        }
        build(player, world, blueprint.name(), feet, plan);
    }

    /** The blueprint's blocks turned about where its maker stood, and put down about where the player stands. */
    private List<PasteRule.Placed> pieces(Blueprint blueprint, Spot feet, int quarters) {
        Schematic blocks = blueprint.schematic();
        StructureRotation rotation = switch (Math.floorMod(quarters, 4)) {
            case 1 -> StructureRotation.CLOCKWISE_90;
            case 2 -> StructureRotation.CLOCKWISE_180;
            case 3 -> StructureRotation.COUNTERCLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
        Map<String, Optional<BlueprintRule.Piece>> byState = new HashMap<>();
        List<PasteRule.Placed> pieces = new ArrayList<>();
        for (BatchBuilder.Placement each : blocks.placements(new Spot(feet.world(), 0, 0, 0))) {
            Optional<BlueprintRule.Piece> piece = byState.computeIfAbsent(each.material(),
                    state -> rule.piece(turned(state, rotation), Blueprints::itemOf));
            if (piece.isEmpty()) {
                continue;
            }
            int[] turned = BlueprintRule.turn(each.spot().x() - blueprint.anchorX(),
                    each.spot().z() - blueprint.anchorZ(), quarters);
            pieces.add(new PasteRule.Placed(new Spot(feet.world(), feet.x() + turned[0],
                    feet.y() + each.spot().y() - blueprint.anchorY(), feet.z() + turned[1]), piece.get()));
        }
        return pieces;
    }

    private static String turned(String state, StructureRotation rotation) {
        if (rotation == StructureRotation.NONE) {
            return state;
        }
        try {
            BlockData data = Bukkit.createBlockData(state);
            data.rotate(rotation);
            return data.getAsString();
        } catch (IllegalArgumentException unknown) {
            return state;
        }
    }

    /** The item a block is placed from, by the block's id. */
    static Optional<String> itemOf(String blockId) {
        Material block = Material.matchMaterial(blockId);
        if (block == null || !block.isBlock()) {
            return Optional.empty();
        }
        Material item = block.createBlockData().getPlacementMaterial();
        return item == null || item.isAir() || !item.isItem() ? Optional.empty()
                : Optional.of(item.getKey().getKey());
    }

    private boolean may(Player player, World world, Spot spot, LandAction action) {
        Location location = new Location(world, spot.x(), spot.y(), spot.z());
        Land claims = land.get();
        if (claims != null && !claims.can(player, location, action)) {
            return false;
        }
        return !LockedWorlds.isLocked(world.getName()) || LockedWorlds.isBuilding(world.getName(), player)
                || LockedWorlds.isOpen(location);
    }

    /** Plain items only — a renamed or enchanted block is never spent on a paste. */
    private static Map<String, Integer> inventory(PlayerInventory inventory) {
        Map<String, Integer> have = new HashMap<>();
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && !stack.isEmpty() && stack.isSimilar(new ItemStack(stack.getType()))) {
                have.merge(stack.getType().getKey().getKey(), stack.getAmount(), Integer::sum);
            }
        }
        return have;
    }

    private static boolean take(PlayerInventory inventory, Map<String, Integer> cost) {
        Map<String, Integer> have = inventory(inventory);
        for (Map.Entry<String, Integer> each : cost.entrySet()) {
            if (have.getOrDefault(each.getKey(), 0) < each.getValue() || Material.matchMaterial(each.getKey()) == null) {
                return false;
            }
        }
        cost.forEach((item, count) -> inventory.removeItem(new ItemStack(Material.matchMaterial(item), count)));
        return true;
    }

    private static List<ItemStack> stacks(Map<String, Integer> items) {
        List<ItemStack> stacks = new ArrayList<>();
        items.forEach((item, count) -> {
            Material material = Material.matchMaterial(item);
            if (material == null) {
                return;
            }
            int left = count;
            while (left > 0) {
                int one = Math.min(left, material.getMaxStackSize());
                stacks.add(new ItemStack(material, one));
                left -= one;
            }
        });
        return stacks;
    }

    private static String items(Map<String, Integer> items) {
        return items.entrySet().stream().map(each -> each.getValue() + " × " + each.getKey().replace('_', ' '))
                .collect(Collectors.joining(", "));
    }

    private static void give(Player player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            player.getInventory().addItem(stack).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private void ghost(Player player, World world, List<PasteRule.Placed> pieces) {
        List<Location> shown = new ArrayList<>();
        for (PasteRule.Placed each : pieces) {
            if (shown.size() >= MOST_GHOST) {
                break;
            }
            Block block = world.getBlockAt(each.spot().x(), each.spot().y(), each.spot().z());
            if (!block.getType().isAir()) {
                continue;
            }
            try {
                player.sendBlockChange(block.getLocation(), Bukkit.createBlockData(each.piece().state()));
                shown.add(block.getLocation());
            } catch (IllegalArgumentException unknown) {
                // A state this server does not know shows as nothing.
            }
        }
        Scheduling.entityLater(plugin, player, GHOST_SECONDS * 20L, () -> {
            for (Location location : shown) {
                player.sendBlockChange(location, location.getBlock().getBlockData());
            }
        });
    }

    /**
     * Places the plan a batch a tick on the region the paste stands in. Each spot is looked at again as it is
     * placed: if the world changed there since the plan, it is left alone and its items go back.
     */
    private void build(Player player, World world, String name, Spot origin, PasteRule.Plan plan) {
        UUID id = player.getUniqueId();
        pasting.add(id);
        messages.send(player, "dnd.blueprint.pasting", "name", name, "blocks", String.valueOf(plan.steps().size()));
        Map<Spot, PasteRule.Step> bySpot = new LinkedHashMap<>();
        plan.steps().forEach(step -> bySpot.put(step.spot(), step));
        List<ItemStack> cleared = new ArrayList<>();
        ItemStack tool = player.getInventory().getItemInMainHand().clone();
        BukkitGround bukkit = BukkitGround.of(world);
        Ground guarded = new Ground() {
            @Override
            public String materialAt(Spot spot) {
                return bukkit.materialAt(spot);
            }

            @Override
            public boolean set(Spot spot, String material) {
                PasteRule.Step step = bySpot.get(spot);
                String there = bukkit.stateAt(spot);
                if (step == null || there == null) {
                    return false;
                }
                boolean expected = step.replaced() == null ? PasteRule.air(there) : there.equals(step.replaced());
                if (!expected) {
                    return false;
                }
                if (step.replaced() != null) {
                    cleared.addAll(world.getBlockAt(spot.x(), spot.y(), spot.z()).getDrops(tool, player));
                }
                return bukkit.set(spot, material);
            }

            @Override
            public String stateAt(Spot spot) {
                return bukkit.stateAt(spot);
            }

            @Override
            public boolean holdsMoreThanItsState(Spot spot) {
                return bukkit.holdsMoreThanItsState(spot);
            }

            @Override
            public boolean isLoaded(Spot spot) {
                return bukkit.isLoaded(spot);
            }
        };
        List<BatchBuilder.Placement> placements = plan.steps().stream()
                .map(step -> new BatchBuilder.Placement(step.spot(), step.state())).toList();
        BatchBuilder builder = new BatchBuilder(guarded, placements);
        Location at = new Location(world, origin.x(), origin.y(), origin.z());
        Scheduling.regionTimer(plugin, at, 1L, 1L, task -> {
            builder.advance(BATCH);
            if (!builder.isDone()) {
                return;
            }
            task.cancel();
            List<Done> done = new ArrayList<>();
            Map<String, Integer> back = new LinkedHashMap<>();
            Set<Spot> covered = new java.util.HashSet<>();
            for (var placement : builder.snapshotSoFar().placements()) {
                covered.add(placement.spot());
                PasteRule.Step step = bySpot.get(placement.spot());
                done.add(new Done(step.spot(), step.state(), placement.material(), step.paid(), step.free()));
                if (step.free()) {
                    FreeBlocks.mark(world.getBlockAt(step.spot().x(), step.spot().y(), step.spot().z()));
                }
            }
            for (PasteRule.Step step : plan.steps()) {
                if (!covered.contains(step.spot())) {
                    step.paid().forEach((item, count) -> back.merge(item, count, Integer::sum));
                }
            }
            List<ItemStack> returned = stacks(back);
            returned.addAll(cleared);
            Runnable finish = () -> {
                pasting.remove(id);
                give(player, returned);
                undos.put(id, new Undo(name, origin, List.copyOf(done), clock.getAsLong()));
                int free = (int) done.stream().filter(Done::free).count();
                messages.send(player, "dnd.blueprint.pasted", "name", name, "placed", String.valueOf(done.size()),
                        "free", String.valueOf(free), "skipped",
                        String.valueOf(plan.occupied() + plan.steps().size() - done.size()));
            };
            Scheduling.entity(plugin, player, finish, () -> {
                pasting.remove(id);
                returned.forEach(stack -> world.dropItemNaturally(at, stack));
            });
        });
    }

    // ------------------------------------------------------------------ undo

    public void undo(Player player) {
        Undo last = undos.get(player.getUniqueId());
        if (last == null) {
            messages.send(player, "dnd.blueprint.nothing-to-undo");
            return;
        }
        if (clock.getAsLong() - last.at() > UNDO_MILLIS) {
            undos.remove(player.getUniqueId());
            messages.send(player, "dnd.blueprint.undo-expired");
            return;
        }
        Spot here = feet(player);
        if (!here.world().equals(last.origin().world())
                || Math.abs(here.x() - last.origin().x()) > UNDO_REACH
                || Math.abs(here.z() - last.origin().z()) > UNDO_REACH) {
            messages.send(player, "dnd.blueprint.undo-too-far", "name", last.name());
            return;
        }
        undos.remove(player.getUniqueId());
        World world = player.getWorld();
        BukkitGround ground = BukkitGround.of(world);
        Map<String, Integer> back = new LinkedHashMap<>();
        int taken = 0;
        List<Done> reversed = new ArrayList<>(last.done());
        java.util.Collections.reverse(reversed);
        for (Done each : reversed) {
            String there = ground.stateAt(each.spot());
            if (there == null || !there.equals(each.placed())
                    || !may(player, world, each.spot(), LandAction.BREAK)) {
                continue;
            }
            Block block = world.getBlockAt(each.spot().x(), each.spot().y(), each.spot().z());
            FreeBlocks.unmark(block);
            String restore = "minecraft:air";
            if (!PasteRule.air(each.before())) {
                // What Demolition cleared went to the player then; it comes back only if they can give it again.
                Optional<BlueprintRule.Piece> was = rule.piece(each.before(), Blueprints::itemOf);
                if (was.isPresent() && take(player.getInventory(), was.get().items())) {
                    restore = each.before();
                }
            }
            ground.set(each.spot(), restore);
            each.paid().forEach((item, count) -> back.merge(item, count, Integer::sum));
            taken++;
        }
        give(player, stacks(back));
        messages.send(player, "dnd.blueprint.undone", "name", last.name(), "blocks", String.valueOf(taken));
    }
}
