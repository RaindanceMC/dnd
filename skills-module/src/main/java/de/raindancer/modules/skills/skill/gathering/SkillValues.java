package de.raindancer.modules.skills.skill.gathering;

import de.raindancer.modules.skills.SkillsServices;
import de.raindancer.modules.skills.model.Effect;
import org.bukkit.entity.Player;

import java.util.Map;

/** What a player's trees add up to, by effect id — nothing outside the skills' worlds or before the hero is read. */
public final class SkillValues {

    private final SkillsServices services;

    public SkillValues(SkillsServices services) {
        this.services = services;
    }

    public Map<String, Double> of(Player player) {
        if (!services.config().worksIn(player.getWorld().getName())) {
            return Map.of();
        }
        return services.heroes().of(player.getUniqueId()).map(services.perks()::effects).orElse(Map.of());
    }

    public static double get(Map<String, Double> values, Effect effect) {
        return values.getOrDefault(effect.id(), 0.0);
    }

    public static boolean has(Map<String, Double> values, Effect effect) {
        return get(values, effect) > 0;
    }
}
