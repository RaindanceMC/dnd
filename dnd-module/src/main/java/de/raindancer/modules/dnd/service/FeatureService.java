package de.raindancer.modules.dnd.service;

import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.Condition;
import de.raindancer.modules.dnd.model.DamageKind;
import de.raindancer.modules.dnd.model.Dice;
import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.MobProfile;
import de.raindancer.modules.dnd.model.Roll;
import de.raindancer.modules.dnd.rules.CheckRule;
import de.raindancer.modules.dnd.rules.FeatureRule;
import de.raindancer.modules.dnd.rules.MobRule;
import de.raindancer.modules.dnd.rules.SpellSlotRule;
import de.raindancer.modules.dnd.util.Living;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.random.RandomGenerator;

/** What an active class feature does. Spending its use is the caller's, and only when this answers true. */
public final class FeatureService implements IDndService {

    private static final int RAGE_SECONDS = 60;
    private static final int RECKLESS_SECONDS = 8;
    private static final int TURN_RADIUS = 9;

    private final Plugin plugin;
    private final Messages messages;
    private final Effects effects;
    private final TargetService targets;
    private final SpellCues cues;
    private final ConditionService conditions;
    private final FeatureRule features;
    private final SpellSlotRule slots;
    private final CheckRule checks;
    private final MobRule mobs;
    private final RandomGenerator random;
    private final java.util.Set<java.util.UUID> winged = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<java.util.UUID> raging = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile java.util.function.Consumer<java.util.UUID> tides = player -> { };

    public FeatureService(Plugin plugin, Messages messages, Effects effects, TargetService targets, SpellCues cues,
                          ConditionService conditions, FeatureRule features, SpellSlotRule slots, CheckRule checks,
                          MobRule mobs, RandomGenerator random) {
        this.plugin = plugin;
        this.messages = messages;
        this.effects = effects;
        this.targets = targets;
        this.cues = cues;
        this.conditions = conditions;
        this.features = features;
        this.slots = slots;
        this.checks = checks;
        this.mobs = mobs;
        this.random = random;
    }

    /** Told when Tides of Chaos is used: the surge it owes comes with the next spell. */
    public void onTides(java.util.function.Consumer<java.util.UUID> owed) {
        this.tides = owed == null ? player -> { } : owed;
    }

    @Override
    public void settings(DndSettings settings) {
        // Every feature's numbers are the class's own.
    }

    /**
     * Uses it. The hero handed back carries whatever it changed beside the use itself (Arcane Recovery's slots);
     * null when it did not go off, with the reason told.
     */
    public Hero use(Player player, Hero hero, Feature feature) {
        return switch (feature) {
            case SECOND_WIND -> secondWind(player, hero);
            case ACTION_SURGE -> actionSurge(player, hero);
            case RAGE -> rage(player, hero);
            case RECKLESS_ATTACK -> {
                conditions.set(player.getUniqueId(), Condition.RECKLESS, RECKLESS_SECONDS * 1000L);
                cues.at(TargetService.middleOf(player), DamageKind.BLUDGEONING);
                messages.send(player, "dnd.feature.reckless");
                yield hero;
            }
            case CUNNING_DASH -> {
                Living.give(player, PotionEffectType.SPEED, 4, 2);
                cues.at(player.getLocation(), DamageKind.NONE);
                yield hero;
            }
            case CUNNING_HIDE -> hide(player, hero);
            case CUNNING_DISENGAGE -> {
                Vector back = player.getLocation().getDirection().setY(0);
                if (back.lengthSquared() < 0.01) {
                    back = new Vector(0, 0, 1);
                }
                player.setVelocity(back.normalize().multiply(-1.3).setY(0.55));
                player.setFallDistance(0);
                Living.give(player, PotionEffectType.SLOW_FALLING, 2, 0);
                yield hero;
            }
            case TURN_UNDEAD -> turnUndead(player, hero);
            case PRESERVE_LIFE -> preserveLife(player, hero);
            case TIDES_OF_CHAOS -> {
                conditions.set(player.getUniqueId(), Condition.TIDES, 60_000L);
                tides.accept(player.getUniqueId());
                cues.at(TargetService.middleOf(player), DamageKind.FORCE);
                messages.send(player, "dnd.feature.tides");
                yield hero;
            }
            case BEND_LUCK -> {
                conditions.set(player.getUniqueId(), Condition.BENT_LUCK, 6_000L);
                messages.send(player, "dnd.feature.bend-luck");
                yield hero;
            }
            case OVERCHANNEL -> {
                conditions.set(player.getUniqueId(), Condition.OVERCHANNELLING, 60_000L);
                cues.at(TargetService.middleOf(player), DamageKind.FORCE);
                messages.send(player, "dnd.feature.overchannel");
                yield hero;
            }
            case INTIMIDATING_PRESENCE -> intimidate(player, hero);
            case DRAGON_WINGS -> wings(player, hero);
            case DRACONIC_PRESENCE -> presence(player, hero);
            case ARCANE_RECOVERY -> {
                if (!slots.wouldRecover(hero)) {
                    messages.send(player, "dnd.feature.nothing-to-recover");
                    yield null;
                }
                cues.at(TargetService.middleOf(player), DamageKind.FORCE);
                yield slots.recover(hero, slots.recoveryBudget(hero.level()));
            }
            default -> null;
        };
    }

