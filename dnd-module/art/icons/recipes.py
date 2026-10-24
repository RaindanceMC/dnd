"""Which glyph, in which colours, every icon is. Paths are texture paths under rainsadventurers:item/."""

PALETTES = {
    "fire": ("#9c2a0a", "#e0561a", "#f7a23b", "#ffe7a1"),
    "cold": ("#2c5f8a", "#5aa7d6", "#a9e4ff", "#f2fdff"),
    "lightning": ("#7a6a10", "#d8c02a", "#fff066", "#ffffff"),
    "thunder": ("#3d4a6b", "#7385b3", "#b4c3e8", "#eef2ff"),
    "force": ("#4b2a87", "#8c5ad6", "#c8a3ff", "#f3e8ff"),
    "radiant": ("#9a6b12", "#e3b23a", "#ffe28a", "#fffbe0"),
    "necrotic": ("#2b3a2a", "#5d7a4a", "#9cbf73", "#e2f5c8"),
    "healing": ("#8e1b2c", "#d93a4f", "#ff8a96", "#ffe1e5"),
    "mist": ("#56606e", "#8f9aab", "#cfd7e3", "#ffffff"),
    "steel": ("#454b54", "#8a93a0", "#c7ced8", "#f4f6f9"),
    "wood": ("#4f3219", "#8a5a2b", "#c08a4e", "#ecc58f"),
    "leather": ("#4a2a17", "#7d4a2a", "#b0754a", "#dcaa7e"),
    "blood": ("#5a0f12", "#a3222a", "#e0505a", "#ffb3b8"),
    "shadow": ("#1f1b2b", "#45405e", "#7a74a0", "#c9c4ef"),
    "arcane": ("#1c3170", "#3d63c9", "#86a8ff", "#e1e9ff"),
    "chaos": ("#5a1666", "#a43bb8", "#e486f2", "#ffe0ff"),
    "dragon": ("#5c0d0d", "#b0261c", "#ef6a3c", "#ffc79a"),
    "gold": ("#7a5a0e", "#c99a22", "#f2d064", "#fff6cc"),
    "nature": ("#1f4a1c", "#3f8a33", "#86c95c", "#dff5c0"),
    "parchment": ("#7a6340", "#c9ae7c", "#f0e2bf", "#fffaf0"),
}

R = {}


def spell(name, glyph, main, second=None):
    R["spell/" + name] = (glyph, main, second or main)


def feature(name, glyph, main, second=None):
    R["feature/" + name] = (glyph, main, second or main)


spell("fire_bolt", "mote", "fire")
spell("ray_of_frost", "ray", "cold")
spell("shocking_grasp", "hand", "lightning")
spell("sacred_flame", "flame", "radiant")
spell("magic_missile", "darts", "force")
spell("burning_hands", "flame_hand", "fire")
spell("thunderwave", "waves", "thunder")
spell("shield", "barrier", "force", "arcane")
spell("mage_armor", "chestplate", "force")
spell("expeditious_retreat", "boot", "mist", "arcane")
spell("feather_fall", "feather", "mist")
spell("jump", "spring", "nature")
spell("cure_wounds", "heart_plus", "healing", "radiant")
spell("healing_word", "bubble", "healing", "parchment")
spell("guiding_bolt", "star", "radiant")
spell("bless", "halo", "radiant")
spell("shield_of_faith", "shield_cross", "radiant", "steel")
spell("inflict_wounds", "skull_hand", "necrotic")
spell("scorching_ray", "rays", "fire")
spell("misty_step", "steps", "shadow", "mist")
spell("shatter", "crack", "thunder")
spell("hold_person", "chains", "steel")
spell("invisibility", "ghost", "mist")
spell("lesser_restoration", "drop_plus", "cold", "radiant")
spell("aid", "shield_heart", "healing", "radiant")
spell("prayer_of_healing", "halo_heart", "healing", "radiant")
spell("fireball", "fireball", "fire")
spell("lightning_bolt", "bolt", "lightning")
spell("haste", "hourglass", "arcane", "gold")
spell("spirit_guardians", "orbit", "radiant")
spell("mass_healing_word", "hearts", "healing")
spell("ice_storm", "hail", "thunder", "cold")
spell("greater_invisibility", "eye_cross", "shadow")
spell("dimension_door", "door", "force", "shadow")
spell("death_ward", "ankh", "radiant")
spell("cone_of_cold", "cone", "cold")
spell("mass_cure_wounds", "heart", "healing")
spell("flame_strike", "pillar", "fire", "radiant")
spell("greater_restoration", "cross", "radiant")
spell("chain_lightning", "chain_bolt", "lightning")
spell("disintegrate", "dust", "nature")
spell("heal", "cross", "healing")
spell("harm", "heart_broken", "necrotic")
spell("finger_of_death", "finger", "necrotic")
spell("fire_storm", "tornado", "fire")
spell("regenerate", "sprout", "nature")
spell("sunburst", "sun", "radiant", "gold")
spell("holy_aura", "aura", "gold", "radiant")
spell("meteor_swarm", "meteor", "dragon", "fire")
spell("power_word_kill", "skull", "shadow")
spell("mass_heal", "gold_heart", "gold")

