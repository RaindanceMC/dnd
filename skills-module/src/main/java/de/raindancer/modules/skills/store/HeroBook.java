package de.raindancer.modules.skills.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.modules.skills.model.Hero;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
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

    /** A hero's place in the top: who, how far. */
    public record Standing(UUID player, int level, long xp) {
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
                    // Whatever class a hero had before 0.11.0, they are its level and experience now.
                    Hero hero = new Hero(player, row.getInt("level"), row.getLong("xp"),
                            decodeNodes(row.getString("nodes")), row.getLong("respec_at"));
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
                    "SELECT player, level, xp FROM hero ORDER BY xp DESC, level DESC, player LIMIT ?")) {
                select.setInt(1, Math.max(1, most));
                List<Standing> found = new ArrayList<>();
                try (ResultSet row = select.executeQuery()) {
                    while (row.next()) {
                        found.add(new Standing(UUID.fromString(row.getString(1)), row.getInt(2), row.getLong(3)));
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
                                  hit_dice_spent, used, long_rest_at, nodes, respec_at, player, rev)
                VALUES ('adventurer', ?, ?, '', 0, '', '', 0, 0, '', 0, ?, ?, ?, 1)
                ON CONFLICT (player) DO NOTHING""")) {
            bind(insert, hero);
            return insert.executeUpdate() > 0 ? 1 : 0;
        }
    }

    private static long update(Connection connection, Hero hero, long expectedRev) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE hero SET level = ?, xp = ?, nodes = ?, respec_at = ?, rev = rev + 1
                WHERE player = ? AND rev = ?""")) {
            bind(update, hero);
            update.setLong(6, expectedRev);
            return update.executeUpdate() > 0 ? expectedRev + 1 : 0;
        }
    }

    private static void bind(PreparedStatement statement, Hero hero) throws SQLException {
        statement.setInt(1, hero.level());
        statement.setLong(2, hero.xp());
        statement.setString(3, encodeNodes(hero.nodes()));
        statement.setLong(4, hero.respecAt());
        statement.setString(5, hero.player().toString());
    }

    /** {@code node=rank;node=rank}, in a fixed order so the same tree is the same text. */
    static String encodeNodes(Map<String, Integer> nodes) {
        List<String> parts = new ArrayList<>();
        new java.util.TreeMap<>(nodes).forEach((node, rank) -> parts.add(node + "=" + rank));
        return String.join(";", parts);
    }

    static Map<String, Integer> decodeNodes(String text) {
        Map<String, Integer> nodes = new HashMap<>();
        if (text == null || text.isEmpty()) {
            return nodes;
        }
        for (String part : text.split(";")) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2 || pair[0].isBlank()) {
                continue;
            }
            try {
                // A rank kept before there were skills names its node alone: it goes to the skill whose tree has it.
                nodes.put(de.raindancer.modules.skills.model.SkillCatalog.qualify(pair[0].strip()),
                        Integer.parseInt(pair[1].strip()));
            } catch (NumberFormatException unreadable) {
                // A rank nobody can read is a rank never taken.
            }
        }
        return nodes;
    }
}
