package de.raindancer.modules.dnd.service;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.Ability;
import de.raindancer.modules.dnd.model.Condition;
import de.raindancer.modules.dnd.model.DamageKind;
import de.raindancer.modules.dnd.model.Dice;
import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.Metamagic;
import de.raindancer.modules.dnd.model.MobProfile;
import de.raindancer.modules.dnd.model.Roll;
import de.raindancer.modules.dnd.model.Spell;
import de.raindancer.modules.dnd.model.SpellShape;
import de.raindancer.modules.dnd.rules.CheckRule;
import de.raindancer.modules.dnd.rules.DamageRule;
import de.raindancer.modules.dnd.rules.FeatureRule;
import de.raindancer.modules.dnd.rules.MobRule;
import de.raindancer.modules.dnd.util.Living;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Performs a spell. Everything here runs on the caster's thread; a creature in another region (Folia) is touched on
 * its own.
 *
 * <p>A spell that finds nothing to aim at answers empty and costs nothing — {@code CastingService} spends the slot
 * only for a spell that went off.
 */
public final class SpellService implements IDndService {

    static final NamespacedKey HELD_JUMP = new NamespacedKey(BodyService.NAMESPACE, "held");

    /**
     * What a cast carries beyond the hero's own numbers.
     *
     * @param dc        a scroll's save DC instead of the caster's, or null
     * @param attack    a scroll's attack bonus instead of the caster's, or null
     * @param advantage Tides of Chaos: the attack roll is made twice and the better kept
     * @param maxDice   Overchannel: the damage dice roll their highest
     */
    public record Extras(Integer dc, Integer attack, boolean advantage, boolean maxDice) {

        public static final Extras NONE = new Extras(null, null, false, false);

        public static Extras fromItem(int dc, int attack) {
            return new Extras(dc, attack, false, false);
        }
    }

    /** A spell about to be cast: who, what, from which slot, bent how. */
    public record Cast(Player caster, Hero hero, Spell spell, int slotLevel, Metamagic metamagic, Extras extras) {

        public Cast(Player caster, Hero hero, Spell spell, int slotLevel, Metamagic metamagic) {
            this(caster, hero, spell, slotLevel, metamagic, Extras.NONE);
        }

        public Cast {
            extras = extras == null ? Extras.NONE : extras;
        }

        boolean bent(Metamagic which) {
            return metamagic == which;
        }

        int seconds() {
            return spell.seconds() * (metamagic == Metamagic.EXTENDED ? 2 : 1);
        }
    }

    /** What came of it, for the line the caster reads. */
    public record Outcome(int hit, int saved, int missed, double damage, double healed) {

        static Outcome nothing() {
            return new Outcome(0, 0, 0, 0, 0);
        }

        Outcome plus(Outcome other) {
            return new Outcome(hit + other.hit, saved + other.saved, missed + other.missed, damage + other.damage,
                    healed + other.healed);
        }
    }

    private final Plugin plugin;
    private final Messages messages;
    private final TargetService targets;
    private final SpellCues cues;
    private final CheckRule checks;
    private final DamageRule damage;
    private final MobRule mobs;
    private final FeatureRule features;
    private final HeroService heroes;
    private final ConditionService conditions;
    private final BodyService body;
    private final ConcentrationService concentration;
    private final RandomGenerator random;

    private volatile DndSettings settings = DndSettings.DEFAULTS;
    private volatile java.util.function.ToIntFunction<Player> saveBonusFromItems = player -> 0;

    public SpellService(Plugin plugin, Messages messages, TargetService targets, SpellCues cues, CheckRule checks,
                        DamageRule damage, MobRule mobs, FeatureRule features, HeroService heroes,
                        ConditionService conditions, BodyService body, ConcentrationService concentration,
                        RandomGenerator random) {
        this.plugin = plugin;
        this.messages = messages;
        this.targets = targets;
        this.cues = cues;
        this.checks = checks;
        this.damage = damage;
        this.mobs = mobs;
        this.features = features;
        this.heroes = heroes;
        this.conditions = conditions;
        this.body = body;
        this.concentration = concentration;
        this.random = random;
        concentration.onPulse(this::pulse);
    }

    /** What a player's magic items add to their saving throws — a Ring of Protection. */
    public void saveBonusFromItems(java.util.function.ToIntFunction<Player> lookup) {
        this.saveBonusFromItems = lookup == null ? player -> 0 : lookup;
    }

    @Override
    public void settings(DndSettings updated) {
        this.settings = updated == null ? DndSettings.DEFAULTS : updated;
    }