feature("second_wind", "swirl", "mist")
feature("fighting_style", "shield", "steel", "gold")
feature("action_surge", "bolt", "gold")
feature("extra_attack", "swords", "steel")
feature("indomitable", "mountain", "steel", "mist")
feature("rage", "rage", "blood")
feature("unarmored_defense", "fist", "leather", "blood")
feature("reckless_attack", "axe", "steel", "wood")
feature("fast_movement", "boot", "leather")
feature("sneak_attack", "dagger", "steel")
feature("cunning_dash", "dash", "shadow")
feature("cunning_hide", "mask", "shadow")
feature("cunning_disengage", "back_arrow", "shadow")
feature("uncanny_dodge", "swirl", "shadow")
feature("evasion", "feather", "shadow")
feature("arcane_recovery", "book_star", "arcane", "parchment")
feature("font_of_magic", "gem", "chaos", "force")
feature("metamagic", "rune", "chaos")
feature("turn_undead", "sun_skull", "radiant")
feature("preserve_life", "hand_heart", "radiant", "healing")
feature("improved_critical", "crit", "gold")
feature("remarkable_athlete", "runner", "gold")
feature("dueling", "sword", "steel")
feature("superior_critical", "crit", "radiant")
feature("survivor", "bandage", "healing", "parchment")
feature("frenzy", "axe", "blood", "wood")
feature("mindless_rage", "eye", "blood")
feature("intimidating_presence", "rage", "dragon")
feature("retaliation", "spikes", "steel", "blood")
feature("fast_hands", "hand", "shadow")
feature("second_story_work", "ladder", "wood")
feature("supreme_sneak", "hood", "shadow")
feature("use_magic_device", "scroll", "parchment")
feature("thiefs_reflexes", "hourglass", "shadow", "gold")
feature("sculpt_spells", "barrier", "fire", "arcane")
feature("potent_cantrip", "mote", "arcane")
feature("empowered_evocation", "fist", "fire")
feature("overchannel", "fireball", "arcane")
feature("wild_magic_surge", "chaos", "chaos")
feature("tides_of_chaos", "wave", "chaos", "cold")
feature("bend_luck", "dice", "chaos", "parchment")
feature("controlled_chaos", "chaos_box", "chaos")
feature("spell_bombardment", "bomb", "chaos", "fire")
feature("draconic_resilience", "scales", "dragon")
feature("elemental_affinity", "dragon_eye", "dragon", "fire")
feature("dragon_wings", "wings", "dragon", "fire")
feature("draconic_presence", "dragon_head", "dragon")
feature("disciple_of_life", "ankh", "gold")
feature("blessed_healer", "hearts", "radiant")
feature("divine_strike", "holy_sword", "radiant", "steel")
feature("supreme_healing", "gold_heart", "radiant")

R["class/fighter"] = ("swords", "steel", "steel")
R["class/barbarian"] = ("axe", "steel", "wood")
R["class/rogue"] = ("dagger", "shadow", "shadow")
R["class/wizard"] = ("staff_hat", "arcane", "arcane")
R["class/sorcerer"] = ("flame_hand", "chaos", "chaos")
R["class/cleric"] = ("holy_symbol", "gold", "radiant")

R["subclass/champion"] = ("laurel", "gold", "gold")
R["subclass/berserker"] = ("rage", "blood", "blood")
R["subclass/thief"] = ("coins", "gold", "parchment")
R["subclass/evocation"] = ("fireball", "fire", "fire")
R["subclass/wild_magic"] = ("chaos", "chaos", "chaos")
R["subclass/draconic"] = ("dragon_head", "dragon", "dragon")
R["subclass/life"] = ("ankh", "healing", "healing")

R["metamagic/quickened"] = ("hourglass", "chaos", "gold")
R["metamagic/twinned"] = ("twin", "chaos", "force")
R["metamagic/empowered"] = ("fist", "chaos", "fire")
R["metamagic/extended"] = ("clock", "chaos", "parchment")
R["metamagic/heightened"] = ("arrow_up", "chaos", "chaos")
R["metamagic/careful"] = ("barrier", "chaos", "steel")
R["metamagic/none"] = ("rune", "steel", "steel")

R["ui/empty"] = ("empty", "steel", "steel")
R["ui/sheet"] = ("book_star", "leather", "parchment")
R["ui/blank"] = ("blank", "steel", "steel")

RECIPES = R