    private Hero secondWind(Player player, Hero hero) {
        double max = TargetService.maxHealth(player);
        if (player.getHealth() >= max) {
            messages.send(player, "dnd.feature.already-healthy");
            return null;
        }
        int healed = Dice.of(1, 10).roll(random) + hero.level();
        player.setHealth(Math.min(max, player.getHealth() + healed));
        cues.at(TargetService.middleOf(player), DamageKind.HEALING);
        messages.send(player, "dnd.feature.second-wind", "health", String.valueOf(healed));
        return hero;
    }

    private Hero actionSurge(Player player, Hero hero) {
        Living.give(player, PotionEffectType.HASTE, 6, 1);
        Living.give(player, PotionEffectType.SPEED, 6, 1);
        Living.give(player, PotionEffectType.STRENGTH, 6, 0);
        effects.play(player.getUniqueId(), Cues.ABILITY);
        messages.send(player, "dnd.feature.action-surge");
        return hero;
    }

    private Hero rage(Player player, Hero hero) {
        if (conditions.has(player.getUniqueId(), Condition.RAGING)) {
            messages.send(player, "dnd.feature.already-raging");
            return null;
        }
        conditions.set(player.getUniqueId(), Condition.RAGING, RAGE_SECONDS * 1000L);
        cues.at(TargetService.middleOf(player), DamageKind.FIRE);
        messages.send(player, "dnd.feature.rage", "bonus", String.valueOf(features.rageDamage(hero.level())));
        return hero;
    }

    private Hero hide(Player player, Hero hero) {
        boolean supreme = Feature.SUPREME_SNEAK.has(hero);
        int seconds = supreme ? 12 : 6;
        Living.give(player, PotionEffectType.INVISIBILITY, seconds, 0);
        if (supreme) {
            Living.give(player, PotionEffectType.SPEED, seconds, 0);
        }
        conditions.set(player.getUniqueId(), Condition.HIDDEN, seconds * 1000L);
        for (LivingEntity near : targets.around(player.getLocation(), 24, entity -> entity instanceof Mob)) {
            if (near instanceof Mob mob && player.equals(mob.getTarget())) {
                Living.onTarget(plugin, mob, () -> mob.setTarget(null));
            }
        }
        cues.at(TargetService.middleOf(player), DamageKind.NONE);
        return hero;
    }

