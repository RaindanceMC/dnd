package de.raindancer.modules.skills.skill.building;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.world.build.Schematic;
import de.raindancer.modules.skills.store.SkillsDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BlueprintBookTest {

    @TempDir
    Path folder;

    private Database database;
    private BlueprintBook book;
    private final UUID ana = UUID.randomUUID();
    private final UUID ben = UUID.randomUUID();

    @BeforeEach
    void open() {
        database = Database.open(folder.resolve("dnd.db"), SkillsDatabase.SCHEMA, () -> false);
        book = new BlueprintBook(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    private static Blueprint hut(String name) {
        Schematic blocks = Schematic.of(2, 1, 2, 4_000, new String[]{"minecraft:stone", "minecraft:air",
                "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", "minecraft:dirt"});
        return new Blueprint(name, blocks, 1, 0, -2, 1_234L);
    }

    @Test
    @DisplayName("a blueprint comes back block for block, with where its maker stood")
    void roundTrip() {
        assertThat(book.save(ana, hut("hut"))).isTrue();
        Blueprint back = book.load(ana, "hut").orElseThrow();
        assertThat(back.anchorX()).isEqualTo(1);
        assertThat(back.anchorZ()).isEqualTo(-2);
        assertThat(back.savedAt()).isEqualTo(1_234L);
        assertThat(back.schematic().stateAt(0, 0, 1))
                .isEqualTo("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]");
        assertThat(back.schematic().stateAt(1, 0, 1)).isEqualTo("minecraft:dirt");
    }

    @Test
    @DisplayName("each player has their own, listed by name; saving a name again replaces it; deleting takes it out")
    void perPlayer() {
        book.save(ana, hut("hut"));
        book.save(ana, hut("barn"));
        book.save(ana, hut("hut"));
        assertThat(book.list(ana)).extracting(Blueprint.Summary::name).containsExactly("barn", "hut");
        assertThat(book.list(ben)).isEmpty();
        assertThat(book.load(ben, "hut")).isEmpty();
        assertThat(book.delete(ana, "barn")).isTrue();
        assertThat(book.delete(ana, "barn")).isFalse();
        assertThat(book.list(ana)).extracting(Blueprint.Summary::name).containsExactly("hut");
    }
}
