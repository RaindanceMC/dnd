package de.raindancer.modules.skills.skill;

import de.raindancer.core.ui.actionbar.ActionBarPriority;
import de.raindancer.modules.skills.SkillsServices;
import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.model.Hero;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * What a player's trees add up to, for the listeners that work the effects: nothing outside the skills' worlds or
 * before the hero is read. Summed once per picture of a hero — a listener on movement asks many times a second.
 */
public final class SkillValues {

    private record Summed(Hero hero, Map<String, Double> values) {
    }

    private final SkillsServices services;
    private final Map<UUID, Summed> summed = new ConcurrentHashMap<>();

    public SkillValues(SkillsServices services) {
        this.services = services;
    }

    public SkillsServices services() {
        return services;
    }

    public Map<String, Double> of(Player player) {
        if (player == null || !services.config().worksIn(player.getWorld().getName())) {
            return Map.of();
        }
        return of(player.getUniqueId());
    }

    /** Wherever they are — for a furnace loaded somewhere, asked when it was loaded. */
    public Map<String, Double> of(UUID player) {
        Hero hero = services.heroes().of(player).orElse(null);
        if (hero == null) {
            summed.remove(player);
            return Map.of();
        }
        Summed known = summed.get(player);
        if (known != null && known.hero() == hero) {
            return known.values();
        }
        Map<String, Double> values = Map.copyOf(services.perks().effects(hero));
        summed.put(player, new Summed(hero, values));
        return values;
    }

    public double value(Player player, Effect effect) {
        return of(player).getOrDefault(effect.id(), 0.0);
    }

    public boolean has(Player player, Effect feat) {
        return value(player, feat) > 0;
    }

    /** A roll against the player's percentage of this effect. */
    public boolean roll(Player player, Effect effect) {
        double percent = value(player, effect);
        return percent > 0 && Chances.hit(percent, ThreadLocalRandom.current().nextDouble());
    }

    /** A percent chance coming up, from the given dice. */
    public boolean roll(Player player, Effect chance, RandomGenerator random) {
        return chance(value(player, chance), random);
    }

    public static boolean chance(double percent, RandomGenerator random) {
        return percent > 0 && random.nextDouble() * 100 < percent;
    }

    /** How many times, for a percentage that may pass 100. */
    public int times(Player player, Effect effect) {
        return Chances.times(value(player, effect), ThreadLocalRandom.current().nextDouble());
    }

    /** A short line over the hotbar, with a cue: something a skill just did for the player. */
    public void notice(Player player, String cue, String key, Object... placeholders) {
        services.effects().play(player.getUniqueId(), cue);
        services.core().actionBars().show(player.getUniqueId(), "dnd-skills", services.messages().get(key, placeholders),
                Duration.ofSeconds(2), ActionBarPriority.LOW);
    }

    public void forget(UUID player) {
        summed.remove(player);
    }
}
