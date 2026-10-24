package de.raindancer.modules.dnd.util;

import de.raindancer.core.platform.util.Scheduling;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Touching a creature that may not be the caller's: on its own thread under Folia, at once on Paper. */
public final class Living {

    private Living() {
    }

    public static void onTarget(Plugin plugin, LivingEntity target, Runnable action) {
        if (Scheduling.isFolia()) {
            Scheduling.entity(plugin, target, action);
        } else {
            action.run();
        }
    }

    public static void give(LivingEntity target, PotionEffectType type, int seconds, int amplifier) {
        target.addPotionEffect(new PotionEffect(type, Math.max(1, seconds) * 20, amplifier, false, true, true));
    }

    /** Takes back an effect this module gave — not one of the same kind that somebody drank. */
    public static void takeBack(LivingEntity target, PotionEffectType type, int amplifier) {
        PotionEffect current = target.getPotionEffect(type);
        if (current != null && current.getAmplifier() == amplifier) {
            target.removePotionEffect(type);
        }
    }
}
