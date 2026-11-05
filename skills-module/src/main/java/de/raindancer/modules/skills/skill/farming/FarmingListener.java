package de.raindancer.modules.skills.skill.farming;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.presence.Away;
import de.raindancer.core.world.activity.Activities;
import de.raindancer.core.world.activity.Activity;
import de.raindancer.core.world.activity.ActivityKind;
import de.raindancer.core.world.activity.Ores;
import de.raindancer.modules.api.ModuleContext;
import de.raindancer.modules.skills.SkillsServices;
import de.raindancer.modules.skills.listener.ISkillsListener;
import de.raindancer.modules.skills.skill.Chances;
import de.raindancer.modules.skills.skill.Pulse;
import de.raindancer.modules.skills.skill.SkillValues;
import de.raindancer.modules.skills.skill.Witnessed;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Farming at work. A crop's bonus comes only from a break Core's Activities told as a HARVEST — a crop grown, a melon
 * or pumpkin the world made, never a placed one — and only to the player who broke it, so a water-and-piston farm or
 * a villager's field gives nothing more; a twin only from a breeding Core counted, never of parents bought as eggs.
 */
public final class FarmingListener implements ISkillsListener {

    /** Animals a farm makes without a farmer: nothing more from them. */
    private static final Set<CreatureSpawnEvent.SpawnReason> NOT_RAISED = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.SPAWNER, CreatureSpawnEvent.SpawnReason.SPAWNER_EGG,
            CreatureSpawnEvent.SpawnReason.DISPENSE_EGG, CreatureSpawnEvent.SpawnReason.COMMAND,
            CreatureSpawnEvent.SpawnReason.CUSTOM);
    /** What Sun and Rain grows: Core's crops with an age, and the stems. A pitcher is two blocks: left alone. */
    private static final Set<Material> GROWING = growing();

    private final Plugin plugin;
    private final SkillValues values;
    private final FarmingRule rule = new FarmingRule();
    private final Witnessed harvested = new Witnessed(1_000);
    private final Witnessed bred = new Witnessed(1_000);
    private final ThreadLocal<Boolean> reaping = ThreadLocal.withInitial(() -> false);

    FarmingListener(Plugin plugin, SkillValues values) {
        this.plugin = plugin;
        this.values = values;
    }

    public static void start(ModuleContext context, SkillsServices services) {
        FarmingListener listener = new FarmingListener(context.plugin(), new SkillValues(services));
        context.listener(listener);
        Object listening = Activities.listen(context.plugin(), listener::did);
        context.closeWith(() -> Activities.stop(listening));
        Pulse.everyPlayer(context, FarmingRule.PULSE_TICKS, listener::tend);
    }

    private static Set<Material> growing() {
        Set<Material> all = EnumSet.copyOf(Ores.AGED_CROPS);
        all.remove(Material.PITCHER_CROP);
        all.add(Material.MELON_STEM);
        all.add(Material.PUMPKIN_STEM);
        return all;
    }

    void did(Player player, Activity activity) {
        if (activity.kind() == ActivityKind.HARVEST) {
            harvested.saw(player.getUniqueId(), activity.what(), System.currentTimeMillis());
        } else if (activity.kind() == ActivityKind.BREED) {
            bred.saw(player.getUniqueId(), activity.what(), System.currentTimeMillis());
        }
    }

    // ------------------------------------------------------------------ fields

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        BlockState state = event.getBlockState();
        Material crop = state.getType();
        if (!harvested.take(player.getUniqueId(), crop.getKey().getKey(), System.currentTimeMillis())) {
            return;
        }
        if (values.has(player, FarmingEffect.REPLANT)) {
            rule.seed(crop).ifPresent(seed -> replant(event.getBlock(), state.getBlockData(), seed, event.getItems()));
        }
        int more = values.times(player, FarmingEffect.CROP_YIELD);
        if (more > 0) {
            rule.produce(crop).ifPresent(produce -> {
                for (Item item : event.getItems()) {
                    ItemStack stack = item.getItemStack();
                    if (stack.getType() == produce) {
                        stack.setAmount(Math.min(stack.getMaxStackSize(), stack.getAmount() * (1 + more)));
                        item.setItemStack(stack);
                    }
                }
            });
        }
    }

    /** Green Thumb: one seed out of the crop's own drops, and the crop back where it stood, a tick later. */
    private void replant(Block block, BlockData grown, Material seed, List<Item> drops) {
        for (Iterator<Item> it = drops.iterator(); it.hasNext(); ) {
            Item item = it.next();
            ItemStack stack = item.getItemStack();
            if (stack.getType() != seed) {
                continue;
            }
            if (stack.getAmount() <= 1) {
                it.remove();
            } else {
                stack.setAmount(stack.getAmount() - 1);
                item.setItemStack(stack);
            }
            BlockData young = grown.clone();
            if (young instanceof Ageable age) {
                age.setAge(0);
            }
            Scheduling.regionLater(plugin, block.getLocation(), 1, () -> {
                if (block.getType().isAir() && young.isSupported(block)) {
                    block.setBlockData(young);
                } else {
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(seed));
                }
            });
            return;
        }
    }

    /** Reaper: a grown crop broken with a hoe takes the grown crops around it along, each a break of its own. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onReap(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (reaping.get() || !rule.reapable(block.getType()) || !grown(block)
                || !rule.hoe(player.getInventory().getItemInMainHand().getType())
                || !values.has(player, FarmingEffect.REAPER)) {
            return;
        }
        List<Block> around = new ArrayList<>();
        for (int dx = -FarmingRule.REAP_RADIUS; dx <= FarmingRule.REAP_RADIUS; dx++) {
            for (int dz = -FarmingRule.REAP_RADIUS; dz <= FarmingRule.REAP_RADIUS; dz++) {
                Block other = block.getRelative(dx, 0, dz);
                if ((dx != 0 || dz != 0) && rule.reapable(other.getType()) && grown(other)) {
                    around.add(other);
                }
            }
        }
        if (around.isEmpty()) {
            return;
        }
        Scheduling.entityLater(plugin, player, 1, () -> {
            reaping.set(true);
            try {
                for (Block other : around) {
                    if (rule.reapable(other.getType()) && grown(other)) {
                        player.breakBlock(other);
                    }
                }
            } finally {
                reaping.set(false);
            }
        });
    }

    private static boolean grown(Block block) {
        return block.getBlockData() instanceof Ageable age && age.getAge() >= age.getMaximumAge();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent event) {
        if (rule.hoe(event.getItem().getType()) && values.roll(event.getPlayer(), FarmingEffect.HOE_SAVE)) {
            event.setCancelled(true);
        }
    }

    /** Sun and Rain: the crops around a farmer who is not away grow a stage now and then — each asked of the world. */
    void tend(Player player) {
        if (Away.isAway(player.getUniqueId())) {
            return;
        }
        double chance = rule.stageChance(values.value(player, FarmingEffect.GROWTH));
        if (chance <= 0) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Block centre = player.getLocation().getBlock();
        int r = FarmingRule.GROWTH_RADIUS;
        int h = FarmingRule.GROWTH_HEIGHT;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -h; dy <= h; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    Block block = centre.getRelative(dx, dy, dz);
                    if (GROWING.contains(block.getType()) && block.getBlockData() instanceof Ageable age
                            && age.getAge() < age.getMaximumAge() && random.nextDouble() < chance) {
                        stage(block, age);
                    }
                }
            }
        }
    }

    private static void stage(Block block, Ageable age) {
        Ageable next = (Ageable) age.clone();
        next.setAge(age.getAge() + 1);
        BlockState state = block.getState();
        state.setBlockData(next);
        if (new BlockGrowEvent(block, state).callEvent()) {
            state.update(true, true);
        }
    }

    // ------------------------------------------------------------------ pasture

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) {
            return;
        }
        LivingEntity child = event.getEntity();
        LivingEntity mother = event.getMother();
        LivingEntity father = event.getFather();
        String what = child.getType().getKey().getKey();
        // A tick later, where the parents stand: the child is only in the world after this event, and by then Core
        // has told whether the breeding counts, which is what decides here too.
        Scheduling.regionLater(plugin, mother.getLocation(), 1, () -> {
            if (!bred.take(player.getUniqueId(), what, System.currentTimeMillis())) {
                return;
            }
            if (values.roll(player, FarmingEffect.TWINS)) {
                twin(child);
            }
            if (values.has(player, FarmingEffect.PLENTY)) {
                for (LivingEntity parent : List.of(mother, father)) {
                    Scheduling.entity(plugin, parent, () -> {
                        if (parent instanceof Animals animal && animal.isValid() && animal.isAdult()) {
                            animal.setAge(0);
                        }
                    });
                }
            }
        });
    }

    private static void twin(LivingEntity child) {
        Class<? extends Entity> kind = child.getType().getEntityClass();
        if (kind == null || !child.isValid()) {
            return;
        }
        child.getWorld().spawn(child.getLocation(), kind, CreatureSpawnEvent.SpawnReason.BREEDING, twin -> {
            if (twin instanceof org.bukkit.entity.Ageable young) {
                young.setBaby();
            }
            if (twin instanceof Sheep lamb && child instanceof Sheep sibling) {
                lamb.setColor(sibling.getColor());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSlaughter(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || !(dead instanceof Animals animal) || !animal.isAdult()
                || NOT_RAISED.contains(dead.getEntitySpawnReason())) {
            return;
        }
        int more = values.times(killer, FarmingEffect.BUTCHER);
        if (more <= 0) {
            return;
        }
        List<ItemStack> extra = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) {
            if (drop != null && rule.animalProduce(drop.getType())) {
                for (int i = 0; i < more; i++) {
                    extra.add(drop.clone());
                }
            }
        }
        event.getDrops().addAll(extra);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        double percent = values.value(event.getPlayer(), FarmingEffect.FLEECE);
        if (percent <= 0) {
            return;
        }
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        List<ItemStack> extra = new ArrayList<>();
        for (ItemStack drop : drops) {
            if (drop.getType().name().endsWith("_WOOL")) {
                int more = Chances.times(drop.getAmount() * percent, ThreadLocalRandom.current().nextDouble());
                if (more > 0) {
                    extra.add(drop.asQuantity(more));
                }
            }
        }
        if (!extra.isEmpty()) {
            drops.addAll(extra);
            event.setDrops(drops);
        }
    }

    /** Quick Weaning: a baby fed grows up sooner still; read and cut a tick later, after the game's own feeding. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Animals baby) || baby.isAdult()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItem(event.getHand());
        if (hand == null || !baby.isBreedItem(hand.getType())) {
            return;
        }
        double percent = values.value(player, FarmingEffect.WEANING);
        if (percent <= 0) {
            return;
        }
        Scheduling.entityLater(plugin, baby, 1, () -> {
            if (baby.isValid() && !baby.isAdult()) {
                baby.setAge(rule.weaned(baby.getAge(), percent));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @Override
    public void forget(UUID player) {
        harvested.forget(player);
        bred.forget(player);
    }
}
