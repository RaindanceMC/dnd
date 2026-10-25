package de.raindancer.modules.dnd.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.modules.dnd.model.AbilityScores;
import de.raindancer.modules.dnd.model.Feature;
import de.raindancer.modules.dnd.model.Hero;
import de.raindancer.modules.dnd.model.HeroClass;
import de.raindancer.modules.dnd.model.Subclass;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Heroes in the database, one row each. Every write names the revision it was based on, so a server writing an
 * older picture of a hero — the one a player just left, still saving — loses rather than overwrites the newer one.
 * Off the server's threads only.
 */
public final class HeroBook {

    /** What a read found. An unreadable database is never taken for a player without a hero. */
    public record Loaded(Status status, Hero hero, long rev) {

        public enum Status {
            FOUND, NONE, UNREADABLE
        }

        static Loaded found(Hero hero, long rev) {
            return new Loaded(Status.FOUND, hero, rev);
        }

        static final Loaded NONE = new Loaded(Status.NONE, null, 0);
        static final Loaded UNREADABLE = new Loaded(Status.UNREADABLE, null, 0);
    }

    /** What a write did. */
    public record Saved(Status status, long rev) {

        public enum Status {
            WRITTEN, CONFLICT, FAILED
        }

        public boolean written() {
            return status == Status.WRITTEN;
        }
    }

    /** A hero's place in the top: who, what class, how far. */
    public record Standing(UUID player, HeroClass heroClass, int level, long xp) {
    }

    private final Database database;

    public HeroBook(Database database) {
        this.database = database;
    }

    public boolean usable() {
        return database.isUsable();
    }

    public Loaded load(UUID player) {
        Optional<Loaded> read = database.read(connection -> {
            try (PreparedStatement select = connection.prepareStatement("SELECT * FROM hero WHERE player = ?")) {
                select.setString(1, player.toString());
                try (ResultSet row = select.executeQuery()) {
                    if (!row.next()) {
                        return Loaded.NONE;
                    }
                    Optional<HeroClass> heroClass = HeroClass.named(row.getString("class"));
                    if (heroClass.isEmpty()) {
                        return Loaded.UNREADABLE;
                    }
                    Hero hero = new Hero(player, heroClass.get(), row.getInt("level"), row.getLong("xp"),
                            AbilityScores.decode(row.getString("scores")), row.getInt("improvements"),
                            decodeLoadout(row.getString("loadout")), decodeSlots(row.getString("slots_spent")),
                            row.getInt("sorcery_points"), row.getInt("hit_dice_spent"),
                            decodeUsed(row.getString("used")), row.getLong("long_rest_at"),
                            Subclass.byId(row.getString("subclass")).orElse(null));
                    return Loaded.found(hero, row.getLong("rev"));
                }
            }
        });
        return read.orElse(Loaded.UNREADABLE);
    }

    /**
     * Writes the hero over the revision it was read at; 0 for a hero not yet in the database.
     */
    public Saved save(Hero hero, long expectedRev) {
        long[] written = {0};
        boolean ok = database.write(connection -> written[0] = expectedRev <= 0
                ? insert(connection, hero) : update(connection, hero, expectedRev));
        if (!ok) {
            return new Saved(Saved.Status.FAILED, expectedRev);
        }
        return written[0] > 0 ? new Saved(Saved.Status.WRITTEN, written[0])
                : new Saved(Saved.Status.CONFLICT, expectedRev);
    }

    /** The most experienced heroes, most first; a tie goes to the higher level. Empty when the database cannot be read. */
    public List<Standing> top(int most) {
        return database.read(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT player, class, level, xp FROM hero ORDER BY xp DESC, level DESC, player LIMIT ?")) {
                select.setInt(1, Math.max(1, most));
                List<Standing> found = new ArrayList<>();
                try (ResultSet row = select.executeQuery()) {
                    while (row.next()) {
                        Optional<HeroClass> heroClass = HeroClass.named(row.getString(2));
                        if (heroClass.isPresent()) {
                            found.add(new Standing(UUID.fromString(row.getString(1)), heroClass.get(),
                                    row.getInt(3), row.getLong(4)));
                        }
                    }
                }
                return List.copyOf(found);
            }
        }).orElse(List.of());
    }

    /** Takes the hero out entirely, whatever revision it is at. */
    public boolean delete(UUID player) {
        return database.write(connection -> {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM hero WHERE player = ?")) {
                delete.setString(1, player.toString());
                delete.executeUpdate();
            }
        });
    }

    private static long insert(Connection connection, Hero hero) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO hero (class, level, xp, scores, improvements, loadout, slots_spent, sorcery_points,
                                  hit_dice_spent, used, long_rest_at, subclass, player, rev)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                ON CONFLICT (player) DO NOTHING""")) {
            bind(insert, hero);
            return insert.executeUpdate() > 0 ? 1 : 0;
        }
    }

    private static long update(Connection connection, Hero hero, long expectedRev) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE hero SET class = ?, level = ?, xp = ?, scores = ?, improvements = ?, loadout = ?,
                                slots_spent = ?, sorcery_points = ?, hit_dice_spent = ?, used = ?, long_rest_at = ?,
                                subclass = ?, rev = rev + 1
                WHERE player = ? AND rev = ?""")) {
            bind(update, hero);
            update.setLong(14, expectedRev);
            return update.executeUpdate() > 0 ? expectedRev + 1 : 0;
        }
    }

    private static void bind(PreparedStatement statement, Hero hero) throws SQLException {
        statement.setString(1, hero.heroClass().id());
        statement.setInt(2, hero.level());
        statement.setLong(3, hero.xp());
        statement.setString(4, hero.scores().encode());
        statement.setInt(5, hero.improvements());
        statement.setString(6, String.join(",", hero.loadout()));
        statement.setString(7, encodeSlots(hero.slotsSpent()));
        statement.setInt(8, hero.sorceryPoints());
        statement.setInt(9, hero.hitDiceSpent());
        statement.setString(10, encodeUsed(hero.used()));
        statement.setLong(11, hero.longRestAt());
        statement.setString(12, hero.subclass() == null ? "" : hero.subclass().id());
        statement.setString(13, hero.player().toString());
    }

    static List<String> decodeLoadout(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(text.split(",", -1));
    }

    static String encodeSlots(List<Integer> spent) {
        List<String> parts = new ArrayList<>();
        for (Integer value : spent) {
            parts.add(String.valueOf(value));
        }
        return String.join(",", parts);
    }

    static List<Integer> decodeSlots(String text) {
        List<Integer> spent = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return spent;
        }
        for (String part : text.split(",", -1)) {
            try {
                spent.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException unreadable) {
                spent.add(0);
            }
        }
        return spent;
    }

    static String encodeUsed(Map<Feature, Integer> used) {
        List<String> parts = new ArrayList<>();
        used.forEach((feature, count) -> parts.add(feature.id() + "=" + count));
        return String.join(";", parts);
    }

    static Map<Feature, Integer> decodeUsed(String text) {
        Map<Feature, Integer> used = new EnumMap<>(Feature.class);
        if (text == null || text.isEmpty()) {
            return used;
        }
        for (String part : text.split(";")) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            Optional<Feature> feature = Feature.byId(pair[0]);
            try {
                feature.ifPresent(found -> used.put(found, Integer.parseInt(pair[1].trim())));
            } catch (NumberFormatException unreadable) {
                // A count nobody can read is a count of nothing used.
            }
        }
        return used;
    }
}
