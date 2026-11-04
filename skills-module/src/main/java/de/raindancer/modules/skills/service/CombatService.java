package de.raindancer.modules.skills.service;

import de.raindancer.core.ui.actionbar.ActionBarPriority;
import de.raindancer.core.ui.actionbar.ActionBars;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.skills.SkillsSettings;
import de.raindancer.modules.skills.model.Hero;
import de.raindancer.modules.skills.model.MagicItem;
import de.raindancer.modules.skills.model.MobProfile;
import de.raindancer.modules.skills.model.Perk;
import de.raindancer.modules.skills.rules.FightRule;
import de.raindancer.modules.skills.rules.MobRule;
import de.raindancer.modules.skills.rules.SkillRule;
import de.raindancer.modules.skills.store.ItemTags;
import de.raindancer.modules.skills.util.Living;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/**
 * The skill tree in a fight: harder blows and arrows, softer hurts, dodging, lifesteal, the off hand of a Dual
 * Wielder, Second Wind and Undying, healing out of a fight, and experience for what a player does — and what the
 * branches add: thorns, a riposte, wards, bleeding, stuns, venom and hexes, burning and pinning arrows, kills in a
 * row, Mana Shield, potions that last, and every mastery feature.
 */
public final class CombatService implements ISkillsService {

    static final long SECOND_WIND_MILLIS = 120_000;
    static final long UNDYING_MILLIS = 600_000;
    /** No blow given or taken for this long: out of a fight, and Mending works. */
    static final long OUT_OF_FIGHT_MILLIS = 8_000;
    /** Ticks between the main hand's blow and the off hand's. */
    private static final long OFF_HAND_DELAY = 4;
    /** A riposte waits this long after the shield stopped a blow. */
    static final long RIPOSTE_MILLIS = 3_000;
    /** Kills further apart than this start a new run. */
    static final long STREAK_MILLIS = 10_000;
    static final long MARK_MILLIS = 10_000;
    static final long DASH_MILLIS = 4_000;
    static final long SMOKE_MILLIS = 60_000;
    /** Bleeding: this many pulses of one damage, a second apart. */
    private static final int BLEED_PULSES = 4;
    private static final double BLOODLUST_HEAL = 4.0;
    private static final de.raindancer.modules.skills.model.Dice SPELLSTRIKE_DICE =
            de.raindancer.modules.skills.model.Dice.of(1, 4).plus(1);

    private final Plugin plugin;
    private final Messages messages;
    private final ActionBars actionBars;
    private final Effects effects;
    private final HeroService heroes;
    private final SkillRule perks;
    private final FightRule fight;
    private final MobRule mobs;
    private final de.raindancer.modules.skills.rules.WorthRule worths = new de.raindancer.modules.skills.rules.WorthRule();
    private final LongSupplier clock;
    private final de.raindancer.core.platform.util.Cooldowns<UUID> secondWind;
    private final de.raindancer.core.platform.util.Cooldowns<UUID> undying;
    private final de.raindancer.core.platform.util.Cooldowns<UUID> dash;
    private final de.raindancer.core.platform.util.Cooldowns<UUID> smoke;
    private final Map<UUID, Long> fighting = new ConcurrentHashMap<>();
    /** Until when each player's next blow is a riposte. */
    private final Map<UUID, Long> riposte = new ConcurrentHashMap<>();
    /** Each player's run of kills: how many, and when the last one was. */
    private final Map<UUID, long[]> streaks = new ConcurrentHashMap<>();
    /** The foe each hunter has marked, and until when. */
    private final Map<UUID, Mark> marks = new ConcurrentHashMap<>();
    /** Players whose off hand is striking right now: that blow does not strike again. */
    private final Set<UUID> offHand = ConcurrentHashMap.newKeySet();
    /** Each player's experience this minute, by kind, for the minute's caps. */
    private final Map<UUID, Map<de.raindancer.core.world.activity.ActivityKind, long[]>> minutes =
            new ConcurrentHashMap<>();
    private volatile SkillsSettings settings = SkillsSettings.DEFAULTS;

