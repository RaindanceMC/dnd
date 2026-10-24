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
        event.setDamage(services.combat().incoming(victim, event.getDamage(), physical, explosion));
        services.resting().interrupt(victim, "dnd.rest.hurt");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLanded(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && event.getFinalDamage() > 0) {
            services.combat().landed(victim, event.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
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
