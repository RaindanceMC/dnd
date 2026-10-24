package de.raindancer.modules.dnd.service;

import de.raindancer.core.platform.util.Cooldowns;
import de.raindancer.core.ui.actionbar.ActionBarPriority;
import de.raindancer.core.ui.actionbar.ActionBars;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.Castable;
import de.raindancer.modules.dnd.model.Condition;
import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.Metamagic;
import de.raindancer.modules.dnd.model.Spell;
import de.raindancer.modules.dnd.rules.FeatureRule;
import de.raindancer.modules.dnd.rules.LoadoutRule;
import de.raindancer.modules.dnd.rules.MetamagicRule;
import de.raindancer.modules.dnd.rules.SpellSlotRule;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Casting a spell or using a feature, start to finish: may they, is it their turn, which slot, which metamagic, did
 * it go off — and only then is anything spent.
 *
 * <h2>Turns, in real time</h2>
 * An action and a bonus action are two separate waits, as a turn at the table has room for one of each: a fireball
 * and a Misty Step back-to-back, but not two fireballs. Action Surge ends every wait at once.
 */
public final class CastingService implements IDndService {

    private final Messages messages;
    private final Effects effects;
    private final ActionBars actionBars;
    private final HeroService heroes;
    private final StanceService stance;
    private final SpellService spells;
    private final FeatureService featureService;
    private final ConcentrationService concentration;
    private final ConditionService conditions;
    private final SpellSlotRule slots;
    private final FeatureRule features;
    private final MetamagicRule metamagic;
    private final LoadoutRule loadouts;

    private final Cooldowns<UUID> actions = new Cooldowns<>();
    private final Cooldowns<UUID> bonusActions = new Cooldowns<>();
    private final Map<Feature, Cooldowns<UUID>> featureWaits = new EnumMap<>(Feature.class);

    private volatile DndSettings settings = DndSettings.DEFAULTS;
    private volatile SurgeService surges;

    public CastingService(Messages messages, Effects effects, ActionBars actionBars, HeroService heroes,
                          StanceService stance, SpellService spells, FeatureService featureService,
                          ConcentrationService concentration, ConditionService conditions, SpellSlotRule slots,
                          FeatureRule features, MetamagicRule metamagic, LoadoutRule loadouts) {
        this.messages = messages;
        this.effects = effects;
        this.actionBars = actionBars;
        this.heroes = heroes;
        this.stance = stance;
        this.spells = spells;
        this.featureService = featureService;
        this.concentration = concentration;
        this.conditions = conditions;
        this.slots = slots;
        this.features = features;
        this.metamagic = metamagic;
        this.loadouts = loadouts;
        for (Feature feature : Feature.values()) {
            int seconds = features.cooldownSeconds(feature);
            if (seconds > 0) {
                Cooldowns<UUID> wait = new Cooldowns<>();
                wait.every(Duration.ofSeconds(seconds));
                featureWaits.put(feature, wait);
            }
        }
        settings(DndSettings.DEFAULTS);
    }

    /** Wild magic, told after every spell from a slot. */
    public void surgesWith(SurgeService surges) {
        this.surges = surges;
    }

    @Override
    public void settings(DndSettings updated) {
        this.settings = updated == null ? DndSettings.DEFAULTS : updated;
        actions.every(Duration.ofMillis(Math.max(0, settings.actionMillis())));
        bonusActions.every(Duration.ofMillis(Math.max(0, settings.bonusActionMillis())));
    }

    /**
     * @param upcast cast a spell from the highest slot left rather than the lowest that will do
     */
    public void cast(Player player, Castable castable, boolean upcast) {
        UUID id = player.getUniqueId();
        Optional<Hero> found = heroes.of(id);
        if (found.isEmpty()) {
            messages.send(player, "dnd.no-class");
            return;
        }
        Hero hero = found.get();
        if (!settings.worksIn(player.getWorld().getName())) {
            refuse(player, "dnd.stance.not-here");
            return;
        }
        if (conditions.has(id, Condition.HELD)) {
            refuse(player, "dnd.cast.held");
            return;
        }
        if (!loadouts.allowed(hero, castable.id())) {
            refuse(player, "dnd.cast.not-yours");
            return;
        }
        if (castable instanceof Feature feature) {
            use(player, hero, feature);
        } else if (castable instanceof Spell spell) {
            cast(player, hero, spell, upcast);
        }
    }

