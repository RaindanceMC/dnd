package de.raindancer.modules.dnd;

import de.raindancer.core.content.advancement.AdvancementDef;
import de.raindancer.core.content.advancement.AdvancementFrame;
import de.raindancer.core.content.advancement.CustomAdvancements;
import org.bukkit.Material;

import java.util.UUID;

/** The adventurers' advancement tab. Every award is safe to repeat; callers report moments, this decides their worth. */
public final class AdventurerAdvancements {

    private static final String PLUGIN = "rainsadventurers";
    private static final String ROOT = PLUGIN + ":root";
    private static final double ORBIT_BLOCKS = 10_000;

    private final CustomAdvancements advancements;

    public AdventurerAdvancements(CustomAdvancements advancements) {
        this.advancements = advancements;
    }

    public void define() {
        advancements.define(AdvancementDef.builder(PLUGIN, "root").icon(Material.POTION)
                .title("<gold>Rain's Adventurers").description("Drink a magic potion")
                .background("minecraft:gui/advancements/backgrounds/adventure").build());
        child("hold_my_elytra", "root", Material.DRAGON_HEAD, "Hold My Elytra",
                "Brew a dragon's head into a Potion of Mach Fuck", AdvancementFrame.TASK);
        child("mach_fuck", "hold_my_elytra", Material.ELYTRA, "Mach Fuck",
                "Glide with a Potion of Mach Fuck in you", AdvancementFrame.GOAL);
        child("low_orbit", "mach_fuck", Material.FIREWORK_ROCKET, "Low Earth Orbit",
                "Fly " + (int) ORBIT_BLOCKS + " blocks on a single Potion of Mach Fuck", AdvancementFrame.CHALLENGE);
        child("lithobraking", "mach_fuck", Material.COBBLESTONE, "Lithobraking",
                "Hit a wall or the ground at Mach Fuck and live", AdvancementFrame.TASK);
        child("wasted_potential", "hold_my_elytra", Material.GLASS_BOTTLE, "Wasted Potential",
                "Let a Potion of Mach Fuck wear off without an elytra on", AdvancementFrame.TASK);
        child("double_shot", "mach_fuck", Material.HONEY_BOTTLE, "Double Shot",
                "Drink a second Potion of Mach Fuck while the first still works", AdvancementFrame.TASK);
        child("triple_batch", "hold_my_elytra", Material.BREWING_STAND, "Triple Batch",
                "Brew three Potions of Mach Fuck at once", AdvancementFrame.GOAL);
        child("hellfire_express", "mach_fuck", Material.NETHERRACK, "Hellfire Express",
                "Fly a Potion of Mach Fuck through the Nether", AdvancementFrame.GOAL);
    }

    public void drank(UUID player) {
        advancements.award(player, ROOT);
    }

    /** A brewing stand near the player brewed {@code potions} Potions of Mach Fuck at once. */
    public void brewedMach(UUID player, int potions) {
        advancements.award(player, ROOT);
        advancements.award(player, PLUGIN + ":hold_my_elytra");
        if (potions >= 3) {
            advancements.award(player, PLUGIN + ":triple_batch");
        }
    }

    public void lithobraked(UUID player) {
        advancements.award(player, PLUGIN + ":lithobraking");
    }

    public void wastedMach(UUID player) {
        advancements.award(player, PLUGIN + ":wasted_potential");
    }

    public void doubleShot(UUID player) {
        advancements.award(player, PLUGIN + ":double_shot");
    }

    public void machInTheNether(UUID player) {
        advancements.award(player, PLUGIN + ":hellfire_express");
    }

    /** Flying on the potion, {@code blocks} so far on this one. */
    public void machFlown(UUID player, double blocks) {
        advancements.award(player, PLUGIN + ":mach_fuck");
        if (blocks >= ORBIT_BLOCKS) {
            advancements.award(player, PLUGIN + ":low_orbit");
        }
    }

    private void child(String id, String parent, Material icon, String title, String description,
                       AdvancementFrame frame) {
        advancements.define(AdvancementDef.builder(PLUGIN, id).parent(parent).icon(icon).title(title)
                .description(description).frame(frame).steps(1).build());
    }
}
