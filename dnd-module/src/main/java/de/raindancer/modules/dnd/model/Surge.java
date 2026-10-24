package de.raindancer.modules.dnd.model;

/**
 * One entry of the wild magic surge table.
 *
 * @param what a spell id, a potion effect, an entity type, a material or a particle — whatever the kind needs
 * @param a    the kind's first number: seconds, a count, blocks, a factor in percent
 * @param b    the kind's second number: an amplifier, a radius, a slot level, seconds — see each kind
 */
public record Surge(int number, Kind kind, String what, int a, int b, Tone tone, String text) {

    /** How the surge treats whoever set it off. Controlled Chaos keeps the kinder of two. */
    public enum Tone {
        GOOD, NEUTRAL, BAD
    }

    public enum Kind {
        /** A fireball centred on the caster, who is caught in it too. */
        FIREBALL_ON_SELF,
        /** A spell, free, at whatever the caster looks at. {@code what} the spell, {@code b} the slot level. */
        CAST,
        /** A random spell from the caster's own list, free, from a slot of {@code b} or lower. */
        RANDOM_SPELL,
        /** A potion effect on the caster: {@code a} seconds at amplifier {@code b}. */
        SELF_EFFECT,
        /** The same on every creature near the caster, the caster included. */
        NEARBY_EFFECT,
        /** The same on hostile creatures near the caster. */
        FOES_EFFECT,
        /** The same on players near the caster, the caster included. */
        ALLIES_EFFECT,
        /** The caster's size: {@code a} percent for {@code b} seconds. */
        SCALE,
        /** The caster's weight: {@code a} percent gravity for {@code b} seconds. */
        GRAVITY,
        /** Somewhere safe up to {@code a} blocks away. */
        TELEPORT,
        /** Thrown up into the air. */
        LAUNCH,
        /** {@code a} creatures of type {@code what} for {@code b} seconds; HOSTILE, FRIENDLY or PASSIVE by tone. */
        SUMMON,
        /** {@code a} creatures falling from above for {@code b} seconds. */
        RAIN,
        HEAL_SELF,
        HURT_SELF,
        SORCERY_BACK,
        SLOT_BACK,
        /** Spells cost no slot for {@code a} seconds. */
        FREE_SPELLS,
        RESET_WAITS,
        /** Illusory blocks of {@code what} within {@code b} blocks for {@code a} seconds — never real ones. */
        ILLUSION,
        /** Particles of {@code what} trailing the caster for {@code a} seconds. */
        AURA,
        FIREWORKS,
        LIGHTNING_SHOW,
        /** {@code a} of {@code what} appear in the caster's inventory. */
        GIFT,
        /** {@code a} experience points spill out. */
        XP,
        /** The caster swaps places with the nearest creature within {@code a} blocks. */
        SWAP,
        /** {@code a} more (or fewer) maximum health for {@code b} seconds. */
        MAX_HEALTH,
        /** Nothing but a sound and a sentence. */
        OMEN,
        /** Full health, every sorcery point, and free spells for a minute. */
        JACKPOT
    }
}
