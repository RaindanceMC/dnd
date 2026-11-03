package de.raindancer.modules.dnd.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.modules.dnd.model.Hero;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HeroBookTest {

    @TempDir
    Path folder;

    private Database database;
    private HeroBook book;
    private final UUID ana = UUID.randomUUID();

    @BeforeEach
    void open() {
        database = Database.open(folder.resolve("dnd.db"), DndDatabase.SCHEMA, () -> false);
        book = new HeroBook(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    private Hero full() {
        return new Hero(ana, 7, 23_500, Map.of("toughness", 2, "brawler", 1), 123_456_789_000L);
    }

    @Test
    @DisplayName("nobody is NONE, not unreadable")
    void nobody() {
        assertThat(book.load(ana).status()).isEqualTo(HeroBook.Loaded.Status.NONE);
    }

    @Test
    @DisplayName("everything about a hero survives being written and read back")
    void roundTrip() {
        Hero hero = full();
        HeroBook.Saved saved = book.save(hero, 0);
        assertThat(saved.written()).isTrue();
        HeroBook.Loaded loaded = book.load(ana);
        assertThat(loaded.status()).isEqualTo(HeroBook.Loaded.Status.FOUND);
        assertThat(loaded.hero()).isEqualTo(hero);
        assertThat(loaded.rev()).isEqualTo(saved.rev());
    }

    @Test
    @DisplayName("a write based on an old revision loses instead of overwriting the newer one")
    void staleWritesLose() {
        Hero hero = full();
        long first = book.save(hero, 0).rev();
        HeroBook.Saved fromOtherServer = book.save(hero.withLevel(7, 30_000), first);
        assertThat(fromOtherServer.written()).isTrue();

        HeroBook.Saved stale = book.save(hero.withLevel(7, 23_600), first);
        assertThat(stale.status()).isEqualTo(HeroBook.Saved.Status.CONFLICT);
        assertThat(book.load(ana).hero().xp()).isEqualTo(30_000);
    }

    @Test
    @DisplayName("a second insert for the same player is a conflict, not a duplicate")
    void insertTwice() {
        assertThat(book.save(full(), 0).written()).isTrue();
        assertThat(book.save(full(), 0).status()).isEqualTo(HeroBook.Saved.Status.CONFLICT);
    }

    @Test
    @DisplayName("deleting takes the hero out entirely")
    void delete() {
        book.save(full(), 0);
        assertThat(book.delete(ana)).isTrue();
        assertThat(book.load(ana).status()).isEqualTo(HeroBook.Loaded.Status.NONE);
    }

    @Test
    @DisplayName("rubbish in the nodes column reads as nothing taken rather than failing")
    void forgivingDecoding() {
        assertThat(HeroBook.decodeNodes("toughness=2;nonsense;brawler=x;=3;dodge=1")).containsOnlyKeys("toughness", "dodge");
        assertThat(HeroBook.encodeNodes(Map.of("b", 1, "a", 2))).isEqualTo("a=2;b=1");
    }

    @Test
    @DisplayName("a hero written before the skill tree, with a class, loads at its level and experience, nothing taken")
    void fromBeforeTheTree() {
        database.write(connection -> {
            try (var insert = connection.prepareStatement("""
                    INSERT INTO hero (player, class, level, xp, scores, improvements, loadout, slots_spent,
                                      sorcery_points, hit_dice_spent, used, long_rest_at, rev, subclass)
                    VALUES (?, 'sorcerer', 9, 50000, '8,14,14,10,12,18', 2, 'fire_bolt,,shield', '0,2,1', 4, 3,
                            'rage=1', 5, 3, 'wild_magic')""")) {
                insert.setString(1, ana.toString());
                insert.executeUpdate();
            }
        });
        HeroBook.Loaded loaded = book.load(ana);
        assertThat(loaded.status()).isEqualTo(HeroBook.Loaded.Status.FOUND);
        assertThat(loaded.hero().level()).isEqualTo(9);
        assertThat(loaded.hero().xp()).isEqualTo(50_000);
        assertThat(loaded.hero().nodes()).isEmpty();
        assertThat(book.save(loaded.hero().withRank("toughness", 1), loaded.rev()).written()).isTrue();
        assertThat(book.load(ana).hero().rank("toughness")).isEqualTo(1);
    }

    @Test
    @DisplayName("the top is the most experienced first, a tie going to the higher level, cut to the size asked")
    void top() {
        UUID bo = UUID.randomUUID();
        UUID cy = UUID.randomUUID();
        book.save(Hero.fresh(ana).withLevel(3, 1_000), 0);
        book.save(Hero.fresh(bo).withLevel(7, 23_500), 0);
        book.save(Hero.fresh(cy).withLevel(2, 400), 0);
        assertThat(book.top(2)).extracting(HeroBook.Standing::player).containsExactly(bo, ana);
        assertThat(book.top(2).getFirst().level()).isEqualTo(7);
        assertThat(book.top(10)).hasSize(3);
    }
}
