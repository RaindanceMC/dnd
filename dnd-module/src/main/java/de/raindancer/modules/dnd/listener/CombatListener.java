package de.raindancer.modules.dnd.listener;

import de.raindancer.modules.dnd.DndServices;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.UUID;

/** Class features in a fight: blows given, blows taken, and the experience a kill brings. */
public final class CombatListener implements IDndListener {

    private final DndServices services;

    public CombatListener(DndServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity target)
                || event.getDamageSource().getDamageType() != DamageType.PLAYER_ATTACK) {
            return;
        }
        event.setDamage(services.combat().melee(attacker, target, event.getDamage()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean physical = cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                || cause == EntityDamageEvent.DamageCause.PROJECTILE;
        boolean explosion = cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
        boolean fire = cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA || cause == EntityDamageEvent.DamageCause.HOT_FLOOR
                || cause == EntityDamageEvent.DamageCause.CAMPFIRE;
        event.setDamage(services.combat().incoming(victim, event.getDamage(), physical, explosion, fire));
        services.resting().interrupt(victim, "dnd.rest.hurt");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLanded(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && event.getFinalDamage() > 0) {
            services.combat().landed(victim, event.getFinalDamage());
        }
    }

    /** Retaliation, once a melee blow on a raging berserker has landed. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStruck(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof LivingEntity attacker
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            services.combat().retaliate(victim, attacker);
        }
    }

    /** What a surge summoned drops nothing; a monster an adventurer killed may drop a magic item. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrops(EntityDeathEvent event) {
        if (de.raindancer.modules.dnd.service.SurgeService.isSummoned(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null && !(event.getEntity() instanceof Player)
                && services.heroes().of(killer.getUniqueId()).isPresent()) {
            services.loot().roll(killer, event.getEntity()).ifPresent(event.getDrops()::add);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (de.raindancer.modules.dnd.service.SurgeService.isSummoned(event.getEntity())) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            services.combat().killed(killer, event.getEntity());
        }
    }

    @Override
    public void forget(UUID player) {
        // Remembered per player in CombatService; PresenceListener tells it.
    }

    @Override
    public String describe() {
        return "class features in combat, and experience for kills";
    }
}