    private void use(Player player, Hero hero, Feature feature) {
        UUID id = player.getUniqueId();
        Cooldowns<UUID> turn = feature.bonusAction() ? bonusActions : actions;
        if (feature != Feature.ACTION_SURGE && !ready(player, turn)) {
            return;
        }
        if (!features.mayUse(hero, feature)) {
            refuse(player, "dnd.feature.none-left", "name", feature.title());
            return;
        }
        int points = features.sorceryCost(feature);
        if (hero.sorceryPoints() < points) {
            refuse(player, "dnd.feature.too-few-points", "name", feature.title(), "cost", String.valueOf(points));
            return;
        }
        Cooldowns<UUID> wait = featureWaits.get(feature);
        if (wait != null && !wait.isReady(id)) {
            long seconds = Math.max(1, wait.remaining(id).orElse(Duration.ZERO).toSeconds());
            refuse(player, "dnd.feature.wait", "name", feature.title(), "seconds", String.valueOf(seconds));
            return;
        }
        Hero after = featureService.use(player, hero, feature);
        if (after == null) {
            effects.play(id, Cues.NO);
            return;
        }
        heroes.change(player, now -> features.spend(feature == Feature.ARCANE_RECOVERY ? after : now, feature)
                .withSorceryPoints(Math.max(0, now.sorceryPoints() - points)));
        if (feature == Feature.ACTION_SURGE) {
            endWaits(id);
        } else {
            begin(turn, id, hero);
            showWait(player, feature.bonusAction());
        }
        if (wait != null) {
            int full = features.cooldownSeconds(feature);
            int mine = features.cooldownSeconds(feature, hero);
            if (mine < full) {
                wait.forget(id);
                wait.restore(id, System.currentTimeMillis() - (full - mine) * 1000L);
            } else {
                wait.start(id);
            }
        }
        effects.play(id, Cues.ABILITY);
        actionBars.show(id, "dnd-cast", messages.get("dnd.cast.used", "name", feature.title()),
                Duration.ofSeconds(2), ActionBarPriority.NORMAL);
    }

    private void cast(Player player, Hero hero, Spell spell, boolean upcast) {
        UUID id = player.getUniqueId();
        Optional<Metamagic> armed = stance.armed(id).filter(found -> metamagic.knows(hero));
        Metamagic bending = null;
        if (armed.isPresent()) {
            if (!metamagic.fits(armed.get(), spell)) {
                messages.send(player, "dnd.metamagic.does-not-fit", "metamagic", armed.get().title(),
                        "spell", spell.title());
            } else if (!metamagic.affordable(hero, armed.get(), spell)) {
                refuse(player, "dnd.metamagic.too-few-points", "metamagic", armed.get().title(),
                        "cost", String.valueOf(armed.get().cost(spell.level())));
                return;
            } else {
                bending = armed.get();
            }
        }
        boolean bonus = spell.bonusAction() || bending == Metamagic.QUICKENED;
        Cooldowns<UUID> turn = bonus ? bonusActions : actions;
        if (!ready(player, turn)) {
            return;
        }
        int slotLevel = 0;
        boolean free = conditions.has(id, Condition.FREE_CASTING);
        if (!spell.isCantrip()) {
            Optional<Integer> slot = slots.pick(hero, spell.level(), upcast);
            if (slot.isEmpty() && !free) {
                refuse(player, "dnd.cast.no-slot", "spell", spell.title());
                return;
            }
            slotLevel = slot.orElse(spell.level());
        }
        boolean attackRoll = spell.shape() == de.raindancer.modules.dnd.model.SpellShape.BOLT
                || spell.shape() == de.raindancer.modules.dnd.model.SpellShape.TOUCH;
        boolean advantage = attackRoll && conditions.has(id, Condition.TIDES);
        boolean overchannel = spell.harmful() && spell.level() >= 1 && spell.level() <= 5
                && conditions.has(id, Condition.OVERCHANNELLING);
        if (conditions.has(id, Condition.HIDDEN)) {
            concentration.on(id).filter(focus -> focus.spell() == Spell.INVISIBILITY)
                    .ifPresent(focus -> concentration.end(player, "dnd.cast.visible"));
            conditions.clear(id, Condition.HIDDEN);
        }
        Optional<SpellService.Outcome> outcome = spells.perform(new SpellService.Cast(player, hero, spell,
                slotLevel, bending, new SpellService.Extras(null, null, advantage, overchannel)));
        if (outcome.isEmpty()) {
            effects.play(id, Cues.NO);
            return;
        }
        if (advantage) {
            conditions.clear(id, Condition.TIDES);
        }
        if (overchannel) {
            conditions.clear(id, Condition.OVERCHANNELLING);
        }
        int cast = slotLevel;
        int spent = free ? 0 : slotLevel;
        Metamagic paid = bending;
        heroes.change(player, now -> {
            Hero after = spent > 0 ? slots.spend(now, spent) : now;
            if (paid != null) {
                after = after.withSorceryPoints(after.sorceryPoints() - paid.cost(spell.level()));
            }
            return after;
        });
        if (paid != null) {
            heroes.of(id).ifPresent(now -> stance.disarm(player, now));
        }
        begin(turn, id, hero);
        showWait(player, bonus);
        effects.play(id, Cues.MAGIC);
        tell(player, spell, cast, paid, outcome.get());
        SurgeService wild = surges;
        if (wild != null && cast > 0) {
            heroes.of(id).ifPresent(now -> wild.afterCast(player, now, cast));
        }
    }

