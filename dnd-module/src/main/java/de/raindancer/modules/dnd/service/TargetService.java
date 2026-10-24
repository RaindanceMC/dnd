package de.raindancer.modules.dnd.service;

import de.raindancer.core.world.combat.Attack;
import de.raindancer.core.world.combat.Combat;
import de.raindancer.modules.dnd.DndSettings;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Who a spell reaches: what the caster looks at, what stands around a point, in a cone or along a line — and whether
 * the server's combat rules let the caster touch them at all.
 */
public final class TargetService implements IDndService {

    private static final double AIM_SLACK = 0.7;

    private final Combat combat;

    public TargetService(Combat combat) {
        this.combat = combat;
    }

    @Override
    public void settings(DndSettings settings) {
        // Reach is the spell's; nothing configurable here.
    }

    /** Whether the server's rules — PvP, claims, protected mobs — let this caster hurt or hinder that creature. */
    public boolean mayHarm(Player caster, LivingEntity target) {
        if (target.equals(caster)) {
            return false;
        }
        Location to = target.getLocation();
        Location from = caster.getLocation();
        Attack attack = new Attack(Attack.Fighter.PLAYER, caster.getUniqueId(),
                target instanceof Player ? Attack.Fighter.PLAYER : Attack.Fighter.MOB, target.getUniqueId(),
                to.getWorld().getName(), false, new Attack.At(to.getX(), to.getY(), to.getZ()),
                new Attack.At(from.getX(), from.getY(), from.getZ()), Attack.Means.OTHER);
        return combat.judge(attack).allowed();
    }

    /** Living, here, not the caster, not an armour stand, not the caster's own pet, not a spectator. */
    public Predicate<LivingEntity> foes(Player caster) {
        return entity -> isAround(entity) && !entity.equals(caster) && !(entity instanceof ArmorStand)
                && !(entity instanceof Tameable pet && caster.getUniqueId().equals(ownerOf(pet)))
                && mayHarm(caster, entity);
    }

    /** Every player who can be helped: alive, here, not watching. */
    public Predicate<LivingEntity> friends() {
        return entity -> entity instanceof Player player && isAround(player);
    }

    private static boolean isAround(LivingEntity entity) {
        if (entity.isDead() || !entity.isValid()) {
            return false;
        }
        return !(entity instanceof Player player) || player.getGameMode() != GameMode.SPECTATOR;
    }

    private static UUID ownerOf(Tameable pet) {
        return pet.getOwnerUniqueId();
    }

    /** The first creature along the caster's line of sight, if no block is in the way first. */
    public Optional<LivingEntity> lookedAt(Player caster, double range, Predicate<LivingEntity> which) {
        Location eye = caster.getEyeLocation();
        RayTraceResult hit = caster.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER,
                true, AIM_SLACK, entity -> entity instanceof LivingEntity living && !living.equals(caster)
                        && which.test(living));
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity living)) {
            return Optional.empty();
        }
        return Optional.of(living);
    }

    /** Where the caster's line of sight ends: the block it meets, or {@code range} ahead. */
    public Location pointLookedAt(Player caster, double range) {
        Location eye = caster.getEyeLocation();
        RayTraceResult hit = caster.getWorld().rayTraceBlocks(eye, eye.getDirection(), range,
                FluidCollisionMode.NEVER, true);
        if (hit == null) {
            return eye.clone().add(eye.getDirection().multiply(range));
        }
        Vector at = hit.getHitPosition();
        Vector back = eye.getDirection().multiply(-0.5);
        return new Location(caster.getWorld(), at.getX(), at.getY(), at.getZ()).add(back);
    }

    public List<LivingEntity> around(Location centre, double radius, Predicate<LivingEntity> which) {
        List<LivingEntity> found = new ArrayList<>();
        for (LivingEntity entity : centre.getWorld().getNearbyLivingEntities(centre, radius)) {
            if (entity.getLocation().distanceSquared(centre) <= radius * radius + 1 && which.test(entity)) {
                found.add(entity);
            }
        }
        found.sort(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(centre)));
        return found;
    }

    /** In front of the caster, within {@code length} and {@code halfAngle} degrees of where they look. */
    public List<LivingEntity> inCone(Player caster, double length, double halfAngle, Predicate<LivingEntity> which) {
        Location eye = caster.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        double cos = Math.cos(Math.toRadians(halfAngle));
        List<LivingEntity> found = new ArrayList<>();
        for (LivingEntity entity : around(eye, length, which)) {
            Vector to = entity.getBoundingBox().getCenter().subtract(eye.toVector());
            if (to.lengthSquared() < 1.0 || to.normalize().dot(look) >= cos) {
                found.add(entity);
            }
        }
        return found;
    }

    /** Along the caster's line of sight, within {@code width} of it, up to the first wall. */
    public List<LivingEntity> alongLine(Player caster, double length, double width, Predicate<LivingEntity> which) {
        Location eye = caster.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        double reach = Math.min(length, eye.distance(pointLookedAt(caster, length)) + 0.5);
        List<LivingEntity> found = new ArrayList<>();
        Location middle = eye.clone().add(look.clone().multiply(reach / 2));
        for (LivingEntity entity : around(middle, reach / 2 + width, which)) {
            Vector to = entity.getBoundingBox().getCenter().subtract(eye.toVector());
            double along = to.dot(look);
            if (along < 0 || along > reach) {
                continue;
            }
            double off = to.clone().subtract(look.clone().multiply(along)).length();
            if (off <= width + entity.getWidth() / 2) {
                found.add(entity);
            }
        }
        return found;
    }

    /** Players near the caster, the caster first, the most hurt next. */
    public List<Player> allies(Player caster, double radius) {
        List<Player> found = new ArrayList<>();
        found.add(caster);
        for (LivingEntity entity : around(caster.getLocation(), radius, friends())) {
            if (entity instanceof Player player && !player.equals(caster)) {
                found.add(player);
            }
        }
        found.subList(1, found.size()).sort(Comparator.comparingDouble(player -> player.getHealth()
                / Math.max(1.0, maxHealth(player))));
        return found;
    }

    public static double maxHealth(LivingEntity entity) {
        var attribute = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }

    /** Feet and head free, something solid underneath within three blocks — the spot to stand on, or null. */
    public static Location standable(Location around) {
        for (int down = 0; down <= 3; down++) {
            Location feet = around.clone().subtract(0, down, 0);
            org.bukkit.block.Block foot = feet.getBlock();
            org.bukkit.block.Block head = foot.getRelative(0, 1, 0);
            org.bukkit.block.Block ground = foot.getRelative(0, -1, 0);
            if (foot.isPassable() && head.isPassable() && !ground.isPassable() && !foot.isLiquid()) {
                return foot.getLocation().add(0.5, 0, 0.5);
            }
        }
        return null;
    }

    /** The point at the middle of a creature, where particles look right. */
    public static Location middleOf(Entity entity) {
        return entity.getLocation().add(0, entity.getHeight() / 2, 0);
    }
}
