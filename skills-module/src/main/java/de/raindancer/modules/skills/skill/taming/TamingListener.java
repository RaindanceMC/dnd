package de.raindancer.modules.skills.skill.taming;

import de.raindancer.core.platform.util.Cooldowns;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.modules.api.ModuleContext;
import de.raindancer.modules.skills.SkillsServices;
import de.raindancer.modules.skills.listener.ISkillsListener;
import de.raindancer.modules.skills.service.BodyService;
import de.raindancer.modules.skills.skill.Pulse;
import de.raindancer.modules.skills.skill.SkillValues;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Taming at work: a player's own wolves, cats and parrots near them, and the horse they ride. What is lent to an
 * animal is a transient modifier (BodyService), so nothing of it is saved into the world; taken off again when the
 * rider gets down, the pet is too far, or the module stops.
 */
public final class TamingListener implements ISkillsListener {

    static final NamespacedKey PET_HEARTS = new NamespacedKey(BodyService.NAMESPACE, "tame_hearts");
    static final NamespacedKey HORSE_SPEED = new NamespacedKey(BodyService.NAMESPACE, "tame_horse_speed");
    static final NamespacedKey HORSE_JUMP = new NamespacedKey(BodyService.NAMESPACE, "tame_horse_jump");

    private final Plugin plugin;
    private final SkillValues values;
    private final TamingRule rule = new TamingRule();
    private final Cooldowns<UUID> loyal = new Cooldowns<>();
    /** Every animal something was lent to, to take it off when the module stops. */
    private final Map<UUID, WeakReference<LivingEntity>> lent = new ConcurrentHashMap<>();

    TamingListener(Plugin plugin, SkillValues values) {
        this.plugin = plugin;
        this.values = values;
        loyal.every(TamingRule.LOYAL_EVERY);
    }

    public static void start(ModuleContext context, SkillsServices services) {
        TamingListener listener = new TamingListener(context.plugin(), new SkillValues(services));
        context.listener(listener);
        Pulse.everyPlayer(context, TamingRule.PULSE_TICKS, listener::tend);
        context.closeWith(listener::stripAll);
    }

    private Player owner(Entity animal) {
        if (!(animal instanceof Tameable pet) || !pet.isTamed() || pet.getOwnerUniqueId() == null
                || !rule.pet(animal.getType().getKey().getKey())) {
            return null;
        }
        return plugin.getServer().getPlayer(pet.getOwnerUniqueId());
    }

