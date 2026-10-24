package de.raindancer.modules.dnd.model;

import java.util.ArrayList;
import java.util.List;

import static de.raindancer.modules.dnd.model.Surge.Kind.*;
import static de.raindancer.modules.dnd.model.Surge.Tone.BAD;
import static de.raindancer.modules.dnd.model.Surge.Tone.GOOD;
import static de.raindancer.modules.dnd.model.Surge.Tone.NEUTRAL;

/** The d100 wild magic surge table. Written for this plugin; every entry is its own. */
public final class WildSurgeTable {

    private static final List<Surge> TABLE = new ArrayList<>();

    static {
        add(FIREBALL_ON_SELF, "", 0, 3, BAD, "A fireball blooms around you, and you are standing in it.");
        add(SELF_EFFECT, "levitation", 6, 0, NEUTRAL, "You drift gently upward, whether you like it or not.");
        add(RAIN, "SHEEP", 6, 30, NEUTRAL, "It rains sheep.");
        add(SCALE, "", 50, 60, NEUTRAL, "You shrink to half your size for a minute.");
        add(SCALE, "", 150, 60, NEUTRAL, "You grow half again as tall for a minute.");
        add(TELEPORT, "", 12, 0, NEUTRAL, "The weave flings you somewhere nearby.");
        add(SORCERY_BACK, "", 0, 0, GOOD, "Raw magic floods back: every sorcery point returns.");
        add(HEAL_SELF, "", 2, 10, GOOD, "Warm light knits your wounds.");
        add(HURT_SELF, "", 1, 10, BAD, "The spell bites back.");
        add(CAST, "lightning_bolt", 0, 3, GOOD, "A stroke of lightning leaps from your eyes.");
        add(SELF_EFFECT, "invisibility", 30, 0, GOOD, "You fade from sight for half a minute.");
        add(NEARBY_EFFECT, "glowing", 60, 0, NEUTRAL, "Everything near you starts to glow, you included.");
        add(ILLUSION, "POPPY", 30, 5, NEUTRAL, "Poppies spring up all around you. They are not really there.");
        add(AURA, "NOTE", 60, 0, NEUTRAL, "Faint music follows you for a minute.");
        add(GRAVITY, "", 25, 20, NEUTRAL, "Gravity forgets about you for a while.");
        add(SUMMON, "CHICKEN", 4, 30, NEUTRAL, "Four startled chickens appear around you.");
        add(FREE_SPELLS, "", 30, 0, GOOD, "For half a minute, your spells cost no slots.");
        add(RESET_WAITS, "", 0, 0, GOOD, "Time hiccups, and every wait is over.");
        add(FOES_EFFECT, "slowness", 10, 2, GOOD, "Everything hostile around you wades through treacle.");
        add(ALLIES_EFFECT, "regeneration", 10, 1, GOOD, "A soothing wind heals everyone near you.");
        add(SELF_EFFECT, "slowness", 10, 1, BAD, "Your legs turn to lead.");
        add(SELF_EFFECT, "speed", 30, 2, GOOD, "You cannot stop running.");
        add(LAUNCH, "", 16, 0, NEUTRAL, "A gust throws you skyward.");
        add(FIREWORKS, "", 5, 0, NEUTRAL, "Fireworks burst all around you.");
        add(LIGHTNING_SHOW, "", 3, 0, NEUTRAL, "Harmless lightning crackles down around you.");
        add(GIFT, "CAKE", 1, 0, GOOD, "A cake appears in your arms. It looks delicious.");
        add(XP, "", 30, 0, GOOD, "Experience glitters on the ground around you.");
        add(SWAP, "", 12, 0, NEUTRAL, "You swap places with the nearest creature.");
        add(MAX_HEALTH, "", 10, 60, GOOD, "Vigour surges through you: more health for a minute.");
        add(MAX_HEALTH, "", -6, 60, BAD, "You feel frail for a minute.");
        add(SELF_EFFECT, "nausea", 8, 0, BAD, "The world spins.");
        add(SELF_EFFECT, "night_vision", 120, 0, GOOD, "Your eyes glow; darkness hides nothing from you.");
        add(SELF_EFFECT, "water_breathing", 300, 0, GOOD, "Gills! You can breathe water for five minutes.");
        add(SELF_EFFECT, "fire_resistance", 60, 0, GOOD, "Your skin turns cool as stone; fire cannot touch you.");
        add(SELF_EFFECT, "resistance", 30, 1, GOOD, "Your skin hardens like bark.");
        add(SELF_EFFECT, "weakness", 20, 0, BAD, "Your arms go limp.");
        add(CAST, "haste", 0, 3, GOOD, "Your own spell catches you: you are hasted.");
        add(CAST, "greater_invisibility", 0, 4, GOOD, "You flicker out of sight.");
        add(CAST, "magic_missile", 0, 3, GOOD, "Glowing darts spill from your fingertips.");
        add(CAST, "thunderwave", 0, 2, GOOD, "A clap of thunder rolls out from you.");
        add(SUMMON, "BAT", 10, 20, NEUTRAL, "A cloud of bats bursts out of your sleeves.");
        add(SUMMON, "FROG", 3, 30, NEUTRAL, "Frogs. Three of them. Nobody knows why.");
        add(SUMMON, "WOLF", 2, 60, GOOD, "Two spectral wolves join you for a minute.");
        add(SUMMON, "VEX", 2, 20, BAD, "Mischievous spirits tear free from the weave, and they are not friendly.");
        add(SUMMON, "SILVERFISH", 3, 20, BAD, "Silverfish crawl out of your pockets.");
        add(SUMMON, "ALLAY", 1, 60, NEUTRAL, "A tiny blue spirit dances around you.");
        add(ILLUSION, "SNOW", 30, 6, NEUTRAL, "Snow piles up around you, cold and entirely imaginary.");
        add(AURA, "HEART", 30, 0, NEUTRAL, "Little hearts float off you.");
        add(AURA, "SOUL_FIRE_FLAME", 30, 0, NEUTRAL, "Blue flames dance on your shoulders, harmlessly.");
        add(OMEN, "entity.witch.celebrate", 0, 0, NEUTRAL, "You hear distant, delighted laughter.");
        add(SELF_EFFECT, "glowing", 60, 0, NEUTRAL, "You glow like a lantern for a minute.");
        add(FOES_EFFECT, "glowing", 60, 0, GOOD, "Every enemy around you is outlined in light.");
        add(FOES_EFFECT, "levitation", 4, 0, GOOD, "Your enemies drift helplessly into the air.");
        add(NEARBY_EFFECT, "levitation", 3, 0, NEUTRAL, "Everyone near you, you included, floats upward.");
        add(SELF_EFFECT, "slow_falling", 60, 0, NEUTRAL, "You fall like a feather for a minute.");
        add(SELF_EFFECT, "jump_boost", 30, 3, NEUTRAL, "Your legs turn into springs.");
        add(HEAL_SELF, "", 4, 8, GOOD, "A rush of life fills you.");
        add(HURT_SELF, "", 2, 6, BAD, "Wild sparks scorch your hands.");
        add(SLOT_BACK, "", 0, 0, GOOD, "A spent spell slot hums back to life.");
        add(TELEPORT, "", 30, 0, NEUTRAL, "You blink far away.");
        add(LAUNCH, "", 22, 0, NEUTRAL, "You are hurled high into the sky, and drift down on the wind.");
        add(GRAVITY, "", 200, 20, BAD, "You feel twice as heavy.");
        add(SCALE, "", 25, 30, NEUTRAL, "You shrink to the size of a cat.");
        add(SCALE, "", 200, 30, NEUTRAL, "You tower over everything.");
        add(CAST, "fireball", 0, 3, GOOD, "A fireball, aimed true, leaves your hands.");
        add(CAST, "ice_storm", 0, 4, GOOD, "Hail hammers down where you are looking.");
        add(CAST, "shatter", 0, 2, GOOD, "A deafening ring bursts where you are looking.");
        add(CAST, "hold_person", 0, 2, GOOD, "The weave reaches out and holds a stranger still.");
        add(RANDOM_SPELL, "", 0, 3, GOOD, "A spell you did not mean to cast leaps out of you.");
        add(RANDOM_SPELL, "", 0, 5, GOOD, "Something big leaves your fingertips.");
        add(FOES_EFFECT, "weakness", 20, 1, GOOD, "Your enemies' arms go weak.");
        add(FOES_EFFECT, "blindness", 6, 0, GOOD, "A flash of light dazzles everything hostile around you.");
        add(NEARBY_EFFECT, "blindness", 4, 0, BAD, "A flash of light dazzles everyone near you, you too.");
        add(ALLIES_EFFECT, "absorption", 60, 1, GOOD, "A shimmering shield wraps everyone near you.");
        add(ALLIES_EFFECT, "speed", 30, 1, GOOD, "A tailwind for everybody nearby.");
        add(SELF_EFFECT, "hunger", 30, 2, BAD, "You are suddenly ravenous.");
        add(SELF_EFFECT, "saturation", 1, 5, GOOD, "You feel as if you had just eaten a feast.");
        add(SELF_EFFECT, "conduit_power", 120, 0, GOOD, "The sea's blessing fills you.");
        add(SELF_EFFECT, "dolphins_grace", 60, 0, NEUTRAL, "You swim like a dolphin for a minute.");
        add(SELF_EFFECT, "luck", 300, 0, GOOD, "Fortune smiles on you.");
        add(SELF_EFFECT, "unluck", 300, 0, BAD, "A black cat crosses your path. Somewhere.");
        add(SELF_EFFECT, "darkness", 6, 0, BAD, "Shadows close in around you.");
        add(SELF_EFFECT, "infested", 30, 0, BAD, "Something crawls under your skin.");
        add(SELF_EFFECT, "oozing", 30, 0, BAD, "You feel distinctly slimy.");
        add(SELF_EFFECT, "weaving", 30, 0, NEUTRAL, "Threads of cobweb trail from your fingers.");
        add(SELF_EFFECT, "wind_charged", 30, 0, NEUTRAL, "Wind gathers in your chest, waiting to burst out.");
        add(GIFT, "GOLDEN_APPLE", 1, 0, GOOD, "A golden apple drops into your hand.");
        add(GIFT, "EMERALD", 2, 0, GOOD, "Two emeralds tumble out of thin air.");
        add(GIFT, "COOKIE", 8, 0, GOOD, "A shower of cookies.");
        add(FIREWORKS, "", 12, 0, NEUTRAL, "A full fireworks show, just for you.");
        add(LIGHTNING_SHOW, "", 1, 0, NEUTRAL, "Thunder answers your call.");
        add(ILLUSION, "AZURE_BLUET", 30, 7, NEUTRAL, "A meadow blooms around you, then fades.");
        add(ILLUSION, "COBWEB", 10, 4, NEUTRAL, "Cobwebs fill the air. They are only illusions.");
        add(OMEN, "entity.cow.ambient", 0, 0, NEUTRAL, "Every cow for a mile turns to look at you.");
        add(SWAP, "", 24, 0, NEUTRAL, "Space folds: you trade places with something farther away.");
        add(MAX_HEALTH, "", 20, 30, GOOD, "You swell with vitality.");
        add(SUMMON, "IRON_GOLEM", 1, 60, GOOD, "An iron guardian answers your call for a minute.");
        add(SUMMON, "PHANTOM", 2, 20, BAD, "Phantoms circle down out of nowhere.");
        add(SELF_EFFECT, "haste", 60, 1, GOOD, "Your hands blur: you dig and swing far faster for a minute.");
        add(JACKPOT, "", 0, 0, GOOD, "Everything at once: full health, every sorcery point, and a minute of free spells.");
    }

    private WildSurgeTable() {
    }

    private static void add(Surge.Kind kind, String what, int a, int b, Surge.Tone tone, String text) {
        TABLE.add(new Surge(TABLE.size() + 1, kind, what, a, b, tone, text));
    }

    public static int size() {
        return TABLE.size();
    }

    /** The entry for a d100 roll, 1 to 100. */
    public static Surge entry(int roll) {
        return TABLE.get(Math.max(1, Math.min(TABLE.size(), roll)) - 1);
    }

    public static List<Surge> all() {
        return List.copyOf(TABLE);
    }
}
