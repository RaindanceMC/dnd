package de.raindancer.modules.skills.skill;

import de.raindancer.modules.skills.SkillsSettings;
import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.model.Hero;
import de.raindancer.modules.skills.rules.SkillRule;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * What a player's nodes add up to, for the listeners that work the effects: nothing outside the skills' worlds or
 * before the hero is read. Summed once per picture of a hero — a listener on movement asks many times a second.
 */
public final class SkillValues {

    private record Summed(Hero hero, Map<String, Double> values) {
    }

    private final Function<UUID, Optional<Hero>> heroes;
    private final SkillRule rule;
    private final Supplier<SkillsSettings> settings;
    private final Map<UUID, Summed> summed = new ConcurrentHashMap<>();

    public SkillValues(Function<UUID, Optional<Hero>> heroes, SkillRule rule, Supplier<SkillsSettings> settings) {
        this.heroes = heroes;
        this.rule = rule;
        this.settings = settings;
    }

    public Map<String, Double> of(Player player) {
        if (player == null || !settings.get().worksIn(player.getWorld().getName())) {
            return Map.of();
        }
        return of(player.getUniqueId());
    }

    /** Wherever they are — for a furnace loaded somewhere, asked when it was loaded. */
    public Map<String, Double> of(UUID player) {
        Optional<Hero> hero = heroes.apply(player);
        if (hero.isEmpty()) {
            summed.remove(player);
            return Map.of();
        }
        Summed known = summed.get(player);
        if (known != null && known.hero() == hero.get()) {
            return known.values();
        }
        Map<String, Double> values = Map.copyOf(rule.effects(hero.get()));
        summed.put(player, new Summed(hero.get(), values));
        return values;
    }

    public double value(Player player, Effect effect) {
        return of(player).getOrDefault(effect.id(), 0.0);
    }

    public boolean has(Player player, Effect feat) {
        return value(player, feat) > 0;
    }

    /** A percent chance coming up. */
    public boolean roll(Player player, Effect chance, RandomGenerator random) {
        return chance(value(player, chance), random);
    }

    public static boolean chance(double percent, RandomGenerator random) {
        return percent > 0 && random.nextDouble() * 100 < percent;
    }

    public void forget(UUID player) {
        summed.remove(player);
    }
}
