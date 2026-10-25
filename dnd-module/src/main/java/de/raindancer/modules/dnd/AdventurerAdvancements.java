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
    }

    public void drank(UUID player) {
        advancements.award(player, ROOT);
    }

    public void brewedMach(UUID player) {
        advancements.award(player, ROOT);
        advancements.award(player, PLUGIN + ":hold_my_elytra");
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
