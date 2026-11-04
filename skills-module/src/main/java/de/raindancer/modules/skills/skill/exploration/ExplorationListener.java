package de.raindancer.modules.skills.skill.exploration;

import de.raindancer.core.platform.util.Cooldowns;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.core.world.locate.StructureLocator;
import de.raindancer.modules.skills.listener.ISkillsListener;
import de.raindancer.modules.skills.service.BodyService;
import de.raindancer.modules.skills.skill.SkillAttributes;
import de.raindancer.modules.skills.skill.SkillValues;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;

/**
 * The explorer's effects: sprinting faster and tiring less, a quicker mount, wider maps, a compass that finds the
 * nearest structure through Core's {@link StructureLocator} (searched a little each tick, never all at once), and one
 * more of something in a structure's chest.
 */
public final class ExplorationListener implements ISkillsListener {

    static final NamespacedKey MOUNT = new NamespacedKey(BodyService.NAMESPACE, "tree_mount_speed");
    private static final Duration SENSE_EVERY = Duration.ofMinutes(5);

    private final Plugin plugin;
    private final SkillValues values;
    private final ExplorationRule rule;
    private final Messages messages;
    private final StructureLocator structures;
    private final Consumer<Player> refresh;
    private final RandomGenerator random;
    private final Cooldowns<UUID> senses;
    /** Who rides what, with the modifier lent to it — taken back when they get off, leave or the module stops. */
    private final Map<UUID, LivingEntity> ridden = new ConcurrentHashMap<>();

    /** @param refresh puts the player's lent attributes right again — sprinting changed */
    public ExplorationListener(Plugin plugin, SkillValues values, ExplorationRule rule, Messages messages,
                               StructureLocator structures, Consumer<Player> refresh, RandomGenerator random,
                               LongSupplier clock) {
        this.plugin = plugin;
        this.values = values;
        this.rule = rule;
        this.messages = messages;
        this.structures = structures;
        this.refresh = refresh;
        this.random = random;
        this.senses = new Cooldowns<>(clock);
        this.senses.every(SENSE_EVERY);
    }

    @Override
    public void forget(UUID player) {
        senses.forget(player);
        LivingEntity mount = ridden.remove(player);
        if (mount != null) {
            Scheduling.entity(plugin, mount, () -> unmount(mount));
        }
    }

