package de.raindancer.modules.dnd.service;

import de.raindancer.core.platform.util.Cooldowns;
import de.raindancer.core.ui.actionbar.ActionBarPriority;
import de.raindancer.core.ui.actionbar.ActionBars;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.Ability;
import de.raindancer.modules.dnd.model.Condition;
import de.raindancer.modules.dnd.model.Dice;
import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.Spell;
import de.raindancer.modules.dnd.rules.CheckRule;
import de.raindancer.modules.dnd.rules.FeatureRule;
import de.raindancer.modules.dnd.rules.MartialRule;
import de.raindancer.modules.dnd.rules.MobRule;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** What classes do to blows: given and taken. And the experience a kill is worth. */
public final class CombatService implements IDndService {

    /** Sneak Attack is once a turn; a turn is this long. */
    private static final Duration TURN = Duration.ofMillis(1_500);
    private static final Duration DODGE = Duration.ofSeconds(6);
    private static final int EXPLOSION_DC = 13;

    private final Messages messages;
    private final ActionBars actionBars;
    private final HeroService heroes;
    private final ConditionService conditions;
    private final ConcentrationService concentration;
    private final MartialRule martial;
    private final FeatureRule features;
    private final CheckRule checks;
    private final MobRule mobs;
    private final RandomGenerator random;

    private final Cooldowns<UUID> sneakAttacks = new Cooldowns<>();
    private final Cooldowns<UUID> dodges = new Cooldowns<>();

    private volatile DndSettings settings = DndSettings.DEFAULTS;

    public CombatService(Messages messages, ActionBars actionBars, HeroService heroes, ConditionService conditions,
                         ConcentrationService concentration, MartialRule martial, FeatureRule features,
                         CheckRule checks, MobRule mobs, RandomGenerator random) {
        this.messages = messages;
        this.actionBars = actionBars;
        this.heroes = heroes;
        this.conditions = conditions;
        this.concentration = concentration;
        this.martial = martial;
        this.features = features;
        this.checks = checks;
        this.mobs = mobs;
        this.random = random;
        sneakAttacks.every(TURN);
        dodges.every(DODGE);
    }

    @Override
    public void settings(DndSettings updated) {
        this.settings = updated == null ? DndSettings.DEFAULTS : updated;
    }

    private Optional<Hero> active(Player player) {
        if (!settings.worksIn(player.getWorld().getName())) {
            return Optional.empty();
        }
        return heroes.of(player.getUniqueId());
    }

    /** A melee blow by a player: what it does with their class behind it. */
    public double melee(Player attacker, LivingEntity target, double base) {
        Optional<Hero> found = active(attacker);
        if (found.isEmpty()) {
            return base;
        }
        Hero hero = found.get();
        UUID id = attacker.getUniqueId();
        boolean unseen = attacker.hasPotionEffect(PotionEffectType.INVISIBILITY);
        double damage = martial.melee(hero, base, conditions.has(id, Condition.RAGING),
                conditions.has(id, Condition.RECKLESS));
        if (Feature.SNEAK_ATTACK.has(hero.heroClass(), hero.level()) && sneakAttacks.isReady(id)) {
            boolean busy = target instanceof Mob mob && mob.getTarget() != null && !attacker.equals(mob.getTarget());
            if (martial.sneakAttackApplies(attacker.isSneaking(), unseen, busy, degreesFromBehind(attacker, target))) {
                int extra = Dice.of(features.sneakAttackDice(hero.level()), 6).roll(random);
                damage += extra;
                sneakAttacks.start(id);
                actionBars.show(id, "dnd-cast", messages.get("dnd.sneak-attack", "damage", String.valueOf(extra)),
                        Duration.ofMillis(1500), ActionBarPriority.NORMAL);
            }
        }
        if (conditions.clear(id, Condition.HIDDEN) || unseen) {
            concentration.on(id).filter(focus -> focus.spell() == Spell.INVISIBILITY)
                    .ifPresent(focus -> concentration.end(attacker, "dnd.cast.visible"));
            if (!concentration.on(id).filter(focus -> focus.spell() == Spell.GREATER_INVISIBILITY).isPresent()) {
                attacker.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
        }
        return damage;
    }

    /** 0 when the attacker stands straight behind the target, 180 straight in front of it. */
    static double degreesFromBehind(LivingEntity attacker, LivingEntity target) {
        Vector facing = target.getLocation().getDirection().setY(0);
        Vector toAttacker = attacker.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
        if (facing.lengthSquared() < 1e-6 || toAttacker.lengthSquared() < 1e-6) {
            return 180;
        }
        double cos = facing.normalize().dot(toAttacker.normalize());
        return 180 - Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
    }

    /**
     * A blow landing on a player: rage, Shield, Reckless Attack, Uncanny Dodge and Evasion — and Death Ward last.
     *
     * @param physical a blow or an arrow
     * @param explosion a creeper, TNT, a fireball's blast
     */
    public double incoming(Player victim, double damage, boolean physical, boolean explosion) {
        Optional<Hero> found = active(victim);
        UUID id = victim.getUniqueId();
        double left = damage;
        if (found.isPresent()) {
            Hero hero = found.get();
            boolean dodge = physical && Feature.UNCANNY_DODGE.has(hero.heroClass(), hero.level())
                    && dodges.tryUse(id);
            left = martial.incoming(damage, conditions.has(id, Condition.RAGING), physical,
                    conditions.has(id, Condition.RECKLESS), conditions.has(id, Condition.SHIELDED), dodge);
            if (dodge) {
                messages.send(victim, "dnd.uncanny-dodge");
            }
            if (explosion && Feature.EVASION.has(hero.heroClass(), hero.level())) {
                boolean saved = checks.save(checks.d20(random, false), checks.saveBonus(hero, Ability.DEXTERITY),
                        EXPLOSION_DC).succeeded();
                left = saved ? 0 : left / 2;
            }
        }
        double health = victim.getHealth() + victim.getAbsorptionAmount();
        if (left >= health && conditions.clear(id, Condition.WARDED)) {
            left = Math.max(0, health - 1);
            messages.send(victim, "dnd.death-ward");
        }
        return left;
    }

    /** After a hit has landed: concentration is tested. */
    public void landed(Player victim, double damage) {
        heroes.of(victim.getUniqueId()).ifPresent(hero -> concentration.hit(victim, hero, damage));
    }

    /** A kill by a player: the experience it is worth. */
    public void killed(Player killer, LivingEntity victim) {
        if (active(killer).isEmpty() || victim instanceof Player) {
            return;
        }
        int xp = mobs.of(victim.getType()).xp();
        if (xp > 0) {
            heroes.grantXp(killer, xp, victim.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '),
                    true);
        }
    }

    public void forget(UUID player) {
        sneakAttacks.forget(player);
        dodges.forget(player);
        conditions.forget(player);
    }
}
