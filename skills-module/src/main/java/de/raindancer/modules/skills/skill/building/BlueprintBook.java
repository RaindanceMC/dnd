package de.raindancer.modules.skills.skill.building;

import de.raindancer.core.data.nbt.Nbt;
import de.raindancer.core.data.sql.Database;
import de.raindancer.core.world.build.Schematic;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Blueprints in the skills database ({@code blueprint}, see {@code SkillsDatabase}), a player's each by name: the
 * blocks as a gzipped Sponge schematic, the box's size beside it for the list. Off the server's threads only.
 */
public final class BlueprintBook {

    private final Database database;

    public BlueprintBook(Database database) {
        this.database = database;
    }

    public boolean save(UUID player, Blueprint blueprint) {
        byte[] data;
        try {
            data = Nbt.writeCompressed(blueprint.schematic().toTag());
        } catch (IOException unwritable) {
            return false;
        }
        Schematic blocks = blueprint.schematic();
        return database.write(connection -> {
            try (PreparedStatement upsert = connection.prepareStatement("""
                    INSERT INTO blueprint (player, name, width, height, length, anchor_x, anchor_y, anchor_z, data,
                                           saved_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (player, name) DO UPDATE SET width = excluded.width, height = excluded.height,
                        length = excluded.length, anchor_x = excluded.anchor_x, anchor_y = excluded.anchor_y,
                        anchor_z = excluded.anchor_z, data = excluded.data, saved_at = excluded.saved_at""")) {
                upsert.setString(1, player.toString());
                upsert.setString(2, blueprint.name());
                upsert.setInt(3, blocks.width());
                upsert.setInt(4, blocks.height());
                upsert.setInt(5, blocks.length());
                upsert.setInt(6, blueprint.anchorX());
                upsert.setInt(7, blueprint.anchorY());
                upsert.setInt(8, blueprint.anchorZ());
                upsert.setBytes(9, data);
                upsert.setLong(10, blueprint.savedAt());
                upsert.executeUpdate();
            }
        });
    }

    /** A player's blueprints by name; empty when there are none — or the database cannot be read. */
    public List<Blueprint.Summary> list(UUID player) {
        return database.read(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT name, width, height, length FROM blueprint WHERE player = ? ORDER BY name")) {
                select.setString(1, player.toString());
                List<Blueprint.Summary> found = new ArrayList<>();
                try (ResultSet row = select.executeQuery()) {
                    while (row.next()) {
                        found.add(new Blueprint.Summary(row.getString(1), row.getInt(2), row.getInt(3), row.getInt(4)));
                    }
                }
                return List.copyOf(found);
            }
        }).orElse(List.of());
    }

    public Optional<Blueprint> load(UUID player, String name) {
        return database.read(connection -> {
            try (PreparedStatement select = connection.prepareStatement("""
                    SELECT anchor_x, anchor_y, anchor_z, data, saved_at FROM blueprint
                    WHERE player = ? AND name = ?""")) {
                select.setString(1, player.toString());
                select.setString(2, name);
                try (ResultSet row = select.executeQuery()) {
                    if (!row.next()) {
                        return Optional.<Blueprint>empty();
                    }
                    try {
                        Schematic blocks = Schematic.from(Nbt.readCompressed(row.getBytes(4)));
                        return Optional.of(new Blueprint(name, blocks, row.getInt(1), row.getInt(2), row.getInt(3),
                                row.getLong(5)));
                    } catch (IOException unreadable) {
                        return Optional.<Blueprint>empty();
                    }
                }
            }
        }).flatMap(found -> found);
    }

    /** @return whether there was one by that name */
    public boolean delete(UUID player, String name) {
        boolean[] gone = {false};
        database.write(connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM blueprint WHERE player = ? AND name = ?")) {
                delete.setString(1, player.toString());
                delete.setString(2, name);
                gone[0] = delete.executeUpdate() > 0;
            }
        });
        return gone[0];
    }
}