    // ------------------------------------------------------------------ on foot, in the water, in the saddle

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        Scheduling.entity(plugin, player, () -> refresh.accept(player));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTire(EntityExhaustionEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        switch (event.getExhaustionReason()) {
            case SPRINT, JUMP, JUMP_SPRINT -> {
                double less = values.value(player, ExplorationEffect.SPRINT_HUNGER);
                if (less > 0) {
                    event.setExhaustion(rule.exhaustion(event.getExhaustion(), less));
                }
            }
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player rider) || !(event.getMount() instanceof LivingEntity mount)) {
            return;
        }
        double faster = values.value(rider, ExplorationEffect.MOUNT_SPEED);
        if (faster <= 0) {
            return;
        }
        ridden.put(rider.getUniqueId(), mount);
        Scheduling.entity(plugin, mount, () -> SkillAttributes.set(mount, "movement_speed", MOUNT, faster / 100.0,
                AttributeModifier.Operation.ADD_SCALAR));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player rider) {
            LivingEntity mount = ridden.remove(rider.getUniqueId());
            if (mount != null) {
                Scheduling.entity(plugin, mount, () -> unmount(mount));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    private static void unmount(LivingEntity mount) {
        SkillAttributes.set(mount, "movement_speed", MOUNT, 0, AttributeModifier.Operation.ADD_SCALAR);
    }

    /** Every mount still lent speed, back to its own — the module stopping. */
    public void stripMounts() {
        ridden.values().forEach(ExplorationListener::unmount);
        ridden.clear();
    }

    // ------------------------------------------------------------------ maps and the compass

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.MAP && event.getAction() == Action.RIGHT_CLICK_AIR) {
            int zoom = rule.zoom(values.value(player, ExplorationEffect.WIDE_MAPS));
            if (zoom > 0) {
                event.setCancelled(true);
                startMap(player, held, zoom);
            }
        } else if (held.getType() == Material.COMPASS && player.isSneaking()) {
            int chunks = (int) Math.round(values.value(player, ExplorationEffect.TREASURE_SENSE));
            if (chunks > 0) {
                event.setCancelled(true);
                sense(player, chunks);
            }
        }
    }

    /** An empty map drawn at a wider zoom, on the same grid the game draws its own maps on. */
    private void startMap(Player player, ItemStack held, int zoom) {
        Location at = player.getLocation();
        MapView view = Bukkit.createMap(player.getWorld());
        view.setScale(MapView.Scale.values()[zoom]);
        view.setCenterX(rule.centre(at.getBlockX(), zoom));
        view.setCenterZ(rule.centre(at.getBlockZ(), zoom));
        view.setTrackingPosition(true);
        view.setUnlimitedTracking(false);
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        if (map.getItemMeta() instanceof MapMeta meta) {
            meta.setMapView(view);
            map.setItemMeta(meta);
        }
        if (held.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(map);
        } else {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().addItem(map).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private void sense(Player player, int chunks) {
        if (!senses.tryUse(player.getUniqueId())) {
            messages.send(player, "dnd.exploration.sense-wait", "seconds", String.valueOf(senses
                    .remaining(player.getUniqueId()).map(Duration::toSeconds).orElse(0L) + 1));
            return;
        }
        messages.send(player, "dnd.exploration.sensing");
        Location from = player.getLocation();
        structures.nearest(from, rule.structures(player.getWorld().getEnvironment()),
                        StructureLocator.Search.standard().within(Math.min(chunks, 100)))
                .thenAccept(found -> Scheduling.entity(plugin, player, () -> found.ifPresentOrElse(at -> {
                    player.setCompassTarget(at);
                    int blocks = (int) Math.round(Math.hypot(at.getX() - player.getLocation().getX(),
                            at.getZ() - player.getLocation().getZ()));
                    messages.send(player, "dnd.exploration.sensed", "blocks", String.valueOf(blocks));
                }, () -> messages.send(player, "dnd.exploration.sensed-nothing", "chunks", String.valueOf(chunks)))));
    }

    /** What Compass Reading shows: where they are, which way they face, the biome — or nothing without a compass. */
    public java.util.Optional<String[]> reading(Player player) {
        Material main = player.getInventory().getItemInMainHand().getType();
        Material off = player.getInventory().getItemInOffHand().getType();
        boolean holding = List.of(Material.COMPASS, Material.RECOVERY_COMPASS, Material.FILLED_MAP).contains(main)
                || List.of(Material.COMPASS, Material.RECOVERY_COMPASS, Material.FILLED_MAP).contains(off);
        if (!holding || !values.has(player, ExplorationEffect.COMPASS_READING)) {
            return java.util.Optional.empty();
        }
        Location at = player.getLocation();
        String biome = at.getBlock().getBiome().getKey().getKey().replace('_', ' ');
        return java.util.Optional.of(new String[]{"x", String.valueOf(at.getBlockX()), "y",
                String.valueOf(at.getBlockY()), "z", String.valueOf(at.getBlockZ()), "facing",
                rule.point(at.getYaw()), "biome", biome});
    }

    /** Roadrunner: on a road block, a road's speed. */
    public double road(Player player) {
        if (!values.has(player, ExplorationEffect.ROAD_RUNNER)) {
            return 0;
        }
        Location below = player.getLocation().subtract(0, 0.2, 0);
        return rule.road(below.getBlock().getType().getKey().getKey()) ? ExplorationRule.ROAD_SPEED : 0;
    }

    // ------------------------------------------------------------------ structure chests

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getInventoryHolder() == null
                || event.getInventoryHolder() instanceof Entity && !(event.getInventoryHolder()
                instanceof org.bukkit.entity.minecart.StorageMinecart)) {
            return;
        }
        if (!rule.chestLoot(event.getLootTable().getKey().toString())
                || !values.roll(player, ExplorationEffect.STRUCTURE_LOOT, random)) {
            return;
        }
        List<ItemStack> loot = new java.util.ArrayList<>(event.getLoot());
        int pick = rule.extraPick(loot.stream().map(ItemStack::getMaxStackSize).toList(), random);
        if (pick < 0) {
            return;
        }
        ItemStack chosen = loot.get(pick).clone();
        if (chosen.getAmount() < chosen.getMaxStackSize()) {
            chosen.setAmount(chosen.getAmount() + 1);
            loot.set(pick, chosen);
        } else {
            chosen.setAmount(1);
            loot.add(chosen);
        }
        event.setLoot(loot);
    }
}
