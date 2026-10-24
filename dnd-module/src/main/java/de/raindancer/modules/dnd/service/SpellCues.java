package de.raindancer.modules.dnd.service;

import de.raindancer.core.ui.effect.Effect;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.effect.ParticleCue;
import de.raindancer.core.ui.effect.SoundCue;
import de.raindancer.modules.dnd.DndSettings;
import de.raindancer.modules.dnd.model.DamageKind;
import org.bukkit.Location;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * What spells sound and look like, as cues an owner can rebind: {@code dnd:fire}, {@code dnd:cold} and so on, one
 * per kind of damage, plus the trail a ray leaves.
 */
public final class SpellCues implements IDndService {

    private static final Map<DamageKind, String> PARTICLE = new EnumMap<>(DamageKind.class);
    private static final Map<DamageKind, SoundCue> SOUND = new EnumMap<>(DamageKind.class);

    static {
        put(DamageKind.FIRE, "FLAME", "entity.blaze.shoot");
        put(DamageKind.COLD, "SNOWFLAKE", "block.powder_snow.break");
        put(DamageKind.LIGHTNING, "ELECTRIC_SPARK", "item.trident.thunder");
        put(DamageKind.THUNDER, "SONIC_BOOM", "entity.generic.explode");
        put(DamageKind.FORCE, "WITCH", "entity.illusioner.cast_spell");
        put(DamageKind.RADIANT, "END_ROD", "block.beacon.power_select");
        put(DamageKind.NECROTIC, "SOUL", "entity.wither.ambient");
        put(DamageKind.BLUDGEONING, "EXPLOSION", "entity.generic.explode");
        put(DamageKind.HEALING, "HEART", "entity.player.levelup");
        put(DamageKind.NONE, "ENCHANT", "entity.evoker.cast_spell");
    }

    private static void put(DamageKind kind, String particle, String sound) {
        PARTICLE.put(kind, particle);
        SOUND.put(kind, new SoundCue(sound, kind == DamageKind.LIGHTNING ? 0.5f : 0.8f, 1.0f));
    }

    private final Effects effects;

    public SpellCues(Effects effects) {
        this.effects = effects;
    }

    @Override
    public void settings(DndSettings settings) {
        // Cues are rebound through Core's own cue screens, not here.
    }

    public static String cue(DamageKind kind) {
        return "dnd:" + kind.name().toLowerCase(Locale.ROOT);
    }

    /** Defines the cues that are not defined yet; an owner's own binding is left alone. */
    public void define() {
        for (DamageKind kind : DamageKind.values()) {
            if (!effects.isDefined(cue(kind))) {
                effects.define(cue(kind), new Effect(SOUND.get(kind),
                        new ParticleCue(PARTICLE.get(kind), 14, 0.4, 0.5, 0.4, 0.02, null)));
            }
        }
    }

    /** The burst of this kind at a point. */
    public void at(Location where, DamageKind kind) {
        effects.playAt(where.getWorld().getName(), where.getX(), where.getY(), where.getZ(), cue(kind));
    }

    /** A filled circle of particles, for an area. */
    public void ring(Location centre, double radius, DamageKind kind) {
        String particle = PARTICLE.getOrDefault(kind, "ENCHANT");
        int points = (int) Math.max(12, Math.min(64, radius * 10));
        for (int point = 0; point < points; point++) {
            double angle = Math.PI * 2 * point / points;
            effects.playAt(centre.getWorld().getName(), centre.getX() + Math.cos(angle) * radius, centre.getY() + 0.3,
                    centre.getZ() + Math.sin(angle) * radius,
                    Effect.of(new ParticleCue(particle, 1, 0.05, 0.05, 0.05, 0.0, null)));
        }
        effects.playAt(centre.getWorld().getName(), centre.getX(), centre.getY() + 0.5, centre.getZ(),
                Effect.of(new ParticleCue(particle, (int) Math.min(120, radius * radius * 4), radius / 2, 0.6,
                        radius / 2, 0.02, null)));
    }

    /** A line of particles from one point to another, with the sound at its end. */
    public void ray(Location from, Location to, DamageKind kind) {
        String particle = PARTICLE.getOrDefault(kind, "ENCHANT");
        double length = from.distance(to);
        int steps = (int) Math.max(2, Math.min(120, length * 3));
        for (int step = 0; step <= steps; step++) {
            double along = step / (double) steps;
            effects.playAt(from.getWorld().getName(), from.getX() + (to.getX() - from.getX()) * along,
                    from.getY() + (to.getY() - from.getY()) * along, from.getZ() + (to.getZ() - from.getZ()) * along,
                    Effect.of(new ParticleCue(particle, 1, 0.02, 0.02, 0.02, 0.0, null)));
        }
        at(to, kind);
    }
}
