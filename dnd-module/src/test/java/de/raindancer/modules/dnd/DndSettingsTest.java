package de.raindancer.modules.dnd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DndSettingsTest {

    private final DndSettings defaults = DndSettings.DEFAULTS;

    @Test
    @DisplayName("every default, spelled out")
    void defaults() {
        assertThat(defaults.worlds()).isEmpty();
        assertThat(defaults.xpPercent()).isEqualTo(100);
        assertThat(defaults.lootPercent()).isEqualTo(100);
        assertThat(defaults.pvpPercent()).isEqualTo(50);
        assertThat(defaults.unlearnHours()).isEqualTo(24);
        assertThat(defaults.machBlocksPerSecond()).isEqualTo(600);
        assertThat(defaults.machSeconds()).isEqualTo(20);
    }

    @Test
    @DisplayName("no worlds means every world; a list means only those, whatever their case")
    void worlds() {
        assertThat(defaults.worksIn("anything")).isTrue();
        DndSettings some = defaults.withWorlds(List.of("Survival", " adventure "));
        assertThat(some.worksIn("survival")).isTrue();
        assertThat(some.worksIn("adventure")).isTrue();
        assertThat(some.worksIn("lobby")).isFalse();
    }

    @Test
    @DisplayName("every with… changes its own component and nothing else")
    void withers() {
        DndSettings changed = defaults.withXpPercent(200).withLootPercent(7).withPvpPercent(10).withUnlearnHours(3)
                .withMachBlocksPerSecond(100).withMachSeconds(9);
        assertThat(changed).isEqualTo(new DndSettings(List.of(), 200, 7, 10, 3, 100, 9));
        assertThat(defaults.withXpPercent(5).lootPercent()).isEqualTo(defaults.lootPercent());
    }
}
