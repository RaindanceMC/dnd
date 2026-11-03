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
 * Wielder, Second Wind and Undying, healing out of a fight, and experience for what a player does.
 */
public final class CombatService implements ISkillsService {

    static final long SECOND_WIND_MILLIS = 120_000;
    static final long UNDYING_MILLIS = 600_000;
    /** No blow given or taken for this long: out of a fight, and Mending works. */
    static final long OUT_OF_FIGHT_MILLIS = 8_000;
    /** Ticks between the main hand's blow and the off hand's. */
    private static final long OFF_HAND_DELAY = 4;

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
    private final Map<UUID, Long> fighting = new ConcurrentHashMap<>();
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
    }

    // ------------------------------------------------------------------ blows given

    /** A blow or an arrow the player landed: what it does now. */
    public double dealt(Player striker, LivingEntity target, FightRule.Kind kind, boolean critical, double damage,
                        double distance) {
        fought(striker.getUniqueId());
        Map<Perk, Double> tree = tree(striker);
        ItemStack main = striker.getInventory().getItemInMainHand();
        int bonus = kind == FightRule.Kind.RANGED ? 0 : weaponBonus(main);
        double max = TargetService.maxHealth(striker);
        var from = striker.getLocation().toVector().subtract(target.getLocation().toVector());
        var facing = target.getLocation().getDirection();
        MobProfile profile = target instanceof Player ? MobProfile.HARMLESS : mobs.of(target.getType());
        FightRule.Hit hit = new FightRule.Hit(kind, critical, FightRule.behind(facing.getX(), facing.getZ(),
                from.getX(), from.getZ()), profile.undead(), target instanceof Player,
                striker.getInventory().getItemInOffHand().isEmpty(), striker.getHealth() < max / 2, distance, bonus);
        return fight.dealt(tree, hit, damage, settings.pvpPercent());
    }

    /** A melee blow landed for {@code dealt}: lifesteal, and the off hand's strike for a Dual Wielder. */
    public void landed(Player striker, LivingEntity target, double dealt) {
        Map<Perk, Double> tree = tree(striker);
        double healed = fight.healed(tree, dealt, target instanceof Player, settings.pvpPercent());
        if (healed > 0) {
            striker.setHealth(Math.min(TargetService.maxHealth(striker), striker.getHealth() + healed));
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
        actionBars.show(victim.getUniqueId(), "dnd-dodge", messages.get("dnd.combat.dodged"),
                java.time.Duration.ofSeconds(1), ActionBarPriority.LOW);
        return true;
    }

    /** What a hurt does to the player, with the tree and a Ring of Protection. */
    public double taken(Player victim, double damage, boolean byPlayer) {
        fought(victim.getUniqueId());
        boolean ring = ItemTags.itemOf(victim.getInventory().getItemInOffHand())
                .filter(item -> item == MagicItem.RING_OF_PROTECTION).isPresent()
                && settings.worksIn(victim.getWorld().getName());
        return fight.taken(tree(victim), damage, byPlayer, settings.pvpPercent(), ring);
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

    /** After a hurt landed: Second Wind, once below a third of their health. */
    public void hurt(Player victim) {
        if (victim.isDead() || tree(victim).getOrDefault(Perk.SECOND_WIND, 0.0) <= 0) {
            return;
        }
        double max = TargetService.maxHealth(victim);
        if (victim.getHealth() >= max / 3) {
            return;
        }
        if (!secondWind.tryUse(victim.getUniqueId())) {
            return;
        }
        victim.setHealth(Math.min(max, victim.getHealth() + max / 3));
        effects.play(victim.getUniqueId(), Cues.HEAL);
        messages.send(victim, "dnd.combat.second-wind");
    }

    // ------------------------------------------------------------------ out of a fight, and kills

    /** Every five seconds: Mending heals a player who has not fought for a while. */
    public void mend(Player player) {
        double perFive = tree(player).getOrDefault(Perk.REGEN, 0.0);
        if (perFive <= 0 || player.isDead()) {
            return;
        }
        Long last = fighting.get(player.getUniqueId());
        if (last != null && clock.getAsLong() - last < OUT_OF_FIGHT_MILLIS) {
            return;
        }
        double max = TargetService.maxHealth(player);
        if (player.getHealth() < max) {
            player.setHealth(Math.min(max, player.getHealth() + perFive));
        }
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
