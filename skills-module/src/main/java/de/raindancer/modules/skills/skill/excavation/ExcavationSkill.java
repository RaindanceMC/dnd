package de.raindancer.modules.skills.skill.excavation;

import de.raindancer.core.platform.util.Cooldowns;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.actionbar.ActionBarPriority;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.world.blocks.PlacedBlocks;
import de.raindancer.modules.skills.SkillsServices;
import de.raindancer.modules.skills.listener.ISkillsListener;
import de.raindancer.modules.skills.skill.gathering.GatherRule;
import de.raindancer.modules.skills.skill.gathering.Hands;
import de.raindancer.modules.skills.skill.gathering.Loot;
import de.raindancer.modules.skills.skill.gathering.NaturalBreaks;
import de.raindancer.modules.skills.skill.gathering.Sense;
import de.raindancer.modules.skills.skill.gathering.SkillValues;
import de.raindancer.modules.skills.skill.gathering.Spot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static de.raindancer.modules.skills.skill.gathering.SkillValues.get;
import static de.raindancer.modules.skills.skill.gathering.SkillValues.has;

/**
 * The Excavation tree at work: a faster shovel that wears slower, treasures, flint from gravel and more clay, finds
 * that come up twice under the brush, Earthmover's three by three and Keen Eye. Only ground the world made counts —
 * never a block a player placed, never sand or gravel that fell and landed — so no farm of placed sand pays.
 */
public final class ExcavationSkill implements ISkillsListener {

    static final NamespacedKey SPEED = Hands.key("excavation_speed");
    private static final Duration EYE_EVERY = Duration.ofSeconds(5);
    private static final int EYE_RADIUS = 8;
    private static final int EYE_MOST = 12;

    /** A block a player has put a brush to: its find, when it pops out, may come up twice. */
    private record Brushing(String world, int x, int y, int z, long at) {
    }

    private final SkillsServices services;
    private final SkillValues values;
    private final NaturalBreaks natural;
    private final ExcavationRule rule = new ExcavationRule();
    private final GatherRule gather = new GatherRule();
    private final Cooldowns<UUID> eyes;
    private final Map<UUID, Brushing> brushing = new ConcurrentHashMap<>();
    private final Set<UUID> moving = ConcurrentHashMap.newKeySet();

    public ExcavationSkill(SkillsServices services, SkillValues values, NaturalBreaks natural) {
        this.services = services;
        this.values = values;
        this.natural = natural;
        this.eyes = new Cooldowns<>(System::currentTimeMillis);
        this.eyes.every(EYE_EVERY);
    }

    private static double roll() {
        return ThreadLocalRandom.current().nextDouble();
    }

    private boolean holds(Player player, GatherRule.Tool tool) {
        return gather.tool(player.getInventory().getItemInMainHand().getType()) == tool;
    }

    public void tick(Player player, Map<String, Double> tree) {
        Hands.speed(player, SPEED, holds(player, GatherRule.Tool.SHOVEL) ? get(tree, ExcavationEffect.DIG_SPEED) : 0);
    }

