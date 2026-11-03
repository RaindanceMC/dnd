package de.raindancer.modules.dnd.service;

import de.raindancer.modules.dnd.model.DamageKind;
import de.raindancer.modules.dnd.model.Scroll;
import de.raindancer.modules.dnd.rules.ItemRule;
import de.raindancer.modules.dnd.util.Living;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Optional;

/**
 * What a scroll or a wand sets off. Each hurt goes through the game as a magic hurt from the reader, so the server's
 * say on who may hurt whom — PvP, claims, protected mobs — is asked first, by {@link TargetService#foes}.
 */
public final class ScrollService implements IDndService {

    private static final double REACH = 24;
    private static final double FIREBALL_RADIUS = 4;
    private static final double HEALING_RADIUS = 8;

    private final Plugin plugin;
    private final TargetService targets;
    private final SpellCues cues;
    private final ItemRule rule;

    public ScrollService(Plugin plugin, TargetService targets, SpellCues cues, ItemRule rule) {
        this.plugin = plugin;
        this.targets = targets;
        this.cues = cues;
        this.rule = rule;
    }

    @Override
    public void settings(de.raindancer.modules.dnd.DndSettings settings) {
        // Reach and strength are the scroll's own.
    }

    /**
     * Sets the scroll off.
     *
     * @param tier    1 to 5
     * @param percent how much stronger — Spellbreaker and Archmage
     * @param darts   for Magic Missile, how many
     * @return whether it went off; nothing to aim at is not going off, and the scroll is kept
     */
    public boolean cast(Player reader, Scroll scroll, int tier, double percent, int darts) {
        double power = rule.power(tier, (int) Math.round(percent));
        return switch (scroll) {
            case MAGIC_MISSILE -> missiles(reader, power * 0.4, darts);
            case FIREBALL -> fireball(reader, power);
            case LIGHTNING -> lightning(reader, power * 1.5);
            case HEALING -> healing(reader, power);
            case MISTY_STEP -> step(reader, 8 + 4 * Scroll.clampTier(tier));
            case SHIELD -> shield(reader, tier);
        };
    }

    private boolean missiles(Player reader, double each, int darts) {
        Optional<LivingEntity> target = targets.lookedAt(reader, REACH, targets.foes(reader));
        if (target.isEmpty()) {
            return false;
        }
        Location from = reader.getEyeLocation();
        cues.ray(from, TargetService.middleOf(target.get()), DamageKind.FORCE);
        hurt(reader, target.get(), each * Math.max(1, darts));
        return true;
    }

    private boolean fireball(Player reader, double damage) {
        Location centre = targets.pointLookedAt(reader, REACH);
        cues.ray(reader.getEyeLocation(), centre, DamageKind.FIRE);
        cues.ring(centre, FIREBALL_RADIUS, DamageKind.FIRE);
        List<LivingEntity> caught = targets.around(centre, FIREBALL_RADIUS, targets.foes(reader));
        for (LivingEntity each : caught) {
            hurt(reader, each, damage);
            Living.onTarget(plugin, each, () -> each.setFireTicks(Math.max(each.getFireTicks(), 80)));
        }
        return true;
    }

    private boolean lightning(Player reader, double damage) {
        Optional<LivingEntity> target = targets.lookedAt(reader, REACH, targets.foes(reader));
        if (target.isEmpty()) {
            return false;
        }
        target.get().getWorld().strikeLightningEffect(target.get().getLocation());
        cues.at(TargetService.middleOf(target.get()), DamageKind.LIGHTNING);
        hurt(reader, target.get(), damage);
        return true;
    }

    private boolean healing(Player reader, double amount) {
        // The reader first, then everybody near.
        for (Player friend : targets.allies(reader, HEALING_RADIUS)) {
            Living.onTarget(plugin, friend, () -> friend.setHealth(Math.min(TargetService.maxHealth(friend),
                    friend.getHealth() + amount)));
            cues.at(TargetService.middleOf(friend), DamageKind.HEALING);
        }
        return true;
    }

    private boolean step(Player reader, double blocks) {
        Location to = TargetService.standable(targets.pointLookedAt(reader, blocks));
        if (to == null) {
            return false;
        }
        cues.at(TargetService.middleOf(reader), DamageKind.FORCE);
        to.setYaw(reader.getLocation().getYaw());
        to.setPitch(reader.getLocation().getPitch());
        reader.teleportAsync(to);
        return true;
    }

    private boolean shield(Player reader, int tier) {
        int clamped = Scroll.clampTier(tier);
        reader.setAbsorptionAmount(Math.max(reader.getAbsorptionAmount(), 2.0 * clamped));
        Living.give(reader, PotionEffectType.RESISTANCE, 6 + 2 * clamped, 0);
        cues.at(TargetService.middleOf(reader), DamageKind.FORCE);
        return true;
    }

    private void hurt(Player reader, LivingEntity target, double amount) {
        Living.onTarget(plugin, target, () -> target.damage(amount, DamageSource.builder(DamageType.MAGIC)
                .withCausingEntity(reader).withDirectEntity(reader).build()));
    }
}
