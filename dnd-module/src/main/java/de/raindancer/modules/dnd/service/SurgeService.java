package de.raindancer.modules.dnd.service;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effect;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.effect.ParticleCue;
import de.raindancer.core.ui.effect.SoundCue;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.Condition;
import de.raindancer.modules.dnd.model.Dice;
import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.Spell;
import de.raindancer.modules.dnd.model.Surge;
import de.raindancer.modules.dnd.rules.SorceryRule;
import de.raindancer.modules.dnd.rules.SurgeRule;
import de.raindancer.modules.dnd.util.Living;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/**
 * Wild magic: whether a sorcerer's spell surges, and what the surge does.
 *
 * <p>Nothing a surge does outlasts it by accident. Summoned creatures carry a tag, drop nothing and are removed when
 * their time is up, or when the module stops; a change of size, weight or health is a transient modifier taken off
 * again on a timer; illusory blocks are sent to the players who see them and never placed in the world.
 */
public final class SurgeService implements IDndService {

    public static final NamespacedKey SUMMONED = new NamespacedKey(BodyService.NAMESPACE, "summoned");
    static final NamespacedKey SURGE_SCALE = new NamespacedKey(BodyService.NAMESPACE, "surge_scale");
    static final NamespacedKey SURGE_GRAVITY = new NamespacedKey(BodyService.NAMESPACE, "surge_gravity");
    static final NamespacedKey SURGE_HEALTH = new NamespacedKey(BodyService.NAMESPACE, "surge_health");

    private static final double BASE_GRAVITY = 0.08;
    private static final int EFFECT_RADIUS = 10;
    private static final int NEARBY_RADIUS = 8;

    private final Plugin plugin;
    private final Messages messages;
    private final Effects effects;
    private final HeroService heroes;
    private final SpellService spells;
    private final BodyService body;
    private final ConditionService conditions;
    private final TargetService targets;
    private final SpellCues cues;
    private final SorceryRule sorcery;
    private final SurgeRule rule;
    private final RandomGenerator random;

    private final Set<UUID> tidesOwed = ConcurrentHashMap.newKeySet();
    /** What a surge summoned, by id, and when it goes. Looked up fresh each time: an entity that reloads is a new object. */
    private final java.util.Map<UUID, Long> summoned = new ConcurrentHashMap<>();
    private final org.bukkit.Server server;
    private volatile Consumer<Player> resetWaits = player -> { };
    private volatile DndSettings settings = DndSettings.DEFAULTS;

