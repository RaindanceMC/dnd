package de.raindancer.modules.dnd.rules;

import de.raindancer.modules.dnd.model.Surge;
import de.raindancer.modules.dnd.model.WildSurgeTable;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SurgeRuleTest {

    private final SurgeRule rule = new SurgeRule();

    private static final Set<String> EFFECTS = Set.of("levitation", "invisibility", "glowing", "slowness",
            "regeneration", "speed", "nausea", "night_vision", "water_breathing", "fire_resistance", "resistance",
            "weakness", "slow_falling", "jump_boost", "blindness", "absorption", "hunger", "saturation",
            "conduit_power", "dolphins_grace", "luck", "unluck", "darkness", "infested", "oozing", "weaving",
            "wind_charged", "haste");

    @Test
    @DisplayName("the table has exactly a hundred entries, numbered 1 to 100, every text different")
    void hundred() {
        assertThat(WildSurgeTable.size()).isEqualTo(100);
        Set<String> texts = new HashSet<>();
        for (int roll = 1; roll <= 100; roll++) {
            Surge surge = WildSurgeTable.entry(roll);
            assertThat(surge.number()).isEqualTo(roll);
            assertThat(texts.add(surge.text())).as("entry " + roll + " repeats another").isTrue();
            assertThat(surge.text()).doesNotContain("<");
        }
        assertThat(WildSurgeTable.entry(0).number()).isEqualTo(1);
        assertThat(WildSurgeTable.entry(500).number()).isEqualTo(100);
    }

    @Test
    @DisplayName("every name an entry uses is one the server knows")
    void namesResolve() {
        for (Surge surge : WildSurgeTable.all()) {
            switch (surge.kind()) {
                case SELF_EFFECT, NEARBY_EFFECT, FOES_EFFECT, ALLIES_EFFECT ->
                        assertThat(EFFECTS).as(surge.number() + "").contains(surge.what());
                case SUMMON, RAIN -> assertThat(EntityType.valueOf(surge.what()).isAlive()).isTrue();
                case GIFT, ILLUSION -> assertThat(Material.valueOf(surge.what())).isNotNull();
                case CAST -> assertThat(de.raindancer.modules.dnd.model.Spell.byId(surge.what()))
                        .as(surge.number() + "").isPresent();
                default -> {
                }
            }
        }
    }

    @Test
    @DisplayName("a surge happens on a percentile roll at or under the chance")
    void chance() {
        assertThat(rule.surges(5, 5)).isTrue();
        assertThat(rule.surges(6, 5)).isFalse();
        assertThat(rule.surges(1, 0)).isFalse();
        assertThat(rule.surges(100, 100)).isTrue();
    }

    @Test
    @DisplayName("Controlled Chaos keeps the kinder of two rolls; without it the first one stands")
    void controlledChaos() {
        Surge bad = WildSurgeTable.entry(1);
        Surge good = WildSurgeTable.entry(7);
        assertThat(bad.tone()).isEqualTo(Surge.Tone.BAD);
        assertThat(good.tone()).isEqualTo(Surge.Tone.GOOD);
        assertThat(rule.pick(1, 7, true)).isEqualTo(good);
        assertThat(rule.pick(7, 1, true)).isEqualTo(good);
        assertThat(rule.pick(1, 7, false)).isEqualTo(bad);
    }

    @Test
    @DisplayName("the table leans neither cruel nor kind")
    void balanced() {
        long good = WildSurgeTable.all().stream().filter(surge -> surge.tone() == Surge.Tone.GOOD).count();
        long bad = WildSurgeTable.all().stream().filter(surge -> surge.tone() == Surge.Tone.BAD).count();
        assertThat(good).isBetween(30L, 55L);
        assertThat(bad).isBetween(12L, 35L);
    }
}
