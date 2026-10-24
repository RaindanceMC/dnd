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
        assertThat(defaults.healthPercent()).isEqualTo(25);
        assertThat(defaults.spellDamagePercent()).isEqualTo(100);
        assertThat(defaults.playerDamagePercent()).isEqualTo(50);
        assertThat(defaults.showRolls()).isTrue();
        assertThat(defaults.actionMillis()).isEqualTo(1200);
        assertThat(defaults.bonusActionMillis()).isEqualTo(500);
        assertThat(defaults.shortRestSeconds()).isEqualTo(30);
        assertThat(defaults.longRestMinutes()).isEqualTo(15);
        assertThat(defaults.stanceNeedsSneak()).isFalse();
        assertThat(defaults.wildSurgePercent()).isEqualTo(5);
        assertThat(defaults.lootPercent()).isEqualTo(100);
        assertThat(defaults.abilityScores()).isTrue();
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
        DndSettings changed = defaults.withXpPercent(200).withHealthPercent(50)
                .withSpellDamagePercent(80).withPlayerDamagePercent(10).withShowRolls(false).withActionMillis(1)
                .withBonusActionMillis(2).withShortRestSeconds(3).withLongRestMinutes(4).withStanceNeedsSneak(true).withWildSurgePercent(50)
                .withLootPercent(7).withAbilityScores(false);
        assertThat(changed).isEqualTo(new DndSettings(List.of(), 200, 50, 80, 10, false, 1, 2, 3, 4, true, 50,
                7, false));
        assertThat(defaults.withXpPercent(5).healthPercent()).isEqualTo(defaults.healthPercent());
    }
}
