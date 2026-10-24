package de.raindancer.modules.dnd.store;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.HeroClass;
import de.raindancer.modules.dnd.model.MagicItem;
import de.raindancer.modules.dnd.model.Metamagic;
import de.raindancer.modules.dnd.model.Spell;
import de.raindancer.modules.dnd.model.Subclass;
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

class DndPackTest {

    private static JsonObject json(String file) throws IOException {
        try (InputStream in = DndPack.class.getResourceAsStream(DndPack.ROOT + file)) {
            assertThat(in).as(file).isNotNull();
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** Follows an item definition to its model, through parents, and checks every texture it names is shipped. */
    private static void resolves(String itemModel) throws IOException {
        String definition = "assets/" + DndPack.NAMESPACE + "/items/" + itemModel + ".json";
        assertThat(DndPack.files()).as("item definition for " + itemModel).contains(definition);
        String model = json(definition).getAsJsonObject("model").get("model").getAsString();
        boolean shaped = false;
        while (model != null && model.startsWith(DndPack.NAMESPACE + ":")) {
            String file = "assets/" + DndPack.NAMESPACE + "/models/" + model.split(":")[1] + ".json";
            assertThat(DndPack.files()).as(itemModel + " -> " + model).contains(file);
            JsonObject json = json(file);
            if (json.has("textures")) {
                for (var texture : json.getAsJsonObject("textures").entrySet()) {
                    String value = texture.getValue().getAsString();
                    if (value.startsWith(DndPack.NAMESPACE + ":")) {
                        assertThat(DndPack.files()).as(itemModel + " texture " + value)
                                .contains("assets/" + DndPack.NAMESPACE + "/textures/" + value.split(":")[1] + ".png");
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
        assertThat(DndPack.files()).hasSizeGreaterThan(300).contains("pack.mcmeta");
        for (String file : DndPack.files()) {
            try (InputStream in = DndPack.class.getResourceAsStream(DndPack.ROOT + file)) {
                assertThat(in).as(file).isNotNull();
            }
        }
    }

    @Test
    @DisplayName("every spell, feature, class, subclass and metamagic has an icon all the way down to its texture")
    void everyIconResolves() throws IOException {
        List<String> paths = new ArrayList<>();
        for (Spell spell : Spell.values()) {
            paths.add(DndPack.iconOf(spell));
        }
        for (Feature feature : Feature.values()) {
            paths.add(DndPack.iconOf(feature));
        }
        for (HeroClass heroClass : HeroClass.values()) {
            paths.add(DndPack.classIcon(heroClass));
        }
        for (Subclass subclass : Subclass.values()) {
            paths.add(DndPack.subclassIcon(subclass));
        }
        for (Metamagic metamagic : Metamagic.values()) {
            paths.add(DndPack.metamagicIcon(metamagic));
        }
        paths.add(DndPack.metamagicIcon(null));
        paths.add(DndPack.EMPTY);
        paths.add(DndPack.SHEET);
        for (String path : paths) {
            resolves(path);
        }
    }

    @Test
    @DisplayName("every magic item has its look: its own model, a scroll's seal by rarity, a weapon's runes")
    void magicItems() throws IOException {
        for (MagicItem item : MagicItem.values()) {
            if (item.isWeapon()) {
                continue;
            }
            resolves(DndPack.itemModel(item, Spell.FIREBALL, item.material()).orElseThrow());
        }
        for (Spell spell : Spell.values()) {
            resolves(DndPack.itemModel(MagicItem.SPELL_SCROLL, spell, null).orElseThrow());
        }
        assertThat(DndPack.itemModel(MagicItem.SPELL_SCROLL, Spell.METEOR_SWARM, null))
                .contains("magic/spell_scroll_legendary");
        for (MagicItem weapon : List.of(MagicItem.WEAPON_PLUS_ONE, MagicItem.WEAPON_PLUS_TWO, MagicItem.WEAPON_PLUS_THREE)) {
            for (Material base : List.of(Material.IRON_SWORD, Material.DIAMOND_AXE, Material.NETHERITE_SWORD)) {
                resolves(DndPack.itemModel(weapon, null, base).orElseThrow());
            }
            assertThat(DndPack.itemModel(weapon, null, Material.BOW)).as("a bow keeps drawing like a bow").isEmpty();
        }
        assertThat(DndPack.itemModel(MagicItem.WEAPON_PLUS_TWO, null, Material.DIAMOND_SWORD))
                .contains("magic/weapon/diamond_sword_plus_2");
        JsonObject flask = json("assets/rainsadventurers/models/item/magic/potion_of_healing.json");
        assertThat(flask.getAsJsonArray("elements")).hasSizeGreaterThan(10);
        assertThat(flask.getAsJsonObject("display").has("gui")).isTrue();
        assertThat(DndPack.files()).as("a potion's liquid bubbles")
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
