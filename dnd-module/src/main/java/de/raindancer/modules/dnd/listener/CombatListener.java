package de.raindancer.modules.dnd.listener;

import de.raindancer.modules.dnd.DndServices;
import de.raindancer.modules.dnd.model.Perk;
import de.raindancer.modules.dnd.rules.FightRule;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The skill tree in a fight and around it: blows and arrows given and taken, dodging, the feats, loot, orbs, hunger
 * and arrows. Experience is not earned here: it comes from Core's Activities (see DndModule). Late (HIGH), so other plugins — and Core's say on who may hurt whom — come first.
 */
public final class CombatListener implements IDndListener {

    /** Hurts the tree never softens: the world's, not a foe's. */
    private static final Set<EntityDamageEvent.DamageCause> UNSOFTENED = EnumSet.of(
            EntityDamageEvent.DamageCause.VOID, EntityDamageEvent.DamageCause.KILL,
            EntityDamageEvent.DamageCause.WORLD_BORDER, EntityDamageEvent.DamageCause.SUICIDE,
            EntityDamageEvent.DamageCause.STARVATION, EntityDamageEvent.DamageCause.SUFFOCATION,
            EntityDamageEvent.DamageCause.DROWNING, EntityDamageEvent.DamageCause.FALL);

    private final DndServices services;

    public CombatListener(DndServices services) {
        this.services = services;
    }

    private static Player playerBehind(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    // ------------------------------------------------------------------ blows given

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStrike(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (event.getDamager() instanceof Player striker && (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            FightRule.Kind kind = cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                    ? FightRule.Kind.SWEEP : FightRule.Kind.MELEE;
            event.setDamage(services.combat().dealt(striker, target, kind, event.isCritical(), event.getDamage(),
                    striker.getLocation().distance(target.getLocation())));
        } else if (event.getDamager() instanceof Projectile arrow && arrow.getShooter() instanceof Player shooter
                && shooter.getWorld() == target.getWorld()) {
            event.setDamage(services.combat().dealt(shooter, target, FightRule.Kind.RANGED, false, event.getDamage(),
                    shooter.getLocation().distance(target.getLocation())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStruck(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player striker && event.getEntity() instanceof LivingEntity target
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK && event.getFinalDamage() > 0) {
            services.combat().landed(striker, target, event.getFinalDamage());
        }
    }

    // ------------------------------------------------------------------ blows taken

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || UNSOFTENED.contains(event.getCause())) {
            return;
        }
        Player foe = event instanceof EntityDamageByEntityEvent byEntity ? playerBehind(byEntity.getDamager()) : null;
        boolean blow = event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE;
        if (blow && services.combat().dodges(victim, foe != null)) {
            event.setCancelled(true);
            return;
        }
        event.setDamage(services.combat().taken(victim, event.getDamage(), foe != null));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && event.getFinalDamage() >= victim.getHealth()
                && event.getCause() != EntityDamageEvent.DamageCause.VOID
                && event.getCause() != EntityDamageEvent.DamageCause.KILL
                && services.combat().undying(victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurtLanded(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && event.getFinalDamage() > 0) {
            services.combat().hurt(victim);
        }
    }

    // ------------------------------------------------------------------ loot, orbs, hunger, arrows

    /** A monster an adventurer killed may drop a magic item. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrops(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && !(event.getEntity() instanceof Player)) {
            double luck = services.combat().chance(killer, Perk.LOOT) * 100;
            services.loot().roll(killer, event.getEntity(), luck).ifPresent(event.getDrops()::add);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onOrbs(PlayerExpChangeEvent event) {
        event.setAmount(services.combat().orbs(event.getPlayer(), event.getAmount()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && event.getFoodLevel() < player.getFoodLevel()
                && ThreadLocalRandom.current().nextDouble() < services.combat().chance(player, Perk.HUNGER_SAVE)) {
            event.setCancelled(true);
        }
    }

    /** Fletcher's arrows that are not used up, and Volley's second one. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter) || !(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (event.shouldConsumeItem() && random.nextDouble() < services.combat().chance(shooter, Perk.ARROW_SAVE)) {
            event.setConsumeItem(false);
            // An arrow that was not used up must not be picked up a second time.
            arrow.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
            shooter.updateInventory();
        }
        if (random.nextDouble() < services.combat().chance(shooter, Perk.VOLLEY)) {
            var velocity = arrow.getVelocity().clone().rotateAroundY(Math.toRadians(random.nextDouble(-4, 4)));
            AbstractArrow second = shooter.launchProjectile(org.bukkit.entity.Arrow.class, velocity);
            second.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
            second.setDamage(arrow.getDamage());
            second.setCritical(arrow.isCritical());
            second.setFireTicks(arrow.getFireTicks());
        }
    }

    @Override
    public void forget(UUID player) {
        services.combat().forget(player);
        services.loot().forget(player);
    }
}
