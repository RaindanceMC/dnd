package de.raindancer.modules.skills.skill;

import de.raindancer.core.ui.actionbar.ActionBarPriority;
import de.raindancer.modules.skills.SkillsServices;
import de.raindancer.modules.skills.model.Effect;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** What a player's trees add up to for one effect, in the worlds the skills work in; 0 for anybody else. */
public final class SkillValues {

    private final SkillsServices services;

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

    private Map<String, Double> of(UUID player) {
        return services.heroes().of(player).map(services.perks()::effects).orElse(Map.of());
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

    /** A short line over the hotbar, with a cue: something a skill just did for the player. */
    public void notice(Player player, String cue, String key, Object... placeholders) {
        services.effects().play(player.getUniqueId(), cue);
        services.core().actionBars().show(player.getUniqueId(), "dnd-skills", services.messages().get(key, placeholders),
                Duration.ofSeconds(2), ActionBarPriority.LOW);
    }

    /** How many times, for a percentage that may pass 100. */
    public int times(Player player, Effect effect) {
        return Chances.times(value(player, effect), ThreadLocalRandom.current().nextDouble());
    }
}