    /** Casts it. Empty when there was nothing to aim at, and then nothing was spent. */
    public Optional<Outcome> perform(Cast cast) {
        return switch (cast.spell().shape()) {
            case BOLT -> bolts(cast);
            case TOUCH -> touch(cast);
            case MISSILES -> missiles(cast);
            case TARGET_SAVE -> targetSave(cast);
            case CONE -> area(cast, targets.inCone(cast.caster(), cast.spell().range(), 35,
                    foes(cast)), cast.caster().getEyeLocation(), false);
            case BURST_SELF -> area(cast, targets.around(cast.caster().getLocation(), cast.spell().radius(),
                    foes(cast)), cast.caster().getLocation(), true);
            case BURST_AT -> burstAt(cast);
            case LINE -> line(cast);
            case CHAIN -> chain(cast);
            case HEAL -> heal(cast);
            case HEAL_GROUP -> healGroup(cast);
            case BUFF_SELF -> buffSelf(cast);
            case BUFF_ONE -> buffOne(cast);
            case BUFF_GROUP -> buffGroup(cast);
            case CLEANSE -> cleanse(cast);
            case TELEPORT -> teleport(cast);
            case AURA -> aura(cast);
        };
    }

    // ------------------------------------------------------------------ who

    private Predicate<LivingEntity> foes(Cast cast) {
        Predicate<LivingEntity> foes = targets.foes(cast.caster());
        boolean sculpted = Feature.SCULPT_SPELLS.has(cast.hero()) && cast.spell().save().isPresent();
        if (cast.bent(Metamagic.CAREFUL) || sculpted) {
            return foes.and(entity -> !(entity instanceof Player));
        }
        return foes;
    }

    private Optional<LivingEntity> aimedFoe(Cast cast, double range) {
        return targets.lookedAt(cast.caster(), range, targets.foes(cast.caster()));
    }

    /** The player looked at within range, or the caster. */
    private Player aimedFriend(Cast cast, double range) {
        return targets.lookedAt(cast.caster(), range, targets.friends())
                .filter(Player.class::isInstance).map(Player.class::cast).orElse(cast.caster());
    }