    /**
     * A spell from a scroll or a wand: the item's numbers, no slot, no metamagic — but it still takes the turn.
     *
     * @return whether it went off, so the item knows to spend itself
     */
    public boolean castFromItem(Player player, Hero hero, Spell spell, int slotLevel, int dc, int attack) {
        UUID id = player.getUniqueId();
        if (!settings.worksIn(player.getWorld().getName())) {
            refuse(player, "dnd.stance.not-here");
            return false;
        }
        if (conditions.has(id, Condition.HELD)) {
            refuse(player, "dnd.cast.held");
            return false;
        }
        Cooldowns<UUID> turn = spell.bonusAction() ? bonusActions : actions;
        if (!ready(player, turn)) {
            return false;
        }
        Optional<SpellService.Outcome> outcome = spells.perform(new SpellService.Cast(player, hero, spell,
                Math.max(spell.level(), slotLevel), null, SpellService.Extras.fromItem(dc, attack)));
        if (outcome.isEmpty()) {
            effects.play(id, Cues.NO);
            return false;
        }
        begin(turn, id, hero);
        effects.play(id, Cues.MAGIC);
        tell(player, spell, Math.max(spell.level(), slotLevel), null, outcome.get());
        return true;
    }

    /** Starts a wait; Thief's Reflexes takes a quarter off it. */
    private void begin(Cooldowns<UUID> turn, UUID id, Hero hero) {
        if (Feature.THIEFS_REFLEXES.has(hero)) {
            long full = turn == bonusActions ? settings.bonusActionMillis() : settings.actionMillis();
            turn.forget(id);
            turn.restore(id, System.currentTimeMillis() - full / 4);
        } else {
            turn.start(id);
        }
    }

    /** Every wait is over at once: Action Surge, and a wild surge. */
    public void endWaits(UUID player) {
        actions.forget(player);
        bonusActions.forget(player);
        featureWaits.values().forEach(each -> each.forget(player));
    }

    private boolean ready(Player player, Cooldowns<UUID> turn) {
        UUID id = player.getUniqueId();
        if (turn.isReady(id)) {
            return true;
        }
        effects.play(id, Cues.COOLDOWN);
        long millis = turn.remaining(id).orElse(Duration.ZERO).toMillis();
        actionBars.show(id, "dnd-cast", messages.get(turn == bonusActions ? "dnd.cast.wait-bonus" : "dnd.cast.wait",
                        "seconds", String.format(java.util.Locale.ROOT, "%.1f", millis / 1000.0)),
                Duration.ofMillis(800), ActionBarPriority.NORMAL);
        return false;
    }

    /** The grey sweep over the hotbar that shows the wait. */
    private void showWait(Player player, boolean bonus) {
        int ticks = (int) ((bonus ? settings.bonusActionMillis() : settings.actionMillis()) / 50);
        if (ticks <= 0 || !stance.isIn(player.getUniqueId())) {
            return;
        }
        for (int slot = 0; slot < Hero.LOADOUT_SIZE; slot++) {
            ItemStack icon = player.getInventory().getItem(slot);
            if (icon != null && !icon.isEmpty()) {
                player.setCooldown(icon.getType(), ticks);
            }
        }
    }

    private void tell(Player player, Spell spell, int slotLevel, Metamagic paid, SpellService.Outcome outcome) {
        String slot = slotLevel <= 0 ? "cantrip" : IconForge.ordinal(slotLevel) + " slot";
        String bent = paid == null ? "" : paid.title();
        String key;
        if (spell.harmful()) {
            key = "dnd.cast.harm";
        } else if (spell.heals()) {
            key = "dnd.cast.heal";
        } else {
            key = "dnd.cast.done";
        }
        actionBars.show(player.getUniqueId(), "dnd-cast", messages.get(key, "spell", spell.title(), "slot", slot,
                        "hit", String.valueOf(outcome.hit()), "saved", String.valueOf(outcome.saved()),
                        "missed", String.valueOf(outcome.missed()),
                        "damage", String.valueOf(Math.round(outcome.damage())),
                        "healed", String.valueOf(Math.round(outcome.healed()))),
                Duration.ofSeconds(3), ActionBarPriority.NORMAL);
        if (paid != null) {
            messages.send(player, "dnd.metamagic.used", "metamagic", bent, "spell", spell.title());
        }
    }

    private void refuse(Player player, String key, Object... values) {
        messages.send(player, key, values);
        effects.play(player.getUniqueId(), Cues.NO);
    }

    public void forget(UUID player) {
        actions.forget(player);
        bonusActions.forget(player);
        featureWaits.values().forEach(wait -> wait.forget(player));
    }
}