    private static Player rider(Entity horse) {
        if (!(horse instanceof AbstractHorse)) {
            return null;
        }
        for (Entity passenger : horse.getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the pack

    /** Every five seconds: the player's pets near them hardier and mended, and the horse they ride. */
    void tend(Player player) {
        double hearts = values.value(player, TamingEffect.HEARTS);
        double mend = values.value(player, TamingEffect.MEND);
        int near = TamingRule.NEAR;
        for (Entity entity : player.getNearbyEntities(near, near / 2.0, near)) {
            if (owner(entity) != player || !(entity instanceof LivingEntity pet)) {
                continue;
            }
            Scheduling.entity(plugin, pet, () -> {
                lend(pet, Attribute.MAX_HEALTH, PET_HEARTS, hearts * 2, AttributeModifier.Operation.ADD_NUMBER);
                AttributeInstance max = pet.getAttribute(Attribute.MAX_HEALTH);
                if (mend > 0 && max != null && !pet.isDead()) {
                    pet.setHealth(Math.min(max.getValue(), pet.getHealth() + mend));
                }
            });
        }
        if (player.getVehicle() instanceof AbstractHorse horse && horse.isTamed()) {
            Scheduling.entity(plugin, horse, () -> saddle(horse, player));
        }
    }

    private void lend(LivingEntity animal, Attribute attribute, NamespacedKey key, double amount,
                      AttributeModifier.Operation operation) {
        BodyService.set(animal, attribute, key, amount, operation);
        if (amount != 0) {
            lent.put(animal.getUniqueId(), new WeakReference<>(animal));
        }
        BodyService.clampHealth(animal);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBite(EntityDamageByEntityEvent event) {
        Player owner = owner(event.getDamager());
        if (owner != null) {
            event.setDamage(rule.dealt(event.getDamage(), values.value(owner, TamingEffect.FANGS)));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        Entity hurt = event.getEntity();
        Player owner = owner(hurt);
        if (owner != null) {
            event.setDamage(rule.taken(event.getDamage(), values.value(owner, TamingEffect.GUARD)));
            return;
        }
        Player rider = rider(hurt);
        if (rider == null) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && values.has(rider, TamingEffect.WINDRIDER)) {
            event.setCancelled(true);
        } else {
            event.setDamage(rule.taken(event.getDamage(), values.value(rider, TamingEffect.HORSE_GUARD)));
        }
    }

    /** Loyal to the End: last, after every softening, a pet's death blow is turned aside — once in a while. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeathBlow(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity pet)) {
            return;
        }
        Player owner = owner(pet);
        if (owner == null || event.getFinalDamage() < pet.getHealth() || !values.has(owner, TamingEffect.LOYAL)
                || !loyal.tryUse(pet.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        pet.setHealth(Math.min(2.0, pet.getHealth()));
        pet.setFireTicks(0);
        if (pet.getWorld() == owner.getWorld()) {
            pet.teleportAsync(owner.getLocation());
        }
        String name = pet.customName() != null
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(pet.customName())
                : pet.getType().getKey().getKey();
        Scheduling.entity(plugin, owner, () -> values.notice(owner, Cues.HEAL, "dnd.nature.loyal", "pet", name));
    }

    // ------------------------------------------------------------------ taming

    /** Fed what tames it, an animal may trust the player at once; otherwise the game's own odds still follow. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Tameable animal)
                || animal.isTamed() || animal instanceof AbstractHorse) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!rule.tamingFood(animal.getType().getKey().getKey(), hand.getType())
                || !values.roll(player, TamingEffect.QUICK)) {
            return;
        }
        if (!new EntityTameEvent(animal, player).callEvent()) {
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
        animal.setOwner(player);
        if (animal instanceof Sittable sitting) {
            sitting.setSitting(true);
        }
        values.notice(player, Cues.EARNED, "dnd.nature.tamed");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getMount() instanceof AbstractHorse horse)) {
            return;
        }
        if (!horse.isTamed()) {
            horse.setDomestication(rule.domestication(horse.getDomestication(), horse.getMaxDomestication(),
                    values.value(player, TamingEffect.QUICK)));
            return;
        }
        Scheduling.entityLater(plugin, horse, 1, () -> saddle(horse, player));
    }

    private void saddle(AbstractHorse horse, Player rider) {
        if (!horse.isValid() || rider(horse) != rider) {
            unsaddle(horse);
            return;
        }
        lend(horse, Attribute.MOVEMENT_SPEED, HORSE_SPEED, values.value(rider, TamingEffect.HORSE_SPEED) / 100,
                AttributeModifier.Operation.ADD_SCALAR);
        lend(horse, Attribute.JUMP_STRENGTH, HORSE_JUMP, values.value(rider, TamingEffect.HORSE_JUMP) / 100,
                AttributeModifier.Operation.ADD_SCALAR);
    }

    private static void unsaddle(LivingEntity horse) {
        BodyService.set(horse, Attribute.MOVEMENT_SPEED, HORSE_SPEED, 0, AttributeModifier.Operation.ADD_SCALAR);
        BodyService.set(horse, Attribute.JUMP_STRENGTH, HORSE_JUMP, 0, AttributeModifier.Operation.ADD_SCALAR);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player && event.getDismounted() instanceof AbstractHorse horse) {
            Scheduling.entity(plugin, horse, () -> unsaddle(horse));
        }
    }

    /** The module stopping: everything lent taken off what is still there. */
    void stripAll() {
        for (WeakReference<LivingEntity> held : lent.values()) {
            LivingEntity animal = held.get();
            if (animal != null && animal.isValid()) {
                unsaddle(animal);
                BodyService.set(animal, Attribute.MAX_HEALTH, PET_HEARTS, 0, AttributeModifier.Operation.ADD_NUMBER);
                BodyService.clampHealth(animal);
            }
        }
        lent.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
        if (event.getPlayer().getVehicle() instanceof AbstractHorse horse) {
            unsaddle(horse);
        }
    }

    @Override
    public void forget(UUID player) {
        lent.values().removeIf(held -> held.get() == null);
    }
}