    public CombatService(Plugin plugin, Messages messages, ActionBars actionBars, Effects effects, HeroService heroes, SkillRule perks,
                         FightRule fight, MobRule mobs, LongSupplier clock) {
        this.plugin = plugin;
        this.messages = messages;
        this.actionBars = actionBars;
        this.effects = effects;
        this.heroes = heroes;
        this.perks = perks;
        this.fight = fight;
        this.mobs = mobs;
        this.clock = clock;
        this.secondWind = new de.raindancer.core.platform.util.Cooldowns<>(clock);
        this.secondWind.every(java.time.Duration.ofMillis(SECOND_WIND_MILLIS));
        this.undying = new de.raindancer.core.platform.util.Cooldowns<>(clock);
        this.undying.every(java.time.Duration.ofMillis(UNDYING_MILLIS));
        this.dash = new de.raindancer.core.platform.util.Cooldowns<>(clock);
        this.dash.every(java.time.Duration.ofMillis(DASH_MILLIS));
        this.smoke = new de.raindancer.core.platform.util.Cooldowns<>(clock);
        this.smoke.every(java.time.Duration.ofMillis(SMOKE_MILLIS));
    }

    private record Mark(UUID target, long until) {
    }

    @Override
    public void settings(SkillsSettings updated) {
        this.settings = updated == null ? SkillsSettings.DEFAULTS : updated;
    }

    /** The tree's values for a player in an adventurers' world; nothing for anybody else. */
    Map<Perk, Double> tree(Player player) {
        if (!settings.worksIn(player.getWorld().getName())) {
            return Map.of();
        }
        return heroes.of(player.getUniqueId()).map(perks::values).orElse(Map.of());
    }

    private void fought(UUID player) {
        fighting.put(player, clock.getAsLong());
    }

    public void forget(UUID player) {
        secondWind.forget(player);
        undying.forget(player);
        fighting.remove(player);
        offHand.remove(player);
        minutes.remove(player);
        dash.forget(player);
        smoke.forget(player);
        riposte.remove(player);
        streaks.remove(player);
        marks.remove(player);
    }

    private boolean has(Map<Perk, Double> tree, Perk feat) {
        return tree.getOrDefault(feat, 0.0) > 0;
    }

    private boolean rolls(Map<Perk, Double> tree, Perk perk, boolean vsPlayer) {
        double chance = fight.chance(tree, perk, vsPlayer, settings.pvpPercent());
        return chance > 0 && ThreadLocalRandom.current().nextDouble() < chance;
    }

    // ------------------------------------------------------------------ blows given

    /** A blow or an arrow the player landed: what it does now. */
    public double dealt(Player striker, LivingEntity target, FightRule.Kind kind, boolean critical, double damage,
                        double distance) {
        return dealt(striker, target, kind, critical, damage, distance, false);
    }