    /** Earthmover: the eight around a dug block, the tick after, each a real break. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!player.isSneaking() || moving.contains(player.getUniqueId()) || !rule.soft(block.getType())
                || !natural.natural(event) || !holds(player, GatherRule.Tool.SHOVEL)
                || !has(values.of(player), ExcavationEffect.DIG_EARTHMOVER)) {
            return;
        }
        World world = block.getWorld();
        Location eye = player.getLocation();
        List<Spot> around = rule.plane(new Spot(block.getX(), block.getY(), block.getZ()), eye.getPitch(),
                eye.getYaw());
        Scheduling.entityLater(services.plugin(), player, 1, () -> {
            moving.add(player.getUniqueId());
            try {
                for (Spot spot : around) {
                    ItemStack tool = player.getInventory().getItemInMainHand();
                    int[] wear = Hands.wear(tool);
                    if (gather.tool(tool.getType()) != GatherRule.Tool.SHOVEL
                            || (wear[1] > 0 && wear[1] - wear[0] <= 2)) {
                        break;
                    }
                    Block other = world.getBlockAt(spot.x(), spot.y(), spot.z());
                    if (rule.soft(other.getType()) && !PlacedBlocks.isPlaced(other)) {
                        player.breakBlock(other);
                    }
                }
            } finally {
                moving.remove(player.getUniqueId());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        BlockState broken = event.getBlockState();
        Material type = broken.getType();
        if (!rule.soft(type) || !natural.natural(player, broken)) {
            return;
        }
        Map<String, Double> tree = values.of(player);
        if (tree.isEmpty() || Hands.silk(player.getInventory().getItemInMainHand())) {
            return;
        }
        Location centre = broken.getLocation().add(0.5, 0.5, 0.5);
        if (type == Material.GRAVEL && gather.chance(get(tree, ExcavationEffect.DIG_FLINT), roll())) {
            centre.getWorld().dropItemNaturally(centre, new ItemStack(Material.FLINT));
        }
        if (type == Material.CLAY && gather.chance(get(tree, ExcavationEffect.DIG_CLAY), roll())) {
            centre.getWorld().dropItemNaturally(centre, new ItemStack(Material.CLAY_BALL, 2));
        }
        if (!holds(player, GatherRule.Tool.SHOVEL)) {
            return;
        }
        Optional<Loot> treasure = rule.treasure(get(tree, ExcavationEffect.DIG_TREASURE),
                has(tree, ExcavationEffect.DIG_RARE_TREASURE), roll(), roll());
        treasure.ifPresent(loot -> {
            centre.getWorld().dropItemNaturally(centre, new ItemStack(loot.material(), loot.amount()));
            services.effects().play(player.getUniqueId(), Cues.EARNED);
            services.core().actionBars().show(player.getUniqueId(), "dnd-excavation",
                    services.messages().get("dnd.excavation.treasure", "amount", String.valueOf(loot.amount()),
                            "item", loot.material().getKey().getKey().replace('_', ' ')),
                    Duration.ofSeconds(3), ActionBarPriority.LOW);
        });
    }

    /** A brush put to suspicious sand or gravel: remembered, so its find is known when it pops out. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBrush(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || block == null
                || event.getItem() == null || event.getItem().getType() != Material.BRUSH
                || !rule.suspicious(block.getType())) {
            return;
        }
        Player player = event.getPlayer();
        Brushing known = brushing.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        boolean same = known != null && known.world().equals(block.getWorld().getName()) && known.x() == block.getX()
                && known.y() == block.getY() && known.z() == block.getZ() && now - known.at() <= ExcavationRule.BRUSH_MILLIS;
        if (!same) {
            brushing.put(player.getUniqueId(), new Brushing(block.getWorld().getName(), block.getX(), block.getY(),
                    block.getZ(), now));
        }
    }

    /**
     * The find popping out of a brushed block. Only an item nobody threw, at the block, and only once the block has
     * turned to plain sand or gravel the tick after — so nothing dropped beside it is ever doubled.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFind(ItemSpawnEvent event) {
        if (brushing.isEmpty() || event.getEntity().getThrower() != null) {
            return;
        }
        Item item = event.getEntity();
        Location at = item.getLocation();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Brushing> entry : brushing.entrySet()) {
            Brushing brushed = entry.getValue();
            if (!brushed.world().equals(at.getWorld().getName()) || !rule.brushFind(brushed.at(), now,
                    at.getX() - brushed.x(), at.getY() - brushed.y(), at.getZ() - brushed.z())) {
                continue;
            }
            Block block = at.getWorld().getBlockAt(brushed.x(), brushed.y(), brushed.z());
            UUID brusher = entry.getKey();
            Scheduling.regionLater(services.plugin(), block.getLocation(), 1, () -> {
                if (rule.suspicious(block.getType()) || !item.isValid() || !brushing.remove(brusher, brushed)) {
                    return;
                }
                Player player = services.server().getPlayer(brusher);
                if (player != null && gather.chance(get(values.of(player), ExcavationEffect.DIG_BRUSH), roll())) {
                    ItemStack stack = item.getItemStack();
                    stack.setAmount(Math.min(stack.getMaxStackSize(), stack.getAmount() * 2));
                    item.setItemStack(stack);
                    services.effects().play(brusher, Cues.EARNED);
                }
            });
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        GatherRule.Tool tool = gather.tool(event.getItem().getType());
        double chance;
        if (tool == GatherRule.Tool.SHOVEL && natural.justBroke(player)) {
            chance = get(values.of(player), ExcavationEffect.DIG_WEAR);
        } else if (tool == GatherRule.Tool.BRUSH) {
            chance = get(values.of(player), ExcavationEffect.DIG_BRUSH_WEAR);
        } else {
            return;
        }
        if (gather.chance(chance, roll())) {
            event.setCancelled(true);
        }
    }

    /** Keen Eye: crouching with a brush shows the suspicious sand and gravel around. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrouch(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking() || !holds(player, GatherRule.Tool.BRUSH)
                || !has(values.of(player), ExcavationEffect.DIG_KEEN_EYE) || !eyes.tryUse(player.getUniqueId())) {
            return;
        }
        List<Block> found = Sense.all(player.getLocation().getBlock(), EYE_RADIUS,
                block -> rule.suspicious(block.getType()), EYE_MOST);
        found.forEach(block -> Sense.show(services.plugin(), player, block, Particle.WAX_ON));
        if (!found.isEmpty()) {
            services.effects().play(player.getUniqueId(), Cues.NOTIFY);
        }
        services.core().actionBars().show(player.getUniqueId(), "dnd-excavation", services.messages().get(
                        found.isEmpty() ? "dnd.excavation.nothing-suspicious" : "dnd.excavation.suspicious",
                        "count", String.valueOf(found.size()), "radius", String.valueOf(EYE_RADIUS)),
                Duration.ofSeconds(3), ActionBarPriority.LOW);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @Override
    public void forget(UUID player) {
        brushing.remove(player);
        moving.remove(player);
        eyes.forget(player);
    }
}
