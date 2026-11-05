package de.raindancer.modules.skills.skill.mining;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.world.blocks.PlacedBlocks;
import de.raindancer.modules.skills.SkillsServices;
import de.raindancer.modules.skills.listener.ISkillsListener;
import de.raindancer.modules.skills.skill.gathering.ConnectedRule;
import de.raindancer.modules.skills.skill.gathering.GatherRule;
import de.raindancer.modules.skills.skill.gathering.Hands;
import de.raindancer.modules.skills.skill.gathering.NaturalBreaks;
import de.raindancer.modules.skills.skill.gathering.SkillValues;
import de.raindancer.modules.skills.skill.gathering.Spot;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static de.raindancer.modules.skills.skill.gathering.SkillValues.get;
import static de.raindancer.modules.skills.skill.gathering.SkillValues.has;

/**
 * The Mining tree at work: a faster pickaxe, ores the world made sometimes dropping twice or three times over — the rarer, the seldomer — and more
 * experience, haste after an ore, night vision deep down, a pickaxe that wears slower and Vein Miner.
 * Only ores the world made count — never one a player placed (Core's {@link PlacedBlocks}) — and never with silk
 * touch, so no ore is ever made out of nothing.
 */
public final class MiningSkill implements ISkillsListener {

    static final NamespacedKey SPEED = Hands.key("mining_speed");

    private final SkillsServices services;
    private final SkillValues values;
    private final NaturalBreaks natural;
    private final MiningRule rule = new MiningRule();
    private final GatherRule gather = new GatherRule();
    private final ConnectedRule connected = new ConnectedRule();
    /** Players whose vein is being mined right now: those breaks start no vein of their own. */
    private final Set<UUID> veining = ConcurrentHashMap.newKeySet();

    public MiningSkill(SkillsServices services, SkillValues values, NaturalBreaks natural) {
        this.services = services;
        this.values = values;
        this.natural = natural;
    }

    private static double roll() {
        return ThreadLocalRandom.current().nextDouble();
    }

    private boolean pickaxe(ItemStack stack) {
        return stack != null && gather.tool(stack.getType()) == GatherRule.Tool.PICKAXE;
    }