    private Hero intimidate(Player player, Hero hero) {
        java.util.Optional<LivingEntity> aimed = targets.lookedAt(player, 9, targets.foes(player));
        if (aimed.isEmpty()) {
            messages.send(player, "dnd.feature.nobody-to-glare-at");
            return null;
        }
        int dc = 8 + checks.spellAttackBonus(hero) - checks.castingModifier(hero)
                + hero.modifier(de.raindancer.modules.dnd.model.Ability.CHARISMA);
        frightenIfFailed(player, aimed.get(), dc, 30);
        return hero;
    }

    private Hero presence(Player player, Hero hero) {
        List<LivingEntity> foes = targets.around(player.getLocation(), 18,
                targets.foes(player).and(entity -> entity instanceof org.bukkit.entity.Enemy));
        cues.ring(player.getLocation(), 9, DamageKind.FIRE);
        int frightened = 0;
        for (LivingEntity foe : foes) {
            if (frightenIfFailed(player, foe, checks.spellDc(hero), 60)) {
                frightened++;
            }
        }
        messages.send(player, "dnd.feature.presence", "count", String.valueOf(frightened), "of",
                String.valueOf(foes.size()));
        return hero;
    }

    /** A Wisdom save, and on a failure weakness, slowness and a shove away. */
    private boolean frightenIfFailed(Player player, LivingEntity target, int dc, int seconds) {
        int bonus = target instanceof Player ? 0 : mobs.of(target.getType()).saveBonus();
        if (checks.save(checks.d20(random, false), bonus, dc).succeeded()) {
            return false;
        }
        conditions.set(target.getUniqueId(), Condition.FRIGHTENED, seconds * 1000L);
        Vector away = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        Vector push = away.lengthSquared() < 0.01 ? new Vector(0, 0.3, 0) : away.normalize().multiply(1.1).setY(0.3);
        Living.onTarget(plugin, target, () -> {
            Living.give(target, PotionEffectType.WEAKNESS, seconds, 1);
            Living.give(target, PotionEffectType.SLOWNESS, seconds, 0);
            target.setVelocity(push);
            if (target instanceof Mob mob) {
                mob.setTarget(null);
            }
        });
        return true;
    }

    /** Dragon Wings: flight on, or off again. Flight this module did not give is never touched. */
    private Hero wings(Player player, Hero hero) {
        if (winged.remove(player.getUniqueId())) {
            fold(player);
            messages.send(player, "dnd.feature.wings-folded");
            return hero;
        }
        if (player.getAllowFlight()) {
            messages.send(player, "dnd.feature.already-flying");
            return null;
        }
        winged.add(player.getUniqueId());
        player.setAllowFlight(true);
        player.setFlying(true);
        cues.at(TargetService.middleOf(player), DamageKind.FIRE);
        messages.send(player, "dnd.feature.wings-spread");
        return hero;
    }

