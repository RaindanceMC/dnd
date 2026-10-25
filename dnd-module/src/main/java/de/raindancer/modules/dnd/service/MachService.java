package de.raindancer.modules.dnd.service;

import de.raindancer.core.ui.actionbar.ActionBarPriority;
import de.raindancer.core.ui.actionbar.ActionBars;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.dnd.AdventurerAdvancements;
import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.rules.MachRule;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The Potion of Mach Fuck in somebody: while it works and they glide, they are flung along where they look every tick,
 * and steer by looking. Crashes and falls are forgiven until a little after it wears off. Runs on the player's own
 * thread, once a tick from {@link #tick}.
 */
public final class MachService implements IDndService {

    /** A move further than this in a tick is a teleport, not flying; it does not count towards the distance. */
    private static final double TELEPORT = 200;
    private static final String BAR = "dnd-mach";

    private final Messages messages;
    private final ActionBars actionBars;
    private final Effects effects;
    private final AdventurerAdvancements advancements;
    private final MachRule rule;
    private final LongSupplier clock;
    private final Map<UUID, Flight> flights = new ConcurrentHashMap<>();
    private volatile DndSettings settings = DndSettings.DEFAULTS;

    private static final class Flight {
        final long drankAt;
        Location last;
        double flown;
        int ticks;
        boolean over;

        Flight(long drankAt) {
            this.drankAt = drankAt;
        }
    }

    public MachService(Messages messages, ActionBars actionBars, Effects effects, AdventurerAdvancements advancements,
                       MachRule rule, LongSupplier clock) {
        this.messages = messages;
        this.actionBars = actionBars;
        this.effects = effects;
        this.advancements = advancements;
        this.rule = rule;
        this.clock = clock;
    }

    @Override
    public void settings(DndSettings updated) {
        this.settings = updated == null ? DndSettings.DEFAULTS : updated;
    }

    public void drank(Player player) {
        flights.put(player.getUniqueId(), new Flight(clock.getAsLong()));
        messages.send(player, "dnd.item.mach", "seconds", String.valueOf(settings.machSeconds()),
                "speed", String.valueOf(settings.machBlocksPerSecond()));
        effects.play(player.getUniqueId(), Cues.ABILITY);
        actionBars.countdown(player.getUniqueId(), BAR, java.time.Duration.ofSeconds(settings.machSeconds()),
                ActionBarPriority.HIGH, left -> messages.get("dnd.item.mach-left",
                        "seconds", String.valueOf((left + 999) / 1000)));
    }

    /** The players with the potion in them, or just coming down from it. */
    public Set<UUID> active() {
        return flights.keySet();
    }

    /** The players whose potion still works, not just forgives. */
    public Set<UUID> working() {
        long now = clock.getAsLong();
        int seconds = settings.machSeconds();
        Set<UUID> found = new java.util.HashSet<>();
        flights.forEach((id, flight) -> {
            if (rule.working(flight.drankAt, now, seconds)) {
                found.add(id);
            }
        });
        return found;
    }

    public void tick(Player player) {
        Flight flight = flights.get(player.getUniqueId());
        if (flight == null) {
            return;
        }
        long now = clock.getAsLong();
        int seconds = settings.machSeconds();
        if (!player.isOnline() || player.isDead() || !rule.shielded(flight.drankAt, now, seconds)) {
            flights.remove(player.getUniqueId());
            return;
        }
        if (!rule.working(flight.drankAt, now, seconds)) {
            if (!flight.over) {
                flight.over = true;
                messages.send(player, "dnd.item.mach-over");
            }
            return;
        }
        Location here = player.getLocation();
        if (flight.last != null && flight.last.getWorld() == here.getWorld()) {
            double moved = flight.last.distance(here);
            if (moved < TELEPORT) {
                flight.flown += moved;
            }
        }
        flight.last = here;
        if (!player.isGliding()) {
            return;
        }
        Vector look = here.getDirection();
        double[] velocity = rule.velocity(new double[]{look.getX(), look.getY(), look.getZ()},
                settings.machBlocksPerSecond());
        player.setVelocity(new Vector(velocity[0], velocity[1], velocity[2]));
        if (flight.ticks++ % 10 == 0) {
            player.getWorld().spawnParticle(Particle.EXPLOSION, here, 1);
            advancements.machFlown(player.getUniqueId(), flight.flown);
        }
        player.getWorld().spawnParticle(Particle.CLOUD, here, 2, 0.2, 0.2, 0.2, 0.01);
    }

    /** Whether a wall or the ground should not hurt now: the potion works, or wore off only just. */
    public boolean shielded(Player player) {
        Flight flight = flights.get(player.getUniqueId());
        return flight != null && rule.shielded(flight.drankAt, clock.getAsLong(), settings.machSeconds());
    }

    public void forget(UUID player) {
        if (flights.remove(player) != null) {
            actionBars.clear(player, BAR);
        }
    }
}