    /** Every second and when the hand changes: the pickaxe's speed, and night vision deep down. */
    public void tick(Player player, Map<String, Double> tree) {
        Hands.speed(player, SPEED, pickaxe(player.getInventory().getItemInMainHand())
                ? get(tree, MiningEffect.MINE_SPEED) : 0);
        PotionEffect sight = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        boolean ours = sight != null && rule.ours(sight.isAmbient(), sight.hasParticles(), sight.getDuration());
        if (has(tree, MiningEffect.MINE_DEEP_SIGHT) && rule.deep(player.getLocation().getBlockY(),
                player.getWorld().getEnvironment() == World.Environment.NORMAL)) {
            if (sight == null || (ours && rule.needsNightVision(sight.getDuration()))) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, MiningRule.NIGHT_VISION_TICKS,
                        0, true, false, true));
            }
        } else if (ours) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!rule.ore(event.getBlock().getType()) || !natural.natural(event)) {
            return;
        }
        Map<String, Double> tree = values.of(player);
        if (tree.isEmpty()) {
            return;
        }
        if (event.getExpToDrop() > 0) {
            event.setExpToDrop(gather.scaled(event.getExpToDrop(), get(tree, MiningEffect.MINE_ORE_XP), roll()));
        }
        if (veining.contains(player.getUniqueId()) && has(tree, MiningEffect.MINE_VEIN_MAGNET)
                && event.getExpToDrop() > 0) {
            player.giveExp(event.getExpToDrop(), true);
            event.setExpToDrop(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!rule.ore(block.getType()) || !natural.natural(event)) {
            return;
        }
        Map<String, Double> tree = values.of(player);
        if (tree.isEmpty()) {
            return;
        }
        haste(player, tree);
        int more = (int) get(tree, MiningEffect.MINE_VEIN);
        if (more > 0 && player.isSneaking() && !veining.contains(player.getUniqueId())
                && pickaxe(player.getInventory().getItemInMainHand())) {
            vein(player, block, more);
        }
    }

    private void haste(Player player, Map<String, Double> tree) {
        int ticks = rule.hasteTicks(get(tree, MiningEffect.MINE_HASTE));
        if (ticks <= 0) {
            return;
        }
        int amplifier = rule.hasteAmplifier(has(tree, MiningEffect.MINE_HASTE_TWO));
        PotionEffect current = player.getPotionEffect(PotionEffectType.HASTE);
        if (current == null || rule.replacesHaste(current.getAmplifier(), current.getDuration(), amplifier, ticks)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, ticks, amplifier, true, false, true));
        }
    }

    /** The rest of the vein, the tick after: each block a real break — claims, drops and wear as by hand. */
    private void vein(Player player, Block origin, int more) {
        Material type = origin.getType();
        World world = origin.getWorld();
        List<Spot> spots = connected.connected(new Spot(origin.getX(), origin.getY(), origin.getZ()), spot -> {
            Block other = world.getBlockAt(spot.x(), spot.y(), spot.z());
            return rule.sameVein(type, other.getType()) && !PlacedBlocks.isPlaced(other);
        }, more, MiningRule.VEIN_REACH, false);
        if (spots.isEmpty()) {
            return;
        }
        Scheduling.entityLater(services.plugin(), player, 1, () -> {
            veining.add(player.getUniqueId());
            try {
                for (Spot spot : spots) {
                    ItemStack tool = player.getInventory().getItemInMainHand();
                    int[] wear = Hands.wear(tool);
                    if (!pickaxe(tool) || !rule.mayVeinOn(wear[1], wear[0])) {
                        break;
                    }
                    Block block = world.getBlockAt(spot.x(), spot.y(), spot.z());
                    if (rule.sameVein(type, block.getType())) {
                        player.breakBlock(block);
                    }
                }
            } finally {
                veining.remove(player.getUniqueId());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        BlockState broken = event.getBlockState();
        if (!rule.ore(broken.getType()) || !natural.natural(player, broken)) {
            return;
        }
        Map<String, Double> tree = values.of(player);
        if (tree.isEmpty() || !gather.multiplies(Hands.silk(player.getInventory().getItemInMainHand()))) {
            return;
        }
        Material ore = broken.getType();
        int times = gather.times(rule.byRarity(get(tree, MiningEffect.MINE_ORE_DOUBLE), ore),
                rule.byRarity(get(tree, MiningEffect.MINE_MOTHERLODE), ore), roll(), roll());
        if (times > 1) {
            for (Item item : event.getItems()) {
                ItemStack stack = item.getItemStack();
                stack.setAmount(Math.min(stack.getMaxStackSize(), stack.getAmount() * times));
                item.setItemStack(stack);
            }
        }
    }

    /** Lodestone: a vein's further drops go straight into the inventory, what does not fit falls as usual. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVeinDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (!veining.contains(player.getUniqueId()) || !has(values.of(player), MiningEffect.MINE_VEIN_MAGNET)) {
            return;
        }
        for (Iterator<Item> items = event.getItems().iterator(); items.hasNext(); ) {
            Item item = items.next();
            var left = player.getInventory().addItem(item.getItemStack());
            if (left.isEmpty()) {
                items.remove();
            } else {
                item.setItemStack(left.values().iterator().next());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        if (!pickaxe(event.getItem()) || !natural.justBroke(player)) {
            return;
        }
        Map<String, Double> tree = values.of(player);
        boolean saved = gather.chance(get(tree, MiningEffect.MINE_WEAR), roll())
                || (veining.contains(player.getUniqueId())
                && gather.chance(get(tree, MiningEffect.MINE_VEIN_WEAR), roll()));
        if (saved) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @Override
    public void forget(UUID player) {
        veining.remove(player);
    }
}
