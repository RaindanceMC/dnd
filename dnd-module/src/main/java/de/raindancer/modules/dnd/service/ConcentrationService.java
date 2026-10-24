package de.raindancer.modules.dnd.service;

import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.Ability;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.Roll;
import de.raindancer.modules.dnd.model.Spell;
import de.raindancer.modules.dnd.rules.CheckRule;
import de.raindancer.modules.dnd.rules.ConcentrationRule;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;

/**
 * One concentration spell per caster: a new one ends the old, it runs out, and a hit can break it with a failed
 * Constitution save. Ending it runs whatever the spell left to undo.
 */
public final class ConcentrationService implements IDndService {

    /** Spirit Guardians and its kind act this often, in milliseconds. */
    public static final long PULSE_MILLIS = 2_000;

    /** A spell being concentrated on. {@code undo} takes back what it gave; it is run once. */
    public record Focus(Spell spell, int slotLevel, long endsAt, Runnable undo, long[] lastPulse) {
    }

    private final Messages messages;
    private final Effects effects;
    private final CheckRule checks;
    private final ConcentrationRule rule;
    private final RandomGenerator random;
    private final LongSupplier clock;
    private final Map<UUID, Focus> focus = new ConcurrentHashMap<>();
    private volatile BiConsumer<Player, Focus> pulse = (player, held) -> { };

    public ConcentrationService(Messages messages, Effects effects, CheckRule checks, ConcentrationRule rule,
                                RandomGenerator random, LongSupplier clock) {
        this.messages = messages;
        this.effects = effects;
        this.checks = checks;
        this.rule = rule;
        this.random = random;
        this.clock = clock;
    }

    @Override
    public void settings(DndSettings settings) {
        // Concentration follows the rules, not the settings.
    }

    /** What an aura does every {@link #PULSE_MILLIS}. */
    public void onPulse(BiConsumer<Player, Focus> action) {
        this.pulse = action == null ? (player, held) -> { } : action;
    }

    public Optional<Focus> on(UUID caster) {
        return Optional.ofNullable(focus.get(caster));
    }

    public void start(Player caster, Spell spell, int slotLevel, long millis, Runnable undo) {
        end(caster, null);
        focus.put(caster.getUniqueId(), new Focus(spell, slotLevel, clock.getAsLong() + millis,
                undo == null ? () -> { } : undo, new long[]{clock.getAsLong()}));
    }

    /**
     * Ends it, running the undo.
     *
     * @param reasonKey what the caster is told, or null to say nothing
     */
    public boolean end(Player caster, String reasonKey) {
        Focus ended = focus.remove(caster.getUniqueId());
        if (ended == null) {
            return false;
        }
        ended.undo().run();
        if (reasonKey != null) {
            messages.send(caster, reasonKey, "spell", ended.spell().title());
            effects.play(caster.getUniqueId(), Cues.WARN);
        }
        return true;
    }

    /** A hit on a concentrating caster: Constitution save against half the damage, at least 10. */
    public void hit(Player caster, Hero hero, double damage) {
        Focus held = focus.get(caster.getUniqueId());
        if (held == null || damage <= 0) {
            return;
        }
        int dc = rule.dc(damage);
        Roll roll = checks.save(checks.d20(random, false), checks.saveBonus(hero, Ability.CONSTITUTION), dc);
        if (!roll.succeeded()) {
            end(caster, "dnd.concentration.broken");
        }
    }

    /** Ends what ran out, and pulses what pulses. On the caster's thread. */
    public void tick(Player caster) {
        Focus held = focus.get(caster.getUniqueId());
        if (held == null) {
            return;
        }
        long now = clock.getAsLong();
        if (now >= held.endsAt()) {
            end(caster, "dnd.concentration.ended");
            return;
        }
        if (now - held.lastPulse()[0] >= PULSE_MILLIS) {
            held.lastPulse()[0] = now;
            pulse.accept(caster, held);
        }
    }

    public void forget(Player caster) {
        end(caster, null);
    }

    public int concentrating() {
        return focus.size();
    }
}
