package de.raindancer.modules.skills.skill.foraging;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.world.activity.Activities;
import de.raindancer.core.world.activity.Activity;
import de.raindancer.core.world.activity.ActivityKind;
import de.raindancer.modules.api.ModuleContext;
import de.raindancer.modules.skills.SkillsServices;
import de.raindancer.modules.skills.listener.ISkillsListener;
import de.raindancer.modules.skills.skill.SkillValues;
import de.raindancer.modules.skills.skill.Witnessed;
import io.papermc.paper.event.block.PlayerShearBlockEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Foraging at work. A flower or mushroom gives more only when Core's Activities told it as FORAGE — the world's own,
 * never placed — and never where bone meal grew something lately ({@link Fertilised}): bone meal on grass makes
 * flowers, and doubled flowers composted would make more bone meal than they took.
 */
public final class ForagingListener implements ISkillsListener {

    private static final long FERTILISED_FOR = Duration.ofMinutes(30).toMillis();
    private static final int FERTILISED_MOST = 16_384;
    /** Bone meal tried on a sapling at most this often for Bloom; a tree that will not fit stays a sapling. */
    private static final int BLOOM_TRIES = 12;

    private final Plugin plugin;
    private final SkillValues values;
    private final ForagingRule rule = new ForagingRule();
    private final Witnessed foraged = new Witnessed(1_000);
    private final Fertilised fertilised = new Fertilised(FERTILISED_FOR, FERTILISED_MOST);
    private final ThreadLocal<Boolean> blooming = ThreadLocal.withInitial(() -> false);

    ForagingListener(Plugin plugin, SkillValues values) {
        this.plugin = plugin;
        this.values = values;
    }

    public static void start(ModuleContext context, SkillsServices services) {
        ForagingListener listener = new ForagingListener(context.plugin(), new SkillValues(services));
        context.listener(listener);
        Object listening = Activities.listen(context.plugin(), listener::did);
        context.closeWith(() -> Activities.stop(listening));
    }

    void did(Player player, Activity activity) {
        if (activity.kind() == ActivityKind.FORAGE) {
            foraged.saw(player.getUniqueId(), activity.what(), System.currentTimeMillis());
        }
    }

    private boolean fertilised(Block block) {
        return fertilised.was(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        BlockState state = event.getBlockState();
        String what = state.getType().getKey().getKey();
        if (!foraged.take(player.getUniqueId(), what, System.currentTimeMillis()) || fertilised(event.getBlock())) {
            return;
        }
        int more = values.times(player, ForagingEffect.WILD);
        if (more > 0) {
            for (Item item : event.getItems()) {
                ItemStack stack = item.getItemStack();
                stack.setAmount(Math.min(stack.getMaxStackSize(), stack.getAmount() * (1 + more)));
                item.setItemStack(stack);
            }
        }
        rareFind(player, what, event.getBlock().getLocation());
    }

    /** Berries: more of them dropped beside the bush — Core counts only what the bush gave — and Bramble Kin. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPick(PlayerHarvestBlockEvent event) {
        Block bush = event.getHarvestedBlock();
        if (!rule.berries(bush.getType())) {
            return;
        }
        Player player = event.getPlayer();
        Location at = bush.getLocation().add(0.5, 0.5, 0.5);
        Material berry = null;
        int picked = 0;
        for (ItemStack stack : event.getItemsHarvested()) {
            if (stack != null && !stack.isEmpty()) {
                berry = stack.getType();
                picked += stack.getAmount();
            }
        }
        int more = berry == null ? 0 : rule.moreBerries(picked, values.value(player, ForagingEffect.BERRIES),
                ThreadLocalRandom.current().nextDouble());
        if (more > 0) {
            bush.getWorld().dropItemNaturally(at, new ItemStack(berry, more));
        }
        rareFind(player, bush.getType().getKey().getKey(), bush.getLocation());
        if (values.has(player, ForagingEffect.BRAMBLE) && ThreadLocalRandom.current().nextDouble() < ForagingRule.REGROW) {
            Scheduling.regionLater(plugin, bush.getLocation(), 1, () -> ripen(bush));
        }
    }

    private static void ripen(Block bush) {
        if (bush.getBlockData() instanceof CaveVinesPlant vine) {
            vine.setBerries(true);
            bush.setBlockData(vine);
        } else if (bush.getType() == Material.SWEET_BERRY_BUSH && bush.getBlockData() instanceof Ageable age) {
            age.setAge(age.getMaximumAge());
            bush.setBlockData(age);
        }
    }

    private void rareFind(Player player, String what, Location at) {
        if (!values.roll(player, ForagingEffect.RARE)) {
            return;
        }
        rule.rareFind(what, ThreadLocalRandom.current().nextDouble()).ifPresent(find -> {
            at.getWorld().dropItemNaturally(at.clone().add(0.5, 0.5, 0.5), new ItemStack(find));
            values.notice(player, Cues.REWARD, "dnd.nature.rare-find", "item",
                    find.getKey().getKey().replace('_', ' '));
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHive(PlayerShearBlockEvent event) {
        Material hive = event.getBlock().getType();
        if (hive != Material.BEEHIVE && hive != Material.BEE_NEST) {
            return;
        }
        int more = values.times(event.getPlayer(), ForagingEffect.HONEY);
        if (more > 0) {
            event.getDrops().add(new ItemStack(Material.HONEYCOMB, more));
        }
    }

    /** Every spot bone meal grew something on is remembered; then the bone meal saved, and Bloom. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBoneMeal(BlockFertilizeEvent event) {
        long now = System.currentTimeMillis();
        Block block = event.getBlock();
        fertilised.mark(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), now);
        for (BlockState grown : event.getBlocks()) {
            fertilised.mark(grown.getWorld().getName(), grown.getX(), grown.getY(), grown.getZ(), now);
        }
        Player player = event.getPlayer();
        if (player == null || blooming.get()) {
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE && values.roll(player, ForagingEffect.BONE_MEAL)) {
            player.getInventory().addItem(new ItemStack(Material.BONE_MEAL)).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        if (rule.blooms(block.getType()) && values.has(player, ForagingEffect.BLOOM)) {
            Material was = block.getType();
            Scheduling.regionLater(plugin, block.getLocation(), 1, () -> bloom(block, was));
        }
    }

    /** Bloom: bone meal again and again, as the game would grow it, until it is grown or will not grow. */
    private void bloom(Block block, Material was) {
        blooming.set(true);
        try {
            for (int i = 0; i < BLOOM_TRIES && block.getType() == was; i++) {
                if (block.getBlockData() instanceof Ageable age && age.getAge() >= age.getMaximumAge()) {
                    return;
                }
                if (!block.applyBoneMeal(BlockFace.UP)) {
                    return;
                }
            }
        } finally {
            blooming.set(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrick(EntityDamageByBlockEvent event) {
        if (event.getEntity() instanceof Player player && event.getDamager() != null
                && rule.prickly(event.getDamager().getType()) && values.has(player, ForagingEffect.BRAMBLE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @Override
    public void forget(UUID player) {
        foraged.forget(player);
    }
}