    /** As the other, for an arrow that may have hit the head. */
    public double dealt(Player striker, LivingEntity target, FightRule.Kind kind, boolean critical, double damage,
                        double distance, boolean headshot) {
        fought(striker.getUniqueId());
        long now = clock.getAsLong();
        Map<Perk, Double> tree = tree(striker);
        ItemStack main = striker.getInventory().getItemInMainHand();
        int bonus = kind == FightRule.Kind.RANGED ? 0 : weaponBonus(main);
        double max = TargetService.maxHealth(striker);
        var from = striker.getLocation().toVector().subtract(target.getLocation().toVector());
        var facing = target.getLocation().getDirection();
        MobProfile profile = target instanceof Player ? MobProfile.HARMLESS : mobs.of(target.getType());
        boolean melee = kind != FightRule.Kind.RANGED;
        Long ripostes = melee ? riposte.remove(striker.getUniqueId()) : null;
        long[] streak = streaks.get(striker.getUniqueId());
        Mark mark = marks.get(striker.getUniqueId());
        var armour = target.getAttribute(Attribute.ARMOR);
        FightRule.Hit hit = new FightRule.Hit(kind, critical, FightRule.behind(facing.getX(), facing.getZ(),
                from.getX(), from.getZ()), profile.undead(), target instanceof Player,
                striker.getInventory().getItemInOffHand().isEmpty(), striker.getHealth() < max / 2, distance, bonus,
                melee ? FightRule.Weapon.of(main.getType().name()) : FightRule.Weapon.NONE, striker.isSneaking(),
                headshot, mark != null && mark.until() > now && mark.target().equals(target.getUniqueId()),
                ripostes != null && ripostes > now, streak == null || now - streak[1] > STREAK_MILLIS ? 0 : (int) streak[0],
                armour == null ? 0 : armour.getValue());
        double force = fight.force(tree, hit, SPELLSTRIKE_DICE.roll(ThreadLocalRandom.current()), settings.pvpPercent());
        if (force > 0) {
            target.getWorld().spawnParticle(Particle.ENCHANTED_HIT, TargetService.middleOf(target), 14, 0.3, 0.4, 0.3,
                    0.2);
        }
        return fight.dealt(tree, hit, damage, settings.pvpPercent()) + force;
    }

