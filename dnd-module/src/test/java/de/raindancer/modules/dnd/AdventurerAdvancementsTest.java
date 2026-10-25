package de.raindancer.modules.dnd;

import de.raindancer.core.content.advancement.AdvancementSink;
import de.raindancer.core.content.advancement.CustomAdvancements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdventurerAdvancementsTest {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());

    private final Map<String, Set<String>> met = new HashMap<>();
    private CustomAdvancements advancements;
    private AdventurerAdvancements adventurers;

    @BeforeEach
    void setUp() {
        advancements = new CustomAdvancements(new AdvancementSink() {
            @Override
            public Set<String> publish(Map<String, String> json) {
                return new HashSet<>(json.keySet());
            }

            @Override
            public Set<String> met(UUID player, String key) {
                return met.getOrDefault(key, Set.of());
            }

            @Override
            public boolean grant(UUID player, String key, String criterion) {
                return met.computeIfAbsent(key, k -> new HashSet<>()).add(criterion);
            }

            @Override
            public boolean revoke(UUID player, String key, String criterion) {
                return met.getOrDefault(key, new HashSet<>()).remove(criterion);
            }
        });
        adventurers = new AdventurerAdvancements(advancements);
        adventurers.define();
    }

    @Test
    @DisplayName("the whole tab loads")
    void allLoad() {
        for (String id : new String[]{"root", "hold_my_elytra", "mach_fuck", "low_orbit"}) {
            assertThat(advancements.isLoaded("rainsadventurers:" + id)).as(id).isTrue();
        }
    }

    @Test
    @DisplayName("drinking a magic potion opens the tab; brewing, flying and a long way on one potion earn the rest")
    void mach() {
        adventurers.drank(ALICE);
        assertThat(advancements.has(ALICE, "rainsadventurers:root")).isTrue();

        adventurers.brewedMach(ALICE);
        assertThat(advancements.has(ALICE, "rainsadventurers:hold_my_elytra")).isTrue();

        adventurers.machFlown(ALICE, 9_999);
        assertThat(advancements.has(ALICE, "rainsadventurers:mach_fuck")).as("the first push").isTrue();
        assertThat(advancements.has(ALICE, "rainsadventurers:low_orbit")).isFalse();
        adventurers.machFlown(ALICE, 10_000);
        assertThat(advancements.has(ALICE, "rainsadventurers:low_orbit")).isTrue();
    }
}
