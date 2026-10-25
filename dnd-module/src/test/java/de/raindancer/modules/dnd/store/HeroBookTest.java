package de.raindancer.modules.dnd.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.modules.dnd.model.AbilityScores;
import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
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
        return Hero.fresh(ana, HeroClass.SORCERER, List.of("fire_bolt", "", "shield"))
                .withLevel(7, 23_500, 2)
                .withScores(new AbilityScores(8, 14, 14, 10, 12, 18), 1)
                .withSlotSpent(1, 2).withSlotSpent(3, -1)
                .withSorceryPoints(4).withHitDiceSpent(3)
                .withUsed(Feature.INDOMITABLE, 1)
                .withLongRestAt(123_456_789_000L)
                .withSubclass(de.raindancer.modules.dnd.model.Subclass.WILD_MAGIC);
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
        HeroBook.Saved fromOtherServer = book.save(hero.withXp(30_000), first);
        assertThat(fromOtherServer.written()).isTrue();

        HeroBook.Saved stale = book.save(hero.withXp(23_600), first);
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
    @DisplayName("rubbish in the text columns reads as nothing spent rather than failing")
    void forgivingDecoding() {
        assertThat(HeroBook.decodeSlots("1,x,,3")).containsExactly(1, 0, 0, 3);
        assertThat(HeroBook.decodeUsed("rage=2;nonsense=1;second_wind=x;broken")).containsEntry(Feature.RAGE, 2)
                .hasSize(1);
        assertThat(HeroBook.decodeLoadout("a,,b")).containsExactly("a", "", "b");
    }

    @Test
    @DisplayName("the top is the most experienced first, a tie going to the higher level, cut to the size asked")
    void top() {
        UUID bo = UUID.randomUUID();
        UUID cy = UUID.randomUUID();
        book.save(Hero.fresh(ana, HeroClass.SORCERER, List.of()).withLevel(3, 1_000, 0), 0);
        book.save(Hero.fresh(bo, HeroClass.SORCERER, List.of()).withLevel(7, 23_500, 0), 0);
        book.save(Hero.fresh(cy, HeroClass.SORCERER, List.of()).withLevel(2, 400, 0), 0);
        assertThat(book.top(2)).extracting(HeroBook.Standing::player).containsExactly(bo, ana);
        assertThat(book.top(2).getFirst().level()).isEqualTo(7);
        assertThat(book.top(2).getFirst().heroClass()).isEqualTo(HeroClass.SORCERER);
        assertThat(book.top(10)).hasSize(3);
    }
}
