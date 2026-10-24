package de.raindancer.modules.dnd.store;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.raindancer.core.ui.menu.Backdrop;
import de.raindancer.modules.dnd.screen.ClassMenu;
import de.raindancer.modules.dnd.screen.ConfirmScreen;
import de.raindancer.modules.dnd.screen.FontOfMagicMenu;
import de.raindancer.modules.dnd.screen.LoadoutMenu;
import de.raindancer.modules.dnd.screen.SheetMenu;
import de.raindancer.modules.dnd.screen.SpellPickerMenu;
import de.raindancer.modules.dnd.screen.SubclassMenu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Every adventurers' menu is on parchment (the sheet on its own page), and every page it needs ships. */
class ParchmentTest {

    @Test
    @DisplayName("Every menu chooses its backdrop")
    void everyMenuChooses() throws NoSuchMethodException {
        for (Class<?> page : List.of(ClassMenu.class, ConfirmScreen.class, FontOfMagicMenu.class, LoadoutMenu.class,
                SheetMenu.class, SpellPickerMenu.class, SubclassMenu.class)) {
            assertThat(page.getDeclaredMethod("backdrop").getReturnType()).as(page.getSimpleName())
                    .isEqualTo(Backdrop.class);
        }
    }

    @Test
    @DisplayName("A parchment page for every window height, in the parchment font")
    void everyHeight() throws Exception {
        Map<Character, JsonObject> glyphs = new HashMap<>();
        try (InputStream in = DndPack.class.getResourceAsStream(DndPack.ROOT + "assets/rainsadventurers/font/parchment.json")) {
            assertThat(in).isNotNull();
            JsonObject font = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement p : font.getAsJsonArray("providers")) {
                JsonObject provider = p.getAsJsonObject();
                if (provider.has("chars")) {
                    glyphs.put(provider.getAsJsonArray("chars").get(0).getAsString().charAt(0), provider);
                }
            }
        }
        for (int rows = 1; rows <= 6; rows++) {
            Backdrop parchment = DndPack.parchment(rows);
            JsonObject provider = glyphs.get(parchment.glyphs().charAt(1));
            assertThat(provider).as("rows " + rows).isNotNull();
            String file = provider.get("file").getAsString().replace("rainsadventurers:", "assets/rainsadventurers/textures/");
            assertThat(DndPack.files()).contains(file);
            try (InputStream in = DndPack.class.getResourceAsStream(DndPack.ROOT + file)) {
                BufferedImage image = ImageIO.read(in);
                assertThat(image.getHeight()).isEqualTo(114 + 18 * rows).isEqualTo(provider.get("height").getAsInt());
            }
        }
        assertThat(DndPack.SHEET_BACKDROP.font().value()).isEqualTo("sheet");
        assertThat(DndPack.SHEET_BACKDROP.titleFont()).isEqualTo(Backdrop.TITLE_FONT);
        assertThat(DndPack.parchment(3).titleFont()).as("the storybook capitals are the adventurers' own")
                .isEqualTo(Backdrop.TITLE_FONT);
    }

    @Test
    @DisplayName("Without ability scores the sheet is painted without their row, in the same font")
    void simpleSheet() throws Exception {
        assertThat(DndPack.SHEET_SIMPLE_BACKDROP.font()).isEqualTo(DndPack.SHEET_BACKDROP.font());
        assertThat(DndPack.SHEET_SIMPLE_BACKDROP.glyphs()).isNotEqualTo(DndPack.SHEET_BACKDROP.glyphs());
        String glyph = String.valueOf(DndPack.SHEET_SIMPLE_BACKDROP.glyphs().charAt(1));
        try (InputStream in = DndPack.class.getResourceAsStream(DndPack.ROOT + "assets/rainsadventurers/font/sheet.json")) {
            JsonObject font = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean found = false;
            for (JsonElement p : font.getAsJsonArray("providers")) {
                JsonObject provider = p.getAsJsonObject();
                if (provider.has("chars") && provider.getAsJsonArray("chars").get(0).getAsString().equals(glyph)) {
                    String file = provider.get("file").getAsString().replace("rainsadventurers:", "assets/rainsadventurers/textures/");
                    assertThat(DndPack.files()).contains(file);
                    found = true;
                }
            }
            assertThat(found).isTrue();
        }
    }
}