    /** Twinned Spell's second target: the nearest other one in reach. */
    private Optional<LivingEntity> twin(Cast cast, LivingEntity first, Predicate<LivingEntity> which) {
        if (!cast.bent(Metamagic.TWINNED)) {
            return Optional.empty();
        }
        double reach = Math.max(9, cast.spell().range());
        for (LivingEntity candidate : targets.around(cast.caster().getLocation(), reach, which)) {
            if (!candidate.equals(first) && cast.caster().hasLineOfSight(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ the shapes

    private Optional<Outcome> bolts(Cast cast) {
        Optional<LivingEntity> aimed = aimedFoe(cast, cast.spell().range());
        if (aimed.isEmpty()) {
            return nothingThere(cast);
        }
        List<LivingEntity> hitList = new ArrayList<>();
        int rays = damage.count(cast.spell(), cast.slotLevel());
        for (int ray = 0; ray < rays; ray++) {
            hitList.add(aimed.get());
        }
        twin(cast, aimed.get(), foes(cast)).ifPresent(hitList::add);
        Outcome outcome = Outcome.nothing();
        for (LivingEntity target : hitList) {
            cues.ray(cast.caster().getEyeLocation(), TargetService.middleOf(target), cast.spell().kind());
            outcome = outcome.plus(attack(cast, target, hitList.size() <= 2));
        }
        return Optional.of(outcome);
    }

    private Optional<Outcome> touch(Cast cast) {
        Optional<LivingEntity> aimed = aimedFoe(cast, cast.spell().range());
        if (aimed.isEmpty()) {
            return nothingThere(cast);
        }
        Outcome outcome = attack(cast, aimed.get(), true);
        Optional<LivingEntity> second = twin(cast, aimed.get(), foes(cast));
        if (second.isPresent()) {
            outcome = outcome.plus(attack(cast, second.get(), true));
        }
        return Optional.of(outcome);
    }

    /** An attack roll against the target's armour class, and the damage when it hits. */
    private Outcome attack(Cast cast, LivingEntity target, boolean tell) {
        int bonus = cast.extras().attack() != null ? cast.extras().attack() : checks.spellAttackBonus(cast.hero());
        int armourClass = armourClassOf(target);
        int die = cast.extras().advantage() ? checks.d20WithAdvantage(random) : checks.d20(random, false);
        Roll roll = checks.attack(die, bonus, armourClass);
        if (tell && settings.tellsRolls()) {
            String key = roll.critical() ? "dnd.roll.attack-critical" : roll.fumble() ? "dnd.roll.attack-fumble"
                    : roll.succeeded() ? "dnd.roll.attack-hit" : "dnd.roll.attack-miss";
            messages.send(cast.caster(), key, "die", String.valueOf(roll.die()), "bonus", signed(roll.bonus()),
                    "total", String.valueOf(roll.total()), "ac", String.valueOf(armourClass));
        }
        if (!roll.succeeded()) {
            if (cast.spell().isCantrip() && Feature.POTENT_CANTRIP.has(cast.hero())) {
                int half = damage.roll(damage.dice(cast.spell(), cast.slotLevel(), cast.hero().level()),
                        rolling(cast, false), random) / 2;
                return new Outcome(0, 0, 1, hurt(cast, target, half, false), 0);
            }
            return new Outcome(0, 0, 1, 0, 0);
        }
        int rolled = damage.roll(damage.dice(cast.spell(), cast.slotLevel(), cast.hero().level()),
                rolling(cast, roll.critical()), random) + extraDamage(cast);
        double dealt = hurt(cast, target, rolled, false);
        sideEffects(cast, target);
        return new Outcome(1, 0, 0, dealt, 0);
    }

    private Optional<Outcome> missiles(Cast cast) {
        List<LivingEntity> aimedAt = new ArrayList<>();
        aimedFoe(cast, cast.spell().range()).ifPresent(aimedAt::add);
        if (aimedAt.isEmpty()) {
            for (LivingEntity entity : targets.inCone(cast.caster(), cast.spell().range(), 30, foes(cast))) {
                if (cast.caster().hasLineOfSight(entity) && aimedAt.size() < 3) {
                    aimedAt.add(entity);
                }
            }
        }
        if (aimedAt.isEmpty()) {
            return nothingThere(cast);
        }
        int darts = damage.count(cast.spell(), cast.slotLevel());
        Outcome outcome = Outcome.nothing();
        for (int dart = 0; dart < darts; dart++) {
            LivingEntity target = aimedAt.get(dart % aimedAt.size());
            cues.ray(cast.caster().getEyeLocation(), TargetService.middleOf(target), cast.spell().kind());
            int rolled = damage.roll(List.of(cast.spell().damage()), rolling(cast, false), random)
                    + (dart == 0 ? extraDamage(cast) : 0);
            outcome = outcome.plus(new Outcome(1, 0, 0, hurt(cast, target, rolled, false), 0));
        }
        return Optional.of(outcome);
    }

    private Optional<Outcome> targetSave(Cast cast) {
        Spell spell = cast.spell();
        Predicate<LivingEntity> which = foes(cast);
        if (spell == Spell.HOLD_PERSON) {
            which = which.and(entity -> mobs.of(entity.getType()).humanoid());
        }
        Optional<LivingEntity> aimed = targets.lookedAt(cast.caster(), spell.range(), which);
        if (aimed.isEmpty()) {
            return nothingThere(cast);
        }
        List<LivingEntity> hitList = new ArrayList<>(List.of(aimed.get()));
        twin(cast, aimed.get(), which).ifPresent(hitList::add);
        Outcome outcome = Outcome.nothing();
        List<LivingEntity> held = new ArrayList<>();
        for (LivingEntity target : hitList) {
            cues.ray(cast.caster().getEyeLocation(), TargetService.middleOf(target), spell.kind());
            if (spell == Spell.POWER_WORD_KILL) {
                if (target.getHealth() + target.getAbsorptionAmount() <= spell.damage().bonus()) {
                    double dealt = hurtExactly(cast, target, target.getHealth() + target.getAbsorptionAmount() + 1);
                    outcome = outcome.plus(new Outcome(1, 0, 0, dealt, 0));
                } else {
                    messages.send(cast.caster(), "dnd.cast.too-strong", "spell", spell.title());
                    outcome = outcome.plus(new Outcome(0, 1, 0, 0, 0));
                }
                continue;
            }
            Roll save = save(cast, target, spell.save().orElse(Ability.WISDOM), target.equals(aimed.get()), true);
            if (spell == Spell.HOLD_PERSON) {
                if (!save.succeeded()) {
                    held.add(target);
                    outcome = outcome.plus(new Outcome(1, 0, 0, 0, 0));
                } else {
                    outcome = outcome.plus(new Outcome(0, 1, 0, 0, 0));
                }
                continue;
            }
            int rolled = damage.roll(damage.dice(spell, cast.slotLevel(), cast.hero().level()), rolling(cast, false),
                    random) + extraDamage(cast);
            int left = damage.afterSave(rolled, spell, save.succeeded(), evades(target));
            double dealt = hurt(cast, target, left, spell == Spell.HARM);
            outcome = outcome.plus(save.succeeded() ? new Outcome(0, 1, 0, dealt, 0) : new Outcome(1, 0, 0, dealt, 0));
        }
        if (!held.isEmpty()) {
            hold(cast, held);
        }
        return Optional.of(outcome);
    }

    private void hold(Cast cast, List<LivingEntity> held) {
        int seconds = cast.seconds();
        List<UUID> ids = new ArrayList<>();
        for (LivingEntity target : held) {
            ids.add(target.getUniqueId());
            onTarget(target, () -> {
                give(target, PotionEffectType.SLOWNESS, seconds, 6);
                give(target, PotionEffectType.WEAKNESS, seconds, 4);
                give(target, PotionEffectType.MINING_FATIGUE, seconds, 3);
                body.lend(target, Attribute.JUMP_STRENGTH, HELD_JUMP, -1.0);
                if (target instanceof Player player) {
                    conditions.set(player.getUniqueId(), Condition.HELD, seconds * 1000L);
                }
                if (target instanceof Mob mob) {
                    mob.setTarget(null);
                }
            });
        }
        concentration.start(cast.caster(), cast.spell(), cast.slotLevel(), seconds * 1000L, () -> {
            for (LivingEntity target : held) {
                onTarget(target, () -> {
                    takeBack(target, PotionEffectType.SLOWNESS, 6);
                    takeBack(target, PotionEffectType.WEAKNESS, 4);
                    takeBack(target, PotionEffectType.MINING_FATIGUE, 3);
                    body.lend(target, Attribute.JUMP_STRENGTH, HELD_JUMP, 0);
                    conditions.clear(target.getUniqueId(), Condition.HELD);
                });
            }
        });
    }

    private Optional<Outcome> burstAt(Cast cast) {
        Spell spell = cast.spell();
        Location centre = aimedFoe(cast, spell.range()).map(LivingEntity::getLocation)
                .orElseGet(() -> targets.pointLookedAt(cast.caster(), spell.range()));
        List<Location> centres = new ArrayList<>(List.of(centre));
        for (int extra = 1; extra < spell.count(); extra++) {
            double angle = Math.PI * 2 * extra / (spell.count() - 1);
            centres.add(centre.clone().add(Math.cos(angle) * spell.radius() * 1.2, 0,
                    Math.sin(angle) * spell.radius() * 1.2));
        }
        Map<UUID, LivingEntity> caught = new LinkedHashMap<>();
        for (Location at : centres) {
            cues.ring(at, spell.radius(), spell.kind());
            for (LivingEntity entity : targets.around(at, spell.radius(), foes(cast))) {
                caught.putIfAbsent(entity.getUniqueId(), entity);
            }
        }
        cues.ray(cast.caster().getEyeLocation(), centre, spell.kind());
        return Optional.of(saveAll(cast, List.copyOf(caught.values()), centre));
    }

    private Optional<Outcome> area(Cast cast, List<LivingEntity> caught, Location from, boolean ring) {
        if (ring) {
            cues.ring(from, cast.spell().radius(), cast.spell().kind());
        } else {
            Location end = targets.pointLookedAt(cast.caster(), cast.spell().range());
            cues.ray(from, end, cast.spell().kind());
            for (LivingEntity entity : caught) {
                cues.at(TargetService.middleOf(entity), cast.spell().kind());
            }
        }
        return Optional.of(saveAll(cast, caught, from));
    }

    private Optional<Outcome> line(Cast cast) {
        List<LivingEntity> caught = targets.alongLine(cast.caster(), cast.spell().range(), cast.spell().radius(),
                foes(cast));
        cues.ray(cast.caster().getEyeLocation(), targets.pointLookedAt(cast.caster(), cast.spell().range()),
                cast.spell().kind());
        return Optional.of(saveAll(cast, caught, cast.caster().getLocation()));
    }

    private Optional<Outcome> chain(Cast cast) {
        Optional<LivingEntity> first = aimedFoe(cast, cast.spell().range());
        if (first.isEmpty()) {
            return nothingThere(cast);
        }
        List<LivingEntity> chain = new ArrayList<>(List.of(first.get()));
        int jumps = damage.count(cast.spell(), cast.slotLevel());
        LivingEntity last = first.get();
        cues.ray(cast.caster().getEyeLocation(), TargetService.middleOf(last), cast.spell().kind());
        while (chain.size() < jumps) {
            LivingEntity from = last;
            Optional<LivingEntity> next = targets.around(from.getLocation(), cast.spell().radius(), foes(cast))
                    .stream().filter(entity -> !chain.contains(entity) && from.hasLineOfSight(entity)).findFirst();
            if (next.isEmpty()) {
                break;
            }
            cues.ray(TargetService.middleOf(from), TargetService.middleOf(next.get()), cast.spell().kind());
            chain.add(next.get());
            last = next.get();
        }
        return Optional.of(saveAll(cast, chain, cast.caster().getLocation()));
    }

    /** Everybody caught rolls their own save; the dice are rolled once for all, as at the table. */
    private Outcome saveAll(Cast cast, List<LivingEntity> caught, Location from) {
        Spell spell = cast.spell();
        int rolled = damage.roll(damage.dice(spell, cast.slotLevel(), cast.hero().level()), rolling(cast, false),
                random) + extraDamage(cast);
        Outcome outcome = Outcome.nothing();
        boolean tell = caught.size() <= 3;
        for (int index = 0; index < caught.size(); index++) {
            LivingEntity target = caught.get(index);
            Roll save = save(cast, target, spell.save().orElse(Ability.DEXTERITY), index == 0, tell);
            int left = damage.afterSave(rolled, spell, save.succeeded(), evades(target));
            double dealt = hurt(cast, target, left, false);
            if (!save.succeeded()) {
                sideEffects(cast, target);
                if (spell == Spell.THUNDERWAVE) {
                    Vector away = target.getLocation().toVector().subtract(from.toVector()).setY(0);
                    if (away.lengthSquared() > 0.01) {
                        Vector push = away.normalize().multiply(1.4).setY(0.4);
                        onTarget(target, () -> target.setVelocity(push));
                    }
                }
            }
            outcome = outcome.plus(save.succeeded() ? new Outcome(0, 1, 0, dealt, 0) : new Outcome(1, 0, 0, dealt, 0));
        }
        return outcome;
    }

    private Optional<Outcome> heal(Cast cast) {
        Spell spell = cast.spell();
        Player first = aimedFriend(cast, spell.range());
        List<LivingEntity> healed = new ArrayList<>(List.of(first));
        twin(cast, first, targets.friends()).ifPresent(healed::add);
        double total = 0;
        for (LivingEntity target : healed) {
            total += heal(cast, target, healing(cast));
            blessedHealer(cast, target);
            if (spell == Spell.HEAL) {
                onTarget(target, () -> cleanse(target, true));
            }
            if (spell == Spell.REGENERATE) {
                onTarget(target, () -> give(target, PotionEffectType.REGENERATION, cast.seconds(), 1));
            }
        }
        return Optional.of(new Outcome(healed.size(), 0, 0, 0, total));
    }

    private Optional<Outcome> healGroup(Cast cast) {
        Spell spell = cast.spell();
        List<Player> allies = targets.allies(cast.caster(), spell.radius());
        if (allies.size() > spell.count()) {
            allies = allies.subList(0, spell.count());
        }
        double total = 0;
        if (spell == Spell.MASS_HEAL) {
            double pool = spell.damage().bonus();
            for (Player ally : allies) {
                double missing = TargetService.maxHealth(ally) - ally.getHealth();
                double given = Math.min(pool, Math.max(0, missing));
                pool -= given;
                total += heal(cast, ally, given);
                onTarget(ally, () -> cleanse(ally, true));
            }
        } else {
            int each = healing(cast);
            for (Player ally : allies) {
                total += heal(cast, ally, each);
            }
            if (allies.size() > 1) {
                blessedHealer(cast, allies.get(1));
            }
        }
        cues.ring(cast.caster().getLocation(), Math.min(spell.radius(), 6), DamageKind.HEALING);
        return Optional.of(new Outcome(allies.size(), 0, 0, 0, total));
    }

    private int healing(Cast cast) {
        boolean disciple = Feature.DISCIPLE_OF_LIFE.has(cast.hero());
        boolean supreme = Feature.SUPREME_HEALING.has(cast.hero());
        return damage.healing(cast.spell(), cast.slotLevel(), checks.castingModifier(cast.hero()), disciple, supreme,
                random);
    }

    /** Blessed Healer: healing somebody else heals the cleric too. */
    private void blessedHealer(Cast cast, LivingEntity healed) {
        if (!healed.equals(cast.caster()) && Feature.BLESSED_HEALER.has(cast.hero()) && cast.slotLevel() >= 1) {
            heal(cast, cast.caster(), 2 + cast.slotLevel());
        }
    }

    /** How the damage dice are rolled for this cast. */
    private DamageRule.Rolling rolling(Cast cast, boolean critical) {
        boolean overchannel = cast.extras().maxDice() && cast.spell().level() >= 1 && cast.spell().level() <= 5;
        return DamageRule.Rolling.PLAIN.critical(critical).empowered(cast.bent(Metamagic.EMPOWERED))
                .bombarding(Feature.SPELL_BOMBARDMENT.has(cast.hero())).maxed(overchannel);
    }

    /** Empowered Evocation and Elemental Affinity: an ability modifier on top of the dice. */
    private int extraDamage(Cast cast) {
        int extra = 0;
        if (Feature.EMPOWERED_EVOCATION.has(cast.hero())) {
            extra += Math.max(0, cast.hero().modifier(Ability.INTELLIGENCE));
        }
        if (Feature.ELEMENTAL_AFFINITY.has(cast.hero()) && cast.spell().kind() == DamageKind.FIRE) {
            extra += Math.max(0, cast.hero().modifier(Ability.CHARISMA));
        }
        return extra;
    }

    private double heal(Cast cast, LivingEntity target, double amount) {
        double missing = Math.max(0, TargetService.maxHealth(target) - target.getHealth());
        double given = Math.min(missing, Math.max(0, amount));
        onTarget(target, () -> {
            target.setHealth(Math.min(TargetService.maxHealth(target), target.getHealth() + given));
            cues.at(TargetService.middleOf(target), DamageKind.HEALING);
        });
        return given;
    }

    private Optional<Outcome> buffSelf(Cast cast) {
        Player caster = cast.caster();
        int seconds = cast.seconds();
        switch (cast.spell()) {
            case SHIELD -> conditions.set(caster.getUniqueId(), Condition.SHIELDED, seconds * 1000L);
            case MAGE_ARMOR -> body.lend(caster, Attribute.ARMOR, BodyService.MAGE_ARMOR, 3.0);
            case EXPEDITIOUS_RETREAT -> {
                give(caster, PotionEffectType.SPEED, seconds, 2);
                concentration.start(caster, cast.spell(), cast.slotLevel(), seconds * 1000L,
                        () -> takeBack(caster, PotionEffectType.SPEED, 2));
            }
            case FEATHER_FALL -> {
                for (Player ally : targets.allies(caster, cast.spell().radius())) {
                    onTarget(ally, () -> give(ally, PotionEffectType.SLOW_FALLING, seconds, 0));
                }
            }
            case JUMP -> give(caster, PotionEffectType.JUMP_BOOST, seconds, 2);
            case INVISIBILITY, GREATER_INVISIBILITY -> {
                give(caster, PotionEffectType.INVISIBILITY, seconds, 0);
                if (cast.spell() == Spell.INVISIBILITY) {
                    conditions.set(caster.getUniqueId(), Condition.HIDDEN, seconds * 1000L);
                }
                concentration.start(caster, cast.spell(), cast.slotLevel(), seconds * 1000L, () -> {
                    takeBack(caster, PotionEffectType.INVISIBILITY, 0);
                    conditions.clear(caster.getUniqueId(), Condition.HIDDEN);
                });
            }
            default -> {
            }
        }
        cues.at(TargetService.middleOf(caster), DamageKind.NONE);
        return Optional.of(new Outcome(1, 0, 0, 0, 0));
    }

    private Optional<Outcome> buffOne(Cast cast) {
        Player first = aimedFriend(cast, cast.spell().range());
        List<Player> chosen = new ArrayList<>(List.of(first));
        twin(cast, first, targets.friends()).filter(Player.class::isInstance).map(Player.class::cast)
                .ifPresent(chosen::add);
        int seconds = cast.seconds();
        switch (cast.spell()) {
            case SHIELD_OF_FAITH -> {
                chosen.forEach(target -> onTarget(target, () -> give(target, PotionEffectType.RESISTANCE, seconds, 0)));
                concentration.start(cast.caster(), cast.spell(), cast.slotLevel(), seconds * 1000L, () ->
                        chosen.forEach(target -> onTarget(target,
                                () -> takeBack(target, PotionEffectType.RESISTANCE, 0))));
            }
            case HASTE -> {
                chosen.forEach(target -> onTarget(target, () -> {
                    give(target, PotionEffectType.SPEED, seconds, 1);
                    give(target, PotionEffectType.HASTE, seconds, 1);
                    body.lend(target, Attribute.ARMOR, BodyService.HASTE, 2.0);
                }));
                concentration.start(cast.caster(), cast.spell(), cast.slotLevel(), seconds * 1000L, () ->
                        chosen.forEach(target -> onTarget(target, () -> {
                            takeBack(target, PotionEffectType.SPEED, 1);
                            takeBack(target, PotionEffectType.HASTE, 1);
                            body.lend(target, Attribute.ARMOR, BodyService.HASTE, 0);
                            give(target, PotionEffectType.SLOWNESS, 6, 2);
                            messages.send(target, "dnd.cast.lethargy");
                        })));
            }
            case DEATH_WARD -> chosen.forEach(target ->
                    conditions.set(target.getUniqueId(), Condition.WARDED, seconds * 1000L));
            default -> {
            }
        }
        chosen.forEach(target -> cues.at(TargetService.middleOf(target), DamageKind.RADIANT));
        return Optional.of(new Outcome(chosen.size(), 0, 0, 0, 0));
    }

    private Optional<Outcome> buffGroup(Cast cast) {
        List<Player> allies = targets.allies(cast.caster(), cast.spell().radius());
        int limit = cast.spell().count() + Math.max(0, cast.slotLevel() - cast.spell().level())
                * (cast.spell() == Spell.BLESS ? 1 : 0);
        if (allies.size() > limit) {
            allies = List.copyOf(allies.subList(0, limit));
        }
        List<Player> chosen = allies;
        int seconds = cast.seconds();
        switch (cast.spell()) {
            case BLESS -> {
                chosen.forEach(target -> onTarget(target, () -> give(target, PotionEffectType.STRENGTH, seconds, 0)));
                concentration.start(cast.caster(), cast.spell(), cast.slotLevel(), seconds * 1000L, () ->
                        chosen.forEach(target -> onTarget(target,
                                () -> takeBack(target, PotionEffectType.STRENGTH, 0))));
            }
            case HOLY_AURA -> {
                chosen.forEach(target -> onTarget(target, () -> {
                    give(target, PotionEffectType.RESISTANCE, seconds, 1);
                    give(target, PotionEffectType.GLOWING, seconds, 0);
                }));
                concentration.start(cast.caster(), cast.spell(), cast.slotLevel(), seconds * 1000L, () ->
                        chosen.forEach(target -> onTarget(target, () -> {
                            takeBack(target, PotionEffectType.RESISTANCE, 1);
                            takeBack(target, PotionEffectType.GLOWING, 0);
                        })));
            }
            case AID -> {
                double more = cast.spell().damage().bonus()
                        + cast.spell().upcast().bonus() * Math.max(0, cast.slotLevel() - cast.spell().level());
                chosen.forEach(target -> onTarget(target, () -> {
                    body.lend(target, Attribute.MAX_HEALTH, BodyService.AID, more);
                    target.setHealth(Math.min(TargetService.maxHealth(target), target.getHealth() + more));
                }));
            }
            default -> {
            }
        }
        cues.ring(cast.caster().getLocation(), Math.min(cast.spell().radius(), 5), DamageKind.RADIANT);
        return Optional.of(new Outcome(chosen.size(), 0, 0, 0, 0));
    }

    private Optional<Outcome> cleanse(Cast cast) {
        Player target = aimedFriend(cast, cast.spell().range());
        List<Player> chosen = new ArrayList<>(List.of(target));
        twin(cast, target, targets.friends()).filter(Player.class::isInstance).map(Player.class::cast)
                .ifPresent(chosen::add);
        boolean greater = cast.spell() == Spell.GREATER_RESTORATION;
        chosen.forEach(one -> onTarget(one, () -> {
            cleanse(one, greater);
            cues.at(TargetService.middleOf(one), DamageKind.RADIANT);
        }));
        return Optional.of(new Outcome(chosen.size(), 0, 0, 0, 0));
    }

    private void cleanse(LivingEntity target, boolean greater) {
        List<PotionEffectType> harmful = new ArrayList<>(List.of(PotionEffectType.POISON, PotionEffectType.BLINDNESS,
                PotionEffectType.NAUSEA, PotionEffectType.DARKNESS));
        if (greater) {
            harmful.addAll(List.of(PotionEffectType.WITHER, PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS,
                    PotionEffectType.MINING_FATIGUE, PotionEffectType.HUNGER, PotionEffectType.LEVITATION));
            body.lend(target, Attribute.JUMP_STRENGTH, HELD_JUMP, 0);
            conditions.clear(target.getUniqueId(), Condition.HELD);
        }
        harmful.forEach(target::removePotionEffect);
    }

    private Optional<Outcome> teleport(Cast cast) {
        Player caster = cast.caster();
        Location eye = caster.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        Location aim = targets.pointLookedAt(caster, cast.spell().range());
        double distance = eye.distance(aim);
        Location landing = null;
        for (double back = 0; back <= distance; back += 0.5) {
            Location feet = aim.clone().subtract(look.clone().multiply(back));
            feet.setY(Math.floor(feet.getY()));
            Location found = standable(feet);
            if (found != null) {
                landing = found;
                break;
            }
        }
        if (landing == null || landing.distanceSquared(caster.getLocation()) < 4) {
            messages.send(caster, "dnd.cast.no-room");
            return Optional.empty();
        }
        landing.setYaw(caster.getLocation().getYaw());
        landing.setPitch(caster.getLocation().getPitch());
        cues.at(TargetService.middleOf(caster), DamageKind.FORCE);
        Location to = landing;
        caster.teleportAsync(to).thenAccept(done -> Scheduling.entity(plugin, caster,
                () -> cues.at(TargetService.middleOf(caster), DamageKind.FORCE)));
        caster.setFallDistance(0);
        return Optional.of(new Outcome(1, 0, 0, 0, 0));
    }

    private static Location standable(Location around) {
        return TargetService.standable(around);
    }

    /**
     * A blast of this spell at a point, with the caster caught in it too — a wild surge's fireball.
     */
    public Outcome explodeAt(Cast cast, Location centre, boolean casterToo) {
        List<LivingEntity> caught = new ArrayList<>(targets.around(centre, cast.spell().radius(), foes(cast)));
        if (casterToo && !caught.contains(cast.caster())) {
            caught.addFirst(cast.caster());
        }
        cues.ring(centre, cast.spell().radius(), cast.spell().kind());
        return saveAll(cast, caught, centre);
    }

    private Optional<Outcome> aura(Cast cast) {
        concentration.start(cast.caster(), cast.spell(), cast.slotLevel(), cast.seconds() * 1000L, () -> { });
        cues.ring(cast.caster().getLocation(), cast.spell().radius(), cast.spell().kind());
        return Optional.of(new Outcome(1, 0, 0, 0, 0));
    }

    /** Spirit Guardians: every hostile creature in the ring saves or is hurt and slowed. */
    private void pulse(Player caster, ConcentrationService.Focus focus) {
        if (focus.spell().shape() != SpellShape.AURA) {
            return;
        }
        Optional<Hero> hero = heroes.of(caster.getUniqueId());
        if (hero.isEmpty()) {
            return;
        }
        Cast cast = new Cast(caster, hero.get(), focus.spell(), focus.slotLevel(), null);
        List<LivingEntity> caught = targets.around(caster.getLocation(), focus.spell().radius(),
                targets.foes(caster).and(entity -> entity instanceof Enemy));
        cues.ring(caster.getLocation(), focus.spell().radius(), focus.spell().kind());
        for (LivingEntity target : caught) {
            onTarget(target, () -> give(target, PotionEffectType.SLOWNESS, 3, 1));
        }
        saveAll(cast, caught, caster.getLocation());
    }

    // ------------------------------------------------------------------ the parts

    private Optional<Outcome> nothingThere(Cast cast) {
        messages.send(cast.caster(), "dnd.cast.no-target", "spell", cast.spell().title());
        return Optional.empty();
    }

    /** A saving throw by the target against the caster's DC. */
    private Roll save(Cast cast, LivingEntity target, Ability ability, boolean first, boolean tell) {
        boolean heightened = first && cast.bent(Metamagic.HEIGHTENED);
        int dc = cast.extras().dc() != null ? cast.extras().dc() : checks.spellDc(cast.hero());
        Optional<Hero> theirs = target instanceof Player player ? heroes.of(player.getUniqueId()) : Optional.empty();
        int bonus = theirs.map(hero -> checks.saveBonus(hero, ability))
                .orElseGet(() -> mobs.of(target.getType()).saveBonus());
        if (target instanceof Player player) {
            bonus += saveBonusFromItems.applyAsInt(player);
        }
        if (conditions.has(cast.caster().getUniqueId(), Condition.BENT_LUCK)) {
            bonus -= Dice.of(1, 4).roll(random);
        }
        Roll roll = checks.save(checks.d20(random, heightened), bonus, dc);
        if (!roll.succeeded() && theirs.isPresent() && target instanceof Player player
                && features.mayUse(theirs.get(), Feature.INDOMITABLE)) {
            roll = checks.save(checks.d20(random, false), bonus, dc);
            onTarget(player, () -> heroes.change(player, hero -> features.spend(hero, Feature.INDOMITABLE)));
            messages.send(player, "dnd.indomitable");
        }
        if (tell && settings.tellsRolls()) {
            messages.send(cast.caster(), roll.succeeded() ? "dnd.roll.save-made" : "dnd.roll.save-failed",
                    "name", nameOf(target), "ability", ability.shortName(), "die", String.valueOf(roll.die()),
                    "bonus", signed(roll.bonus()), "total", String.valueOf(roll.total()), "dc", String.valueOf(dc));
        }
        return roll;
    }

    private boolean evades(LivingEntity target) {
        if (!(target instanceof Player player)) {
            return false;
        }
        return heroes.of(player.getUniqueId()).filter(hero -> Feature.EVASION.has(hero))
                .isPresent();
    }

    private int armourClassOf(LivingEntity target) {
        if (target instanceof Player player) {
            int dexterity = heroes.of(player.getUniqueId()).map(hero -> hero.modifier(Ability.DEXTERITY)).orElse(0);
            var armour = player.getAttribute(Attribute.ARMOR);
            return checks.playerArmourClass(armour == null ? 0 : (int) Math.round(armour.getValue()), dexterity);
        }
        MobProfile profile = mobs.of(target.getType());
        var armour = target.getAttribute(Attribute.ARMOR);
        int worn = armour == null ? 0 : (int) Math.round(armour.getValue() * 0.4);
        return profile.armourClass() + worn;
    }

    /** Spell damage, scaled by the settings, through the server's own damage event so protections still apply. */
    private double hurt(Cast cast, LivingEntity target, int rolled, boolean neverBelowOne) {
        if (rolled <= 0) {
            return 0;
        }
        double amount = damage.scaled(rolled, settings.spellDamagePercent());
        if (target instanceof Player) {
            int percent = settings.playerDamagePercent();
            amount = percent <= 0 ? 0 : Math.max(0.5, amount * percent / 100.0);
        }
        if (neverBelowOne) {
            amount = Math.min(amount, Math.max(0, target.getHealth() - 1));
        }
        if (amount <= 0) {
            return 0;
        }
        return hurtExactly(cast, target, amount);
    }

    private double hurtExactly(Cast cast, LivingEntity target, double amount) {
        DamageSource source = DamageSource.builder(DamageType.MAGIC).withCausingEntity(cast.caster())
                .withDirectEntity(cast.caster()).build();
        onTarget(target, () -> {
            target.setNoDamageTicks(0);
            target.damage(amount, source);
        });
        return amount;
    }

    /** What a hit leaves behind beside the damage: flames, frost, light. */
    private void sideEffects(Cast cast, LivingEntity target) {
        Spell spell = cast.spell();
        onTarget(target, () -> {
            if (target.isDead()) {
                return;
            }
            switch (spell.kind()) {
                case FIRE -> target.setFireTicks(Math.max(target.getFireTicks(), 60));
                case COLD -> give(target, PotionEffectType.SLOWNESS, Math.max(2, spell.seconds()), 1);
                default -> {
                }
            }
            if (spell == Spell.GUIDING_BOLT) {
                give(target, PotionEffectType.GLOWING, spell.seconds(), 0);
            }
            if (spell == Spell.SUNBURST) {
                give(target, PotionEffectType.BLINDNESS, spell.seconds(), 0);
            }
            if (spell == Spell.SHOCKING_GRASP) {
                Vector away = target.getLocation().toVector().subtract(cast.caster().getLocation().toVector())
                        .setY(0);
                if (away.lengthSquared() > 0.01) {
                    target.setVelocity(away.normalize().multiply(0.8).setY(0.3));
                }
            }
        });
    }

    private void onTarget(LivingEntity target, Runnable action) {
        Living.onTarget(plugin, target, action);
    }

    private static void give(LivingEntity target, PotionEffectType type, int seconds, int amplifier) {
        Living.give(target, type, seconds, amplifier);
    }

    private static void takeBack(LivingEntity target, PotionEffectType type, int amplifier) {
        Living.takeBack(target, type, amplifier);
    }

    private static String nameOf(LivingEntity target) {
        return target instanceof Player player ? player.getName()
                : target.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    /** The dice a hero would roll with this spell from this slot — for the spellbook. */
    public List<Dice> diceOf(Hero hero, Spell spell, int slotLevel) {
        return damage.dice(spell, slotLevel, hero.level());
    }
}
