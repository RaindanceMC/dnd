package de.raindancer.modules.dnd.store;

import de.raindancer.core.data.sql.Schema;

/** The network's one adventurers database. Steps are only ever appended. */
public final class DndDatabase {

    public static final String NAME = "dnd";

    public static final Schema SCHEMA = Schema.of(
            """
            CREATE TABLE hero (
                player TEXT NOT NULL PRIMARY KEY,
                class TEXT NOT NULL,
                level INTEGER NOT NULL,
                xp INTEGER NOT NULL,
                scores TEXT NOT NULL,
                improvements INTEGER NOT NULL,
                loadout TEXT NOT NULL,
                slots_spent TEXT NOT NULL,
                sorcery_points INTEGER NOT NULL,
                hit_dice_spent INTEGER NOT NULL,
                used TEXT NOT NULL,
                long_rest_at INTEGER NOT NULL,
                rev INTEGER NOT NULL
            )
            """,
            "ALTER TABLE hero ADD COLUMN subclass TEXT NOT NULL DEFAULT ''",
            // 0.11.0: the skill tree. The class, scores, slots and the rest stay as they were written, unread.
            "ALTER TABLE hero ADD COLUMN nodes TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE hero ADD COLUMN respec_at INTEGER NOT NULL DEFAULT 0");

    private DndDatabase() {
    }
}
