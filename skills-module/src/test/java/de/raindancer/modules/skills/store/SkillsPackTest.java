package de.raindancer.modules.skills.store;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.raindancer.modules.skills.model.MagicItem;
import de.raindancer.modules.skills.model.Rarity;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillsPackTest {

    private static JsonObject json(String file) throws IOException {
        try (InputStream in = SkillsPack.class.getResourceAsStream(SkillsPack.ROOT + file)) {
            assertThat(in).as(file).isNotNull();
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** Follows an item definition to its model, through parents, and checks every texture it names is shipped. */
    private static void resolves(String itemModel) throws IOException {
        String definition = "assets/" + SkillsPack.NAMESPACE + "/items/" + itemModel + ".json";
        assertThat(SkillsPack.files()).as("item definition for " + itemModel).contains(definition);
        String model = json(definition).getAsJsonObject("model").get("model").getAsString();
        boolean shaped = false;
        while (model != null && model.startsWith(SkillsPack.NAMESPACE + ":")) {
            String file = "assets/" + SkillsPack.NAMESPACE + "/models/" + model.split(":")[1] + ".json";
            assertThat(SkillsPack.files()).as(itemModel + " -> " + model).contains(file);
            JsonObject json = json(file);
            if (json.has("textures")) {
                for (var texture : json.getAsJsonObject("textures").entrySet()) {
                    String value = texture.getValue().getAsString();
                    if (value.startsWith(SkillsPack.NAMESPACE + ":")) {
                        assertThat(SkillsPack.files()).as(itemModel + " texture " + value)
                                .contains("assets/" + SkillsPack.NAMESPACE + "/textures/" + value.split(":")[1] + ".png");
                    }
                }
            }
            shaped |= json.has("elements") || json.get("parent") != null
                    && json.get("parent").getAsString().matches("minecraft:item/(generated|handheld)");
            model = json.has("parent") ? json.get("parent").getAsString() : null;
        }
        assertThat(shaped).as(itemModel + " ends in a shape").isTrue();
    }

    @Test
    @DisplayName("every file the index names is in the jar, and the index is not empty")
    void everyFileShips() throws IOException {
        assertThat(SkillsPack.files()).hasSizeGreaterThan(100).contains("pack.mcmeta", "assets/rainsadventurers/font/tree.json",
                "assets/rainsadventurers/font/tree_scene.json");
        for (String file : SkillsPack.files()) {
            try (InputStream in = SkillsPack.class.getResourceAsStream(SkillsPack.ROOT + file)) {
                assertThat(in).as(file).isNotNull();
            }
        }
    }

    @Test
    @DisplayName("every magic item has its look: its own model, a scroll's seal by rarity, a weapon's runes")
    void magicItems() throws IOException {
        for (MagicItem item : MagicItem.values()) {
            if (item.isWeapon()) {
                continue;
            }
            resolves(SkillsPack.itemModel(item, Rarity.COMMON, item.material()).orElseThrow());
        }
        for (Rarity rarity : Rarity.values()) {
            resolves(SkillsPack.itemModel(MagicItem.SPELL_SCROLL, rarity, null).orElseThrow());
        }
        assertThat(SkillsPack.itemModel(MagicItem.SPELL_SCROLL, Rarity.LEGENDARY, null))
                .contains("magic/spell_scroll_legendary");
        for (String branch : java.util.List.of("vitality", "might", "marksman", "shadow", "arcana")) {
            assertThat(SkillsPack.treeBackdrop(branch)).as(branch).isNotNull();
        }
        for (MagicItem weapon : List.of(MagicItem.WEAPON_PLUS_ONE, MagicItem.WEAPON_PLUS_TWO, MagicItem.WEAPON_PLUS_THREE)) {
            for (Material base : List.of(Material.IRON_SWORD, Material.DIAMOND_AXE, Material.NETHERITE_SWORD)) {
                resolves(SkillsPack.itemModel(weapon, null, base).orElseThrow());
            }
            assertThat(SkillsPack.itemModel(weapon, null, Material.BOW)).as("a bow keeps drawing like a bow").isEmpty();
        }
        assertThat(SkillsPack.itemModel(MagicItem.WEAPON_PLUS_TWO, null, Material.DIAMOND_SWORD))
                .contains("magic/weapon/diamond_sword_plus_2");
        JsonObject flask = json("assets/rainsadventurers/models/item/magic/potion_of_healing.json");
        assertThat(flask.getAsJsonArray("elements")).hasSizeGreaterThan(10);
        assertThat(flask.getAsJsonObject("display").has("gui")).isTrue();
        assertThat(SkillsPack.files()).as("a potion's liquid bubbles")
                .contains("assets/rainsadventurers/textures/item/magic/potion_of_healing.png.mcmeta");
    }

    @Test
    @DisplayName("the pack takes in 26.2 and 26.3 clients")
    void format() throws IOException {
        JsonObject pack = json("pack.mcmeta").getAsJsonObject("pack");
        assertThat(pack.get("min_format").getAsInt()).isLessThanOrEqualTo(88);
        assertThat(pack.get("max_format").getAsInt()).isGreaterThanOrEqualTo(97);
    }
}