    public SurgeService(Plugin plugin, Messages messages, Effects effects, HeroService heroes, SpellService spells,
                        BodyService body, ConditionService conditions, TargetService targets, SpellCues cues,
                        SorceryRule sorcery, SurgeRule rule, RandomGenerator random) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.messages = messages;
        this.effects = effects;
        this.heroes = heroes;
        this.spells = spells;
        this.body = body;
        this.conditions = conditions;
        this.targets = targets;
        this.cues = cues;
        this.sorcery = sorcery;
        this.rule = rule;
        this.random = random;
    }

    @Override
    public void settings(DndSettings updated) {
        this.settings = updated == null ? DndSettings.DEFAULTS : updated;
    }

    /** How "every wait is over" is done; the casting service knows its waits. */
    public void onResetWaits(Consumer<Player> how) {
        this.resetWaits = how == null ? player -> { } : how;
    }

    /** Tides of Chaos was used: the next spell from a slot surges for certain. */
    public void owe(UUID player) {
        tidesOwed.add(player);
    }

    /** After a spell from a slot: surges when the sorcerer owes one, or on the percentile roll. */
    public void afterCast(Player player, Hero hero, int slotLevel) {
        if (slotLevel < 1 || !Feature.WILD_MAGIC_SURGE.has(hero)) {
            return;
        }
        if (tidesOwed.remove(player.getUniqueId())) {
            heroes.change(player, now -> now.withUsed(Feature.TIDES_OF_CHAOS, 0));
            messages.send(player, "dnd.surge-tides");
            surge(player, hero);
            return;
        }
        if (rule.surges(1 + random.nextInt(100), settings.wildSurgePercent())) {
            surge(player, hero);
        }
    }

    /** Rolls on the table and makes it happen. */
    public Surge surge(Player player, Hero hero) {
        boolean controlled = Feature.CONTROLLED_CHAOS.has(hero);
        Surge surge = rule.pick(1 + random.nextInt(100), 1 + random.nextInt(100), controlled);
        happen(player, hero, surge);
        return surge;
    }

    public void happen(Player player, Hero hero, Surge surge) {
        messages.send(player, "dnd.surge", "number", String.valueOf(surge.number()), "text", surge.text());
        effects.play(player.getUniqueId(), Cues.MAGIC);
        cues.at(TargetService.middleOf(player), de.raindancer.modules.dnd.model.DamageKind.FORCE);
        switch (surge.kind()) {
            case FIREBALL_ON_SELF -> spells.explodeAt(new SpellService.Cast(player, hero, Spell.FIREBALL, surge.b(),
                    null), player.getLocation(), true);
            case CAST -> Spell.byId(surge.what()).ifPresent(spell -> spells.perform(new SpellService.Cast(player,
                    hero, spell, Math.max(spell.level(), surge.b()), null)));
            case RANDOM_SPELL -> randomSpell(player, hero, surge.b());
            case SELF_EFFECT -> effect(surge.what()).ifPresent(type ->
                    Living.give(player, type, surge.a(), surge.b()));
            case NEARBY_EFFECT -> effect(surge.what()).ifPresent(type -> {
                Living.give(player, type, surge.a(), surge.b());
                for (LivingEntity other : targets.around(player.getLocation(), NEARBY_RADIUS, targets.foes(player))) {
                    Living.onTarget(plugin, other, () -> Living.give(other, type, surge.a(), surge.b()));
                }
            });
            case FOES_EFFECT -> effect(surge.what()).ifPresent(type -> {
                for (LivingEntity foe : targets.around(player.getLocation(), EFFECT_RADIUS,
                        targets.foes(player).and(entity -> entity instanceof Enemy))) {
                    Living.onTarget(plugin, foe, () -> Living.give(foe, type, surge.a(), surge.b()));
                }
            });
            case ALLIES_EFFECT -> effect(surge.what()).ifPresent(type -> {
                for (Player ally : targets.allies(player, EFFECT_RADIUS)) {
                    Living.onTarget(plugin, ally, () -> Living.give(ally, type, surge.a(), surge.b()));
                }
            });
            case SCALE -> lendFor(player, Attribute.SCALE, SURGE_SCALE, surge.a() / 100.0 - 1.0, surge.b());
            case GRAVITY -> lendFor(player, Attribute.GRAVITY, SURGE_GRAVITY,
                    BASE_GRAVITY * (surge.a() / 100.0 - 1.0), surge.b());
            case TELEPORT -> teleport(player, surge.a());
            case LAUNCH -> {
                player.setVelocity(new Vector(0, surge.a() / 10.0, 0));
                Scheduling.entityLater(plugin, player, 25L,
                        () -> Living.give(player, PotionEffectType.SLOW_FALLING, 10, 0));
            }
            case SUMMON -> summon(player, surge, false);
            case RAIN -> summon(player, surge, true);
            case HEAL_SELF -> heal(player, Dice.of(surge.a(), surge.b()).roll(random));
            case HURT_SELF -> {
                int hurt = Dice.of(surge.a(), surge.b()).roll(random);
                player.damage(hurt, DamageSource.builder(DamageType.MAGIC).build());
            }
            case SORCERY_BACK -> heroes.change(player, now -> now.withSorceryPoints(sorcery.most(now)));
            case SLOT_BACK -> heroes.change(player, SurgeService::oneSlotBack);
            case FREE_SPELLS -> conditions.set(player.getUniqueId(), Condition.FREE_CASTING, surge.a() * 1000L);
            case RESET_WAITS -> resetWaits.accept(player);
            case ILLUSION -> illusion(player, surge);
            case AURA -> aura(player, surge.what(), surge.a());
            case FIREWORKS -> fireworks(player, surge.a());
            case LIGHTNING_SHOW -> {
                for (int bolt = 0; bolt < surge.a(); bolt++) {
                    Location at = player.getLocation().add(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
                    player.getWorld().strikeLightningEffect(at);
                }
            }
            case GIFT -> {
                Material material = Material.valueOf(surge.what());
                player.getInventory().addItem(new ItemStack(material, surge.a())).values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
            case XP -> player.getWorld().spawn(player.getLocation().add(0, 0.5, 0), ExperienceOrb.class,
                    orb -> orb.setExperience(surge.a()));
            case SWAP -> swap(player, surge.a());
            case MAX_HEALTH -> {
                lendFor(player, Attribute.MAX_HEALTH, SURGE_HEALTH, surge.a(), surge.b());
                if (surge.a() > 0) {
                    heal(player, surge.a());
                }
            }
            case OMEN -> effects.playAt(player.getWorld().getName(), player.getX(), player.getY(), player.getZ(),
                    Effect.of(new SoundCue(surge.what(), 1.0f, 1.0f)));
            case JACKPOT -> {
                heal(player, TargetService.maxHealth(player));
                heroes.change(player, now -> now.withSorceryPoints(sorcery.most(now)));
                conditions.set(player.getUniqueId(), Condition.FREE_CASTING, 60_000L);
                fireworks(player, 6);
            }
        }
    }

    // ------------------------------------------------------------------ the kinds

    private static Optional<PotionEffectType> effect(String name) {
        return Optional.ofNullable(Registry.EFFECT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT))));
    }

    private void randomSpell(Player player, Hero hero, int highest) {
        List<Spell> candidates = new ArrayList<>();
        for (Spell spell : Spell.values()) {
            if (spell.learnedBy(hero.heroClass()) && spell.level() >= 1 && spell.level() <= highest) {
                candidates.add(spell);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        Spell spell = candidates.get(random.nextInt(candidates.size()));
        messages.send(player, "dnd.surge-spell", "spell", spell.title());
        spells.perform(new SpellService.Cast(player, hero, spell, spell.level(), null));
    }

    /** Lends a modifier and takes it back after that many seconds. */
    private void lendFor(Player player, Attribute attribute, NamespacedKey key, double amount, int seconds) {
        body.lend(player, attribute, key, amount);
        Scheduling.entityLater(plugin, player, seconds * 20L, () -> body.lend(player, attribute, key, 0));
    }

    private void heal(Player player, double amount) {
        player.setHealth(Math.min(TargetService.maxHealth(player), player.getHealth() + Math.max(0, amount)));
        cues.at(TargetService.middleOf(player), de.raindancer.modules.dnd.model.DamageKind.HEALING);
    }

    private static Hero oneSlotBack(Hero hero) {
        for (int level = 1; level <= Hero.SLOT_LEVELS; level++) {
            if (hero.spentSlots(level) > 0) {
                return hero.withSlotSpent(level, hero.spentSlots(level) - 1);
            }
        }
        return hero;
    }

    private void teleport(Player player, int blocks) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = blocks / 2.0 + random.nextDouble() * blocks / 2.0;
            Location aim = player.getLocation().add(Math.cos(angle) * distance, 2, Math.sin(angle) * distance);
            if (!aim.getWorld().isChunkLoaded(aim.getBlockX() >> 4, aim.getBlockZ() >> 4)) {
                continue;
            }
            Location landing = TargetService.standable(aim);
            if (landing != null) {
                landing.setYaw(player.getLocation().getYaw());
                landing.setPitch(player.getLocation().getPitch());
                player.teleportAsync(landing);
                return;
            }
        }
    }

    private void summon(Player player, Surge surge, boolean fromAbove) {
        EntityType type = EntityType.valueOf(surge.what());
        for (int count = 0; count < surge.a(); count++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = fromAbove ? random.nextDouble() * 4 : 1.5 + random.nextDouble() * 1.5;
            Location at = player.getLocation().add(Math.cos(angle) * distance, fromAbove ? 10 : 0.2,
                    Math.sin(angle) * distance);
            if (!fromAbove) {
                Location safe = TargetService.standable(at.clone().add(0, 1, 0));
                at = safe == null ? player.getLocation() : safe;
            }
            Entity entity = player.getWorld().spawnEntity(at, type);
            entity.getPersistentDataContainer().set(SUMMONED, PersistentDataType.BYTE, (byte) 1);
            entity.setPersistent(false);
            if (entity instanceof Tameable pet && surge.tone() == Surge.Tone.GOOD) {
                pet.setOwner(player);
            }
            if (entity instanceof IronGolem golem) {
                golem.setPlayerCreated(true);
            }
            if (entity instanceof Mob mob && surge.tone() == Surge.Tone.BAD) {
                mob.setTarget(player);
            }
            summoned.put(entity.getUniqueId(), System.currentTimeMillis() + surge.b() * 1000L);
        }
        // One delayed task per surge rather than a timer: nothing runs while nothing is summoned.
        Scheduling.globalLater(plugin, surge.b() * 20L + 5L, this::sweep);
        Scheduling.globalLater(plugin, surge.b() * 20L + 20L * 30, this::sweep);
    }

    private void illusion(Player player, Surge surge) {
        Material material = Material.valueOf(surge.what());
        BlockData fake = material.createBlockData();
        List<Location> spots = new ArrayList<>();
        Location centre = player.getLocation();
        int radius = Math.max(1, surge.b());
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius || random.nextInt(100) >= 45) {
                    continue;
                }
                Location feet = centre.clone().add(x, 0, z);
                if (feet.getBlock().getType().isAir() && feet.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                    spots.add(feet.getBlock().getLocation());
                }
            }
        }
        List<Player> seeing = new ArrayList<>();
        for (Player other : player.getWorld().getPlayers()) {
            if (other.getLocation().distanceSquared(centre) < 48 * 48) {
                seeing.add(other);
            }
        }
        for (Player viewer : seeing) {
            for (Location spot : spots) {
                viewer.sendBlockChange(spot, fake);
            }
        }
        Scheduling.regionLater(plugin, centre, surge.a() * 20L, () -> {
            for (Player viewer : seeing) {
                if (!viewer.isOnline()) {
                    continue;
                }
                for (Location spot : spots) {
                    viewer.sendBlockChange(spot, spot.getBlock().getBlockData());
                }
            }
        });
    }

    private void aura(Player player, String particle, int seconds) {
        long until = System.currentTimeMillis() + seconds * 1000L;
        Scheduling.entityTimer(plugin, player, 1L, 10L, task -> {
            if (!player.isOnline() || System.currentTimeMillis() >= until) {
                task.cancel();
                return;
            }
            effects.playAt(player.getWorld().getName(), player.getX(), player.getY() + 1.2, player.getZ(),
                    Effect.of(new ParticleCue(particle, 4, 0.4, 0.5, 0.4, 0.01, null)));
        });
    }

    private void fireworks(Player player, int bursts) {
        for (int burst = 0; burst < bursts; burst++) {
            Location at = player.getLocation().add(random.nextInt(9) - 4, 4 + random.nextInt(5),
                    random.nextInt(9) - 4);
            int colour = random.nextInt(0xFFFFFF);
            effects.playAt(at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), new Effect(
                    new SoundCue("entity.firework_rocket.blast", 1.0f, 1.0f),
                    new ParticleCue("DUST", 40, 1.2, 1.2, 1.2, 0.0, colour)));
        }
    }

    private void swap(Player player, int blocks) {
        Optional<LivingEntity> nearest = targets.around(player.getLocation(), blocks,
                        entity -> !entity.equals(player) && !(entity instanceof Player) && entity.isValid())
                .stream().findFirst();
        if (nearest.isEmpty()) {
            return;
        }
        LivingEntity other = nearest.get();
        Location mine = player.getLocation();
        Location theirs = other.getLocation();
        theirs.setYaw(mine.getYaw());
        theirs.setPitch(mine.getPitch());
        player.teleportAsync(theirs);
        Living.onTarget(plugin, other, () -> other.teleportAsync(mine));
    }

    /** Whatever a surge summoned whose time is up goes. Run once after each surge, and again half a minute later. */
    public void sweep() {
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<UUID, Long> entry : java.util.List.copyOf(summoned.entrySet())) {
            if (entry.getValue() > now) {
                continue;
            }
            Entity entity = server.getEntity(entry.getKey());
            if (entity == null) {
                // Not loaded right now. It is not saved either, so it cannot come back; give up on it after a minute.
                if (now - entry.getValue() > 60_000L) {
                    summoned.remove(entry.getKey());
                }
                continue;
            }
            summoned.remove(entry.getKey());
            Scheduling.entity(plugin, entity, entity::remove);
        }
    }

    /** Everything a surge brought into the world, gone — for the module stopping. */
    public void cleanUp() {
        for (UUID id : java.util.List.copyOf(summoned.keySet())) {
            Entity entity = server.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        summoned.clear();
    }

    public int summoned() {
        return summoned.size();
    }

    public void forget(UUID player) {
        tidesOwed.remove(player);
    }

    public static boolean isSummoned(Entity entity) {
        return entity.getPersistentDataContainer().has(SUMMONED, PersistentDataType.BYTE);
    }
}
