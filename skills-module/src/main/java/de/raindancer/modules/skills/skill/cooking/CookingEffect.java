package de.raindancer.modules.skills.skill.cooking;

import de.raindancer.modules.skills.model.Effect;
import de.raindancer.modules.skills.skill.EffectText;

/** What a node of the Cooking tree does, per rank. */
public enum CookingEffect implements Effect {

    FILLING("cook_filling", "Food fills you %s%% more", 100),
    SERVINGS("cook_servings", "%s%% chance of an extra serving from a furnace or smoker", 70),
    SPEED("cook_speed", "Furnaces and smokers you work cook food %s%% faster", 90),
    FUEL("cook_fuel", "Fuel burns %s%% longer in furnaces and smokers you work", 100),
    WELL_FED("cook_well_fed", "A cooked meal blesses you for %s seconds", 90),
    IRON_STOMACH("cook_iron_stomach", "Iron Stomach: rotten flesh, raw chicken, spider eyes and pufferfish do not make you sick", 1),
    MASTER_CHEF("cook_master_chef", "Master Chef: food cooks in a moment in a furnace or smoker you work", 1),
    FEAST("cook_feast", "Feast: a meal you eat feeds the players around you too, half as much", 1);

    private final String id;
    private final String text;
    private final int cap;

    CookingEffect(String id, String text, int cap) {
        this.id = id;
        this.text = text;
        this.cap = cap;
    }

    @Override
    public String id() {
        return id;
    }

    /** The most all ranks together may add up to; a feat's is 1: had or not. */
    @Override
    public int cap() {
        return cap;
    }

    @Override
    public String describe(double value) {
        return EffectText.describe(text, value);
    }
}
