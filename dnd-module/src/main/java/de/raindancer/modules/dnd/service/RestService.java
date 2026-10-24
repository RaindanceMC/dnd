package de.raindancer.modules.dnd.service;

import de.raindancer.core.ui.actionbar.ActionBarPriority;
import de.raindancer.core.ui.actionbar.ActionBars;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.Condition;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.rules.HealthRule;
import de.raindancer.modules.dnd.rules.RestRule;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Short rests — standing still a while, spending hit dice — and long rests, which are a night's sleep. */
public final class RestService implements IDndService {

    private static final double STILL = 0.75;

    private record Resting(Location at, long endsAt) {
    }

    private final Messages messages;
    private final Effects effects;
    private final ActionBars actionBars;
    private final HeroService heroes;
    private final BodyService body;
    private final ConditionService conditions;
    private final RestRule rests;
    private final HealthRule health;
    private final LongSupplier clock;
    private final Map<UUID, Resting> resting = new ConcurrentHashMap<>();

    private volatile DndSettings settings = DndSettings.DEFAULTS;

    public RestService(Messages messages, Effects effects, ActionBars actionBars, HeroService heroes,
                       BodyService body, ConditionService conditions, RestRule rests, HealthRule health,
                       LongSupplier clock) {
        this.messages = messages;
        this.effects = effects;
        this.actionBars = actionBars;
        this.heroes = heroes;
        this.body = body;
        this.conditions = conditions;
        this.rests = rests;
        this.health = health;
        this.clock = clock;
    }

    @Override
    public void settings(DndSettings updated) {
        this.settings = updated == null ? DndSettings.DEFAULTS : updated;
    }

    public boolean isResting(UUID player) {
        return resting.containsKey(player);
    }

    public void startShort(Player player) {
        if (heroes.of(player.getUniqueId()).isEmpty()) {
            messages.send(player, "dnd.no-class");
            return;
        }
        if (isResting(player.getUniqueId())) {
            messages.send(player, "dnd.rest.already");
            return;
        }
        int seconds = Math.max(1, settings.shortRestSeconds());
        resting.put(player.getUniqueId(), new Resting(player.getLocation(), clock.getAsLong() + seconds * 1000L));
        messages.send(player, "dnd.rest.short-started", "seconds", String.valueOf(seconds));
        effects.play(player.getUniqueId(), Cues.TELEPORT_WAIT);
        actionBars.countdown(player.getUniqueId(), "dnd-rest", Duration.ofSeconds(seconds), ActionBarPriority.HIGH,
                left -> messages.get("dnd.rest.short-bar", "seconds", String.valueOf((left + 999) / 1000)));
    }

    /** Once a second: moved, or done? */
    public void tick(Player player) {
        Resting rest = resting.get(player.getUniqueId());
        if (rest == null) {
            return;
        }
        Location now = player.getLocation();
        if (!now.getWorld().equals(rest.at().getWorld()) || now.distanceSquared(rest.at()) > STILL * STILL) {
            interrupt(player, "dnd.rest.moved");
            return;
        }
        if (clock.getAsLong() >= rest.endsAt()) {
            resting.remove(player.getUniqueId());
            finishShort(player);
        }
    }

    public void interrupt(Player player, String reasonKey) {
        if (resting.remove(player.getUniqueId()) != null) {
            actionBars.clear(player.getUniqueId(), "dnd-rest");
            messages.send(player, reasonKey);
            effects.play(player.getUniqueId(), Cues.NO);
        }
    }

    private void finishShort(Player player) {
        double max = TargetService.maxHealth(player);
        double[] healed = {0};
        int[] dice = {0};
        heroes.change(player, hero -> {
            RestRule.Spent spent = rests.spendHitDice(hero, max - player.getHealth(), health.perHitDie(hero));
            healed[0] = spent.healed();
            dice[0] = spent.dice();
            return rests.afterShortRest(hero).withHitDiceSpent(hero.hitDiceSpent() + spent.dice());
        });
        player.setHealth(Math.min(max, player.getHealth() + healed[0]));
        actionBars.clear(player.getUniqueId(), "dnd-rest");
        effects.play(player.getUniqueId(), Cues.HEAL);
        messages.send(player, "dnd.rest.short-done", "dice", String.valueOf(dice[0]),
                "health", String.valueOf(Math.round(healed[0])));
    }

    /** A night slept through. Refused, with the wait told, when the last one was too recent — unless forced. */
    public void longRest(Player player, boolean forced) {
        Optional<Hero> found = heroes.of(player.getUniqueId());
        if (found.isEmpty() || !forced && !settings.worksIn(player.getWorld().getName())) {
            return;
        }
        long now = clock.getAsLong();
        if (!forced && !rests.mayLongRest(found.get().longRestAt(), now, settings.longRestMinutes())) {
            long minutes = Math.max(1, rests.waitLeftMillis(found.get().longRestAt(), now,
                    settings.longRestMinutes()) / 60_000);
            messages.send(player, "dnd.rest.long-too-soon", "minutes", String.valueOf(minutes));
            return;
        }
        heroes.change(player, hero -> rests.afterLongRest(hero, now));
        body.endLoans(player);
        conditions.clear(player.getUniqueId(), Condition.WARDED);
        player.setHealth(TargetService.maxHealth(player));
        effects.play(player.getUniqueId(), Cues.REWARD);
        messages.send(player, "dnd.rest.long-done");
    }

    public void forget(UUID player) {
        resting.remove(player);
    }
}