    /**
     * A melee blow landed for {@code dealt}: lifesteal, bleeding, a stun, venom, a hex, Executioner, and the off
     * hand's strike for a Dual Wielder.
     */
    public void landed(Player striker, LivingEntity target, double dealt, boolean critical) {
        Map<Perk, Double> tree = tree(striker);
        boolean vsPlayer = target instanceof Player;
        double healed = fight.healed(tree, dealt, vsPlayer, settings.pvpPercent());
        if (healed > 0) {
            striker.setHealth(Math.min(TargetService.maxHealth(striker), striker.getHealth() + healed));
        }
        if (rolls(tree, Perk.BLEED, vsPlayer)) {
            bleed(striker, target, BLEED_PULSES);
        }
        if (critical && rolls(tree, Perk.STUN, vsPlayer)) {
            Living.give(target, PotionEffectType.SLOWNESS, 2, 3);
            Living.give(target, PotionEffectType.WEAKNESS, 2, 0);
            target.getWorld().spawnParticle(Particle.CRIT, target.getEyeLocation(), 12, 0.3, 0.2, 0.3, 0.1);
        }
        if (rolls(tree, Perk.VENOM, vsPlayer)) {
            Living.give(target, PotionEffectType.POISON, 4, 0);
        }
        hex(tree, target, vsPlayer);
        double max = TargetService.maxHealth(target);
        boolean boss = target instanceof org.bukkit.entity.Boss || target instanceof org.bukkit.entity.ElderGuardian
                || target instanceof org.bukkit.entity.Warden;
        if (fight.finishes(tree, vsPlayer, boss, target.getHealth() - dealt, max)) {
            // The blow is not dealt yet: once it is, a monster left below a fifth of its health is finished.
            de.raindancer.core.platform.util.Scheduling.entityLater(plugin, target, 1, () -> {
                if (!target.isDead() && target.isValid() && target.getHealth() < max * FightRule.EXECUTE_SHARE) {
                    target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, TargetService.middleOf(target), 1);
                    target.setHealth(0);
                }
            });
        }
        if (tree.getOrDefault(Perk.DUAL_WIELD, 0.0) <= 0 || offHand.contains(striker.getUniqueId())) {
            return;
        }
        ItemStack off = striker.getInventory().getItemInOffHand();
        double weapon = weaponDamage(off);
        if (weapon <= 0) {
            return;
        }
        double strike = fight.offHand(weapon + weaponBonus(off));
        de.raindancer.core.platform.util.Scheduling.entityLater(plugin, striker, OFF_HAND_DELAY, () -> {
            if (!striker.isOnline() || striker.isDead() || target.isDead() || !target.isValid()
                    || target.getWorld() != striker.getWorld() || target.getLocation().distance(striker.getLocation()) > 6) {
                return;
            }
            striker.swingOffHand();
            // Held until the strike is dealt — on Folia that is on the target's own thread, later.
            offHand.add(striker.getUniqueId());
            Living.onTarget(plugin, target, () -> {
                try {
                    target.setNoDamageTicks(0);
                    target.damage(strike, DamageSource.builder(DamageType.PLAYER_ATTACK)
                            .withCausingEntity(striker).withDirectEntity(striker).build());
                } finally {
                    offHand.remove(striker.getUniqueId());
                }
            });
        });
    }

    private void hex(Map<Perk, Double> tree, LivingEntity target, boolean vsPlayer) {
        if (rolls(tree, Perk.HEX, vsPlayer)) {
            Living.give(target, PotionEffectType.WEAKNESS, 5, 0);
            target.getWorld().spawnParticle(Particle.WITCH, target.getEyeLocation(), 10, 0.3, 0.3, 0.3, 0.05);
        }
    }

    /** A pulse of bleeding a second from now, and the next after it. */
    private void bleed(Player striker, LivingEntity target, int left) {
        if (left <= 0) {
            return;
        }
        de.raindancer.core.platform.util.Scheduling.entityLater(plugin, target, 20, () -> {
            if (target.isDead() || !target.isValid()) {
                return;
            }
            int frames = target.getNoDamageTicks();
            target.setNoDamageTicks(0);
            target.damage(1.0, DamageSource.builder(DamageType.GENERIC).withCausingEntity(striker).build());
            target.setNoDamageTicks(frames);
            target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, TargetService.middleOf(target), 3, 0.2, 0.3,
                    0.2, 0.05);
            bleed(striker, target, left - 1);
        });
    }

    /**
     * An arrow of the player's hit {@code target}: it may set it alight, pin it, hex it, mark it — and with Retrieval
     * it comes back to the quiver.
     */
    public void arrowLanded(Player shooter, org.bukkit.entity.AbstractArrow arrow, LivingEntity target) {
        Map<Perk, Double> tree = tree(shooter);
        if (tree.isEmpty()) {
            return;
        }
        boolean vsPlayer = target instanceof Player;
        if (rolls(tree, Perk.IGNITE, vsPlayer)) {
            target.setFireTicks(Math.max(target.getFireTicks(), 80));
        }
        if (rolls(tree, Perk.PIN, vsPlayer)) {
            Living.give(target, PotionEffectType.SLOWNESS, 2, 4);
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation(), 8, 0.3, 0.1, 0.3, 0.05);
        }
        hex(tree, target, vsPlayer);
        if (has(tree, Perk.HUNTERS_MARK)) {
            marks.put(shooter.getUniqueId(), new Mark(target.getUniqueId(), clock.getAsLong() + MARK_MILLIS));
            Living.give(target, PotionEffectType.GLOWING, (int) (MARK_MILLIS / 1000), 0);
        }
        if (has(tree, Perk.RETRIEVAL) && !(arrow instanceof org.bukkit.entity.Trident)
                && arrow.getPickupStatus() == org.bukkit.entity.AbstractArrow.PickupStatus.ALLOWED) {
            ItemStack back = arrow.getItemStack().asOne();
            // Only the first hit sends it back; a piercing arrow that lands later is not picked up again.
            arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.CREATIVE_ONLY);
            Living.onTarget(plugin, shooter, () -> shooter.getInventory().addItem(back).values()
                    .forEach(left -> shooter.getWorld().dropItem(shooter.getLocation(), left)));
        }
    }

    /** An arrow the player looses: faster with Velocity, and through two more foes with Skewer. */
    public void loosed(Player shooter, org.bukkit.entity.AbstractArrow arrow) {
        Map<Perk, Double> tree = tree(shooter);
        double faster = tree.getOrDefault(Perk.VELOCITY, 0.0);
        if (faster > 0) {
            arrow.setVelocity(arrow.getVelocity().multiply(1 + faster / 100.0));
        }
        if (has(tree, Perk.SKEWER)) {
            arrow.setPierceLevel(Math.max(arrow.getPierceLevel(), 2));
        }
    }

    /** The player killed something: their run of kills goes on, and Bloodlust heals them. */
    public void killed(Player killer) {
        long now = clock.getAsLong();
        streaks.compute(killer.getUniqueId(), (id, run) -> run == null || now - run[1] > STREAK_MILLIS
                ? new long[]{1, now} : new long[]{run[0] + 1, now});
        if (has(tree(killer), Perk.BLOODLUST) && !killer.isDead()) {
            killer.setHealth(Math.min(TargetService.maxHealth(killer), killer.getHealth() + BLOODLUST_HEAL));
            killer.getWorld().spawnParticle(Particle.HEART, killer.getEyeLocation().add(0, 0.4, 0), 3, 0.3, 0.2, 0.3);
        }
    }

    /** Dash: crouching while sprinting throws the player ahead, once every few seconds. */
    public boolean dash(Player player) {
        if (!player.isSprinting() || player.isFlying() || player.isGliding() || player.isInsideVehicle()
                || !has(tree(player), Perk.DASH) || !dash.tryUse(player.getUniqueId())) {
            return false;
        }
        var ahead = player.getLocation().getDirection().setY(0);
        if (ahead.lengthSquared() < 1e-6) {
            return false;
        }
        player.setVelocity(ahead.normalize().multiply(1.5).setY(0.3));
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 12, 0.3, 0.1, 0.3, 0.02);
        effects.play(player.getUniqueId(), Cues.ABILITY);
        return true;
    }

    /** Whether the blow being dealt is a Dual Wielder's off hand. */
    public boolean isOffHand(Player striker) {
        return offHand.contains(striker.getUniqueId());
    }

    /** What a weapon does in the main hand: the player's own 1 and the item's damage, 0 for no weapon. */
    static double weaponDamage(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return 0;
        }
        double damage = 0;
        for (AttributeModifier modifier : item.getType().getDefaultAttributeModifiers(EquipmentSlot.HAND)
                .get(Attribute.ATTACK_DAMAGE)) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                damage += modifier.getAmount();
            }
        }
        return damage <= 0 ? 0 : damage + 1;
    }

    public static int weaponBonus(ItemStack weapon) {
        return ItemTags.itemOf(weapon).filter(MagicItem::isWeapon).map(item -> item.amount().bonus()).orElse(0);
    }

    // ------------------------------------------------------------------ blows taken

    /** Whether the blow or arrow coming at the player misses them. */
    public boolean dodges(Player victim, boolean byPlayer) {
        double chance = fight.dodgeChance(tree(victim), byPlayer, settings.pvpPercent());
        if (chance <= 0 || ThreadLocalRandom.current().nextDouble() >= chance) {
            return false;
        }
        fought(victim.getUniqueId());
        effects.play(victim.getUniqueId(), Cues.VANISH);
        if (has(tree(victim), Perk.VANISH)) {
            Living.give(victim, PotionEffectType.INVISIBILITY, 3, 0);
        }
        actionBars.show(victim.getUniqueId(), "dnd-dodge", messages.get("dnd.combat.dodged"),
                java.time.Duration.ofSeconds(1), ActionBarPriority.LOW);
        return true;
    }

    /** What a hurt does to the player, with the tree and a Ring of Protection. */
    public double taken(Player victim, double damage, boolean byPlayer) {
        return taken(victim, damage, byPlayer, FightRule.Ward.NONE);
    }

    /** As the other, with the ward for this kind of hurt, and Mana Shield paying its share in experience. */
    public double taken(Player victim, double damage, boolean byPlayer, FightRule.Ward ward) {
        fought(victim.getUniqueId());
        boolean ring = ItemTags.itemOf(victim.getInventory().getItemInOffHand())
                .filter(item -> item == MagicItem.RING_OF_PROTECTION).isPresent()
                && settings.worksIn(victim.getWorld().getName());
        Map<Perk, Double> tree = tree(victim);
        double left = fight.taken(tree, damage, byPlayer, settings.pvpPercent(), ring, ward);
        if (tree.getOrDefault(Perk.MANA_SHIELD, 0.0) <= 0) {
            return left;
        }
        int points = victim.calculateTotalExperiencePoints();
        FightRule.Paid paid = fight.manaShield(tree, left, byPlayer, settings.pvpPercent(), points);
        if (paid.points() > 0) {
            victim.setExperienceLevelAndProgress(points - paid.points());
            victim.getWorld().spawnParticle(Particle.ENCHANT, TargetService.middleOf(victim), 12, 0.4, 0.5, 0.4, 0.5);
        }
        return paid.damage();
    }

    /** What a fall does to the player. */
    public double fell(Player victim, double damage) {
        return fight.fall(tree(victim), damage);
    }

    /** Whether poison or wither cannot hurt the player — Pure Blood. */
    public boolean pure(Player victim) {
        return has(tree(victim), Perk.PURE_BLOOD);
    }

    /** Whether the player never catches fire — Fireproof. */
    public boolean fireproof(Player victim) {
        return has(tree(victim), Perk.FIREPROOF);
    }

    /** Whether the player's shield never wears out — Aegis. */
    public boolean aegis(Player player) {
        return has(tree(player), Perk.AEGIS);
    }

    /**
     * A melee blow that hurt the player for {@code damage}: Thorns hurts the striker back. Only a blow, so thorns
     * never answer thorns.
     */
    public void struckBy(Player victim, LivingEntity striker, double damage) {
        double back = fight.thorns(tree(victim), damage, striker instanceof Player, settings.pvpPercent());
        if (back <= 0 || striker.isDead()) {
            return;
        }
        Living.onTarget(plugin, striker, () -> striker.damage(back, DamageSource.builder(DamageType.THORNS)
                .withCausingEntity(victim).withDirectEntity(victim).build()));
    }

    /** The player's shield stopped a blow: their next blow is a riposte, and Aegis throws the striker back. */
    public void blocked(Player victim, org.bukkit.entity.Entity striker) {
        fought(victim.getUniqueId());
        Map<Perk, Double> tree = tree(victim);
        if (tree.getOrDefault(Perk.RIPOSTE, 0.0) > 0) {
            riposte.put(victim.getUniqueId(), clock.getAsLong() + RIPOSTE_MILLIS);
        }
        if (has(tree, Perk.AEGIS) && striker instanceof LivingEntity foe && foe.getWorld() == victim.getWorld()) {
            var away = foe.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 1e-6) {
                var push = away.normalize().multiply(0.9).setY(0.35);
                Living.onTarget(plugin, foe, () -> foe.setVelocity(push));
            }
        }
    }

    /**
     * A hurt that would kill the player: Undying keeps them at one heart, once every ten minutes — unless a totem in
     * their hands will save them anyway.
     *
     * @return whether they were kept alive
     */
    public boolean undying(Player victim) {
        if (tree(victim).getOrDefault(Perk.UNDYING, 0.0) <= 0 || holdsTotem(victim)) {
            return false;
        }
        if (!undying.tryUse(victim.getUniqueId())) {
            return false;
        }
        victim.setHealth(Math.min(TargetService.maxHealth(victim), 2.0));
        Living.give(victim, PotionEffectType.RESISTANCE, 4, 1);
        victim.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, victim.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4,
                0.3);
        effects.play(victim.getUniqueId(), Cues.REWARD);
        messages.send(victim, "dnd.combat.undying");
        return true;
    }

    private static boolean holdsTotem(Player player) {
        return player.getInventory().getItemInMainHand().getType() == org.bukkit.Material.TOTEM_OF_UNDYING
                || player.getInventory().getItemInOffHand().getType() == org.bukkit.Material.TOTEM_OF_UNDYING;
    }

    /**
     * After a hurt landed: Second Wind, once below a third of their health; Smoke Bomb, once below a quarter. The
     * health is the one before the hurt: it is taken off after this.
     */
    public void hurt(Player victim, double damage) {
        if (victim.isDead()) {
            return;
        }
        Map<Perk, Double> tree = tree(victim);
        double max = TargetService.maxHealth(victim);
        double after = victim.getHealth() - Math.max(0, damage);
        if (after <= 0) {
            return;
        }
        if (after < max / 4 && has(tree, Perk.SMOKE_BOMB) && smoke.tryUse(victim.getUniqueId())) {
            smokeBomb(victim);
        }
        if (after >= max / 3 || !has(tree, Perk.SECOND_WIND) || !secondWind.tryUse(victim.getUniqueId())) {
            return;
        }
        // Healed once the hurt is taken off.
        de.raindancer.core.platform.util.Scheduling.entityLater(plugin, victim, 1, () -> {
            if (!victim.isDead()) {
                victim.setHealth(Math.min(TargetService.maxHealth(victim), victim.getHealth() + max / 3));
            }
        });
        effects.play(victim.getUniqueId(), Cues.HEAL);
        messages.send(victim, "dnd.combat.second-wind");
    }

    private void smokeBomb(Player victim) {
        Living.give(victim, PotionEffectType.INVISIBILITY, 4, 0);
        Living.give(victim, PotionEffectType.SPEED, 4, 1);
        victim.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, victim.getLocation().add(0, 1, 0), 40, 0.8, 0.8,
                0.8, 0.02);
        for (org.bukkit.entity.Entity near : victim.getNearbyEntities(16, 8, 16)) {
            if (near instanceof org.bukkit.entity.Mob mob) {
                Living.onTarget(plugin, mob, () -> {
                    if (victim.equals(mob.getTarget())) {
                        mob.setTarget(null);
                    }
                });
            }
        }
        effects.play(victim.getUniqueId(), Cues.VANISH);
        messages.send(victim, "dnd.combat.smoke-bomb");
    }

    // ------------------------------------------------------------------ out of a fight, and kills

    /**
     * Every five seconds, for a player who has not fought for a while: Mending heals them, and an Arcane Barrier
     * gathers again once the last is spent.
     */
    public void mend(Player player) {
        Map<Perk, Double> tree = tree(player);
        if (tree.isEmpty() || player.isDead()) {
            return;
        }
        Long last = fighting.get(player.getUniqueId());
        if (last != null && clock.getAsLong() - last < OUT_OF_FIGHT_MILLIS) {
            return;
        }
        double perFive = tree.getOrDefault(Perk.REGEN, 0.0);
        double max = TargetService.maxHealth(player);
        if (perFive > 0 && player.getHealth() < max) {
            player.setHealth(Math.min(max, player.getHealth() + perFive));
        }
        if (has(tree, Perk.ARCANE_BARRIER) && player.getAbsorptionAmount() <= 0) {
            // Four hearts of absorption; one that somebody drank is never taken away.
            Living.takeBack(player, PotionEffectType.ABSORPTION, 1);
            if (!player.hasPotionEffect(PotionEffectType.ABSORPTION)) {
                Living.give(player, PotionEffectType.ABSORPTION, 120, 1);
                player.getWorld().spawnParticle(Particle.ENCHANT, TargetService.middleOf(player), 20, 0.5, 0.6, 0.5,
                        0.6);
            }
        }
    }

    /**
     * A potion effect came to the player: one they drank lasts longer with Potion Duration, a harmful one wears off
     * sooner with Effect Shed. It is put back a tick later at its new length, by this plugin, so it is not changed
     * twice.
     */
    public void effected(Player player, org.bukkit.potion.PotionEffect effect, boolean drunk) {
        if (effect == null || effect.isInfinite() || effect.isAmbient()) {
            return;
        }
        Map<Perk, Double> tree = tree(player);
        boolean harmful = effect.getType().getCategory() == org.bukkit.potion.PotionEffectTypeCategory.HARMFUL;
        int ticks;
        if (harmful && tree.getOrDefault(Perk.EFFECT_SHED, 0.0) > 0) {
            ticks = fight.shorter(tree, effect.getDuration());
        } else if (!harmful && drunk && tree.getOrDefault(Perk.POTION_DURATION, 0.0) > 0) {
            ticks = fight.longer(tree, effect.getDuration());
        } else {
            return;
        }
        if (ticks == effect.getDuration()) {
            return;
        }
        de.raindancer.core.platform.util.Scheduling.entityLater(plugin, player, 1, () -> {
            var now = player.getPotionEffect(effect.getType());
            if (now == null || now.getAmplifier() != effect.getAmplifier() || now.getDuration() > effect.getDuration()) {
                return;
            }
            player.removePotionEffect(effect.getType());
            player.addPotionEffect(effect.withDuration(Math.max(1, ticks - (effect.getDuration() - now.getDuration()))));
        });
    }

    /**
     * Something the player did, from Core's {@code Activities}: its worth in adventurer experience, with Studious and
     * its kin. Nothing for a player who is away or outside the adventurers' worlds.
     */
    public void did(Player player, de.raindancer.core.world.activity.Activity activity) {
        if (player == null || !settings.worksIn(player.getWorld().getName())
                || de.raindancer.core.social.presence.Away.isAway(player.getUniqueId())) {
            return;
        }
        long worth = worths.allowed(activity.kind(), used(player.getUniqueId(), activity.kind(), 0),
                worths.worth(activity));
        if (worth <= 0) {
            return;
        }
        used(player.getUniqueId(), activity.kind(), worth);
        Optional<Hero> hero = heroes.ensure(player);
        double more = hero.map(found -> perks.value(found, Perk.XP)).orElse(0.0);
        heroes.grantXp(player, Math.round(worth * (100 + more) / 100.0), source(activity), true);
    }

    /** This minute's experience of a kind so far, after {@code adding} to it. */
    private long used(UUID player, de.raindancer.core.world.activity.ActivityKind kind, long adding) {
        long minute = clock.getAsLong() / 60_000L;
        Map<de.raindancer.core.world.activity.ActivityKind, long[]> mine = minutes.computeIfAbsent(player,
                id -> new ConcurrentHashMap<>());
        long[] bucket = mine.compute(kind, (k, held) -> held == null || held[0] != minute
                ? new long[]{minute, 0} : held);
        synchronized (bucket) {
            bucket[1] += adding;
            return bucket[1];
        }
    }

    private static String source(de.raindancer.core.world.activity.Activity activity) {
        return switch (activity.kind()) {
            case PLAY -> "playing";
            case MINE -> activity.what().replace('-', ' ') + " ore";
            case TRAVEL -> "travelling";
            case EXPLORE -> "a new biome";
            case ENCHANT -> "enchanting";
            case REPAIR -> "anvil work";
            case TRADE -> "trading";
            case FISH -> "fishing";
            default -> activity.what().isEmpty() ? activity.kind().name().toLowerCase(java.util.Locale.ROOT)
                    : activity.what().replace("minecraft:", "").replace('_', ' ');
        };
    }

    /** Experience from orbs, with Orb Catcher and its kin. */
    public int orbs(Player player, int amount) {
        double more = tree(player).getOrDefault(Perk.VANILLA_XP, 0.0);
        return amount <= 0 || more <= 0 ? amount : (int) Math.min(Integer.MAX_VALUE, Math.round(amount * (100 + more) / 100));
    }

    public double chance(Player player, Perk perk) {
        return tree(player).getOrDefault(perk, 0.0) / 100.0;
    }
}