    private void fold(Player player) {
        if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL || player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
            player.setFlying(false);
            player.setAllowFlight(false);
            Living.give(player, PotionEffectType.SLOW_FALLING, 6, 0);
        }
    }

    /** Folds the wings of somebody leaving, dying or stepping out of adventuring. */
    public void foldWings(Player player) {
        if (winged.remove(player.getUniqueId())) {
            fold(player);
        }
    }

    public boolean isWinged(java.util.UUID player) {
        return winged.contains(player);
    }

    /** Once a second, on the player's thread: what a subclass does while time passes. */
    public void tick(Player player, java.util.Optional<Hero> found) {
        java.util.UUID id = player.getUniqueId();
        if (found.isEmpty()) {
            foldWings(player);
            raging.remove(id);
            return;
        }
        Hero hero = found.get();
        if (winged.contains(id) && !Feature.DRAGON_WINGS.has(hero)) {
            foldWings(player);
        }
        double max = TargetService.maxHealth(player);
        if (Feature.SURVIVOR.has(hero) && player.getHealth() > 0 && player.getHealth() < max / 2) {
            double heal = (5 + Math.max(0, hero.modifier(de.raindancer.modules.dnd.model.Ability.CONSTITUTION))) / 6.0;
            player.setHealth(Math.min(max, player.getHealth() + heal));
        }
        boolean rage = conditions.has(id, Condition.RAGING);
        if (rage) {
            raging.add(id);
            if (Feature.FRENZY.has(hero)) {
                Living.give(player, PotionEffectType.HASTE, 2, 1);
            }
            if (Feature.MINDLESS_RAGE.has(hero)) {
                player.removePotionEffect(PotionEffectType.NAUSEA);
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.removePotionEffect(PotionEffectType.DARKNESS);
            }
        } else if (raging.remove(id) && Feature.FRENZY.has(hero)) {
            Living.give(player, PotionEffectType.HUNGER, 20, 1);
            Living.give(player, PotionEffectType.SLOWNESS, 20, 0);
            messages.send(player, "dnd.feature.exhausted");
        }
    }

    public void forget(java.util.UUID player) {
        raging.remove(player);
        winged.remove(player);
    }

    private Hero turnUndead(Player player, Hero hero) {
        List<LivingEntity> undead = targets.around(player.getLocation(), TURN_RADIUS,
                targets.foes(player).and(entity -> mobs.of(entity.getType()).undead()));
        cues.ring(player.getLocation(), TURN_RADIUS, DamageKind.RADIANT);
        if (undead.isEmpty()) {
            messages.send(player, "dnd.feature.no-undead");
            return null;
        }
        int dc = checks.spellDc(hero);
        int destroyUpTo = features.destroysUndeadUpTo(hero.level());
        int turned = 0;
        int destroyed = 0;
        for (LivingEntity target : undead) {
            MobProfile profile = mobs.of(target.getType());
            Roll save = checks.save(checks.d20(random, false), profile.saveBonus(), dc);
            if (save.succeeded()) {
                continue;
            }
            if (profile.xp() > 0 && profile.xp() <= destroyUpTo) {
                destroyed++;
                DamageSource source = DamageSource.builder(DamageType.MAGIC).withCausingEntity(player)
                        .withDirectEntity(player).build();
                Living.onTarget(plugin, target, () -> target.damage(target.getHealth()
                        + target.getAbsorptionAmount() + 1, source));
                continue;
            }
            turned++;
            Vector away = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
            Vector push = away.lengthSquared() < 0.01 ? new Vector(0, 0.3, 0) : away.normalize().multiply(1.2).setY(0.3);
            Living.onTarget(plugin, target, () -> {
                Living.give(target, PotionEffectType.WEAKNESS, 30, 1);
                Living.give(target, PotionEffectType.SLOWNESS, 30, 0);
                target.setVelocity(push);
                if (target instanceof Mob mob) {
                    mob.setTarget(null);
                }
            });
        }
        messages.send(player, "dnd.feature.turned", "turned", String.valueOf(turned), "destroyed",
                String.valueOf(destroyed), "count", String.valueOf(undead.size()));
        return hero;
    }

    private Hero preserveLife(Player player, Hero hero) {
        double pool = 5.0 * hero.level();
        double total = 0;
        int helped = 0;
        for (Player ally : targets.allies(player, 9)) {
            double half = TargetService.maxHealth(ally) / 2;
            double room = Math.max(0, half - ally.getHealth());
            double given = Math.min(pool, room);
            if (given <= 0) {
                continue;
            }
            pool -= given;
            total += given;
            helped++;
            Living.onTarget(plugin, ally, () -> {
                ally.setHealth(Math.min(TargetService.maxHealth(ally), ally.getHealth() + given));
                cues.at(TargetService.middleOf(ally), DamageKind.HEALING);
            });
            if (pool <= 0) {
                break;
            }
        }
        if (helped == 0) {
            messages.send(player, "dnd.feature.nobody-low");
            return null;
        }
        messages.send(player, "dnd.feature.preserve-life", "health", String.valueOf(Math.round(total)), "count",
                String.valueOf(helped));
        return hero;
    }
}
