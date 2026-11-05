package de.raindancer.modules.skills.listener;

import de.raindancer.modules.skills.SkillsServices;
import de.raindancer.modules.skills.model.Perk;
import de.raindancer.modules.skills.rules.FightRule;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The skill tree in a fight and around it: blows and arrows given and taken, dodging, blocking, the wards, the feats,
 * loot, orbs, hunger, arrows, kills in a row, potion effects and the dash. Experience is not earned here: it comes from Core's Activities (see SkillsModule). Late (HIGH), so other plugins — and Core's say on who may hurt whom — come first.
 */
public final class CombatListener implements ISkillsListener {

    /** Hurts the tree never softens: the world's, not a foe's. */
    private static final Set<EntityDamageEvent.DamageCause> UNSOFTENED = EnumSet.of(
            EntityDamageEvent.DamageCause.VOID, EntityDamageEvent.DamageCause.KILL,
            EntityDamageEvent.DamageCause.WORLD_BORDER, EntityDamageEvent.DamageCause.SUICIDE,
            EntityDamageEvent.DamageCause.STARVATION, EntityDamageEvent.DamageCause.SUFFOCATION,
            EntityDamageEvent.DamageCause.DROWNING, EntityDamageEvent.DamageCause.FALL);

    /** Hurts with a ward of their own. */
    static FightRule.Ward ward(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CAMPFIRE -> FightRule.Ward.FIRE;
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> FightRule.Ward.BLAST;
            case PROJECTILE -> FightRule.Ward.MISSILE;
            case MAGIC, POISON, WITHER, DRAGON_BREATH, SONIC_BOOM -> FightRule.Ward.MAGIC;
            default -> FightRule.Ward.NONE;
        };
    }

    private final SkillsServices services;

    public CombatListener(SkillsServices services) {
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
            var box = target.getBoundingBox();
            boolean head = FightRule.headshot(arrow.getLocation().getY(), box.getMinY(), box.getMaxY());
            event.setDamage(services.combat().dealt(shooter, target, FightRule.Kind.RANGED, false, event.getDamage(),
                    shooter.getLocation().distance(target.getLocation()), head));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStruck(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target) || event.getFinalDamage() <= 0) {
            return;
        }
        if (event.getDamager() instanceof Player striker
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            services.combat().landed(striker, target, event.getFinalDamage(), event.isCritical());
        } else if (event.getDamager() instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player shooter) {
            services.combat().arrowLanded(shooter, arrow, target);
        }
        if (target instanceof Player victim && event.getDamager() instanceof LivingEntity striker
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            services.combat().struckBy(victim, striker, event.getFinalDamage());
        }
    }

    // ------------------------------------------------------------------ blows taken

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.FALL) {
            event.setDamage(services.combat().fell(victim, event.getDamage()));
            return;
        }
        if (UNSOFTENED.contains(cause)) {
            return;
        }
        if ((cause == EntityDamageEvent.DamageCause.POISON || cause == EntityDamageEvent.DamageCause.WITHER)
                && services.combat().pure(victim)) {
            event.setCancelled(true);
            return;
        }
        Entity damager = event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null;
        Player foe = damager == null ? null : playerBehind(damager);
        boolean blow = cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                || cause == EntityDamageEvent.DamageCause.PROJECTILE;
        if (blow && damager != null && victim.isBlocking() && event.getFinalDamage() <= 0) {
            services.combat().blocked(victim, damager instanceof Projectile missile
                    && missile.getShooter() instanceof Entity shooter ? shooter : damager);
            return;
        }
        if (blow && services.combat().dodges(victim, foe != null)) {
            event.setCancelled(true);
            return;
        }
        event.setDamage(services.combat().taken(victim, event.getDamage(), foe != null, ward(cause)));
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
            services.combat().hurt(victim, event.getFinalDamage());
        }
    }

    /** Fireproof: a player who never catches fire. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (event.getEntity() instanceof Player player && services.combat().fireproof(player)) {
            event.setCancelled(true);
        }
    }

    /** Aegis: a shield that never wears out. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent event) {
        if (event.getItem().getType() == org.bukkit.Material.SHIELD && services.combat().aegis(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Dash: crouching while sprinting. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrouch(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            services.combat().dash(event.getPlayer());
        }
    }

    /** Potions that last longer, harmful effects that wear off sooner; this plugin's own are left as they are. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getNewEffect() == null
                || (event.getAction() != EntityPotionEffectEvent.Action.ADDED
                && event.getAction() != EntityPotionEffectEvent.Action.CHANGED)) {
            return;
        }
        switch (event.getCause()) {
            case PLUGIN, COMMAND, BEACON, CONDUIT, TOTEM, UNKNOWN -> {
            }
            default -> services.combat().effected(player, event.getNewEffect(),
                    event.getCause() == EntityPotionEffectEvent.Cause.POTION_DRINK);
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

    /** Kills in a row, and Bloodlust's healing. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && killer != event.getEntity()) {
            de.raindancer.modules.skills.util.Living.onTarget(services.plugin(), killer,
                    () -> services.combat().killed(killer));
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
        services.combat().loosed(shooter, arrow);
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
            second.setPierceLevel(arrow.getPierceLevel());
        }
    }

    @Override
    public void forget(UUID player) {
        services.combat().forget(player);
        services.loot().forget(player);
    }
}
