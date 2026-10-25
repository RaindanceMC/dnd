package de.raindancer.modules.dnd.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("What the adventurers' screens say")
class ScreenLineRuleTest {

    private final ScreenLineRule rule = new ScreenLineRule();

    @Test
    @DisplayName("a progress bar of ten, full at the top")
    void bar() {
        assertThat(rule.bar(0.0)).isEqualTo("<dark_gray>▰▰▰▰▰▰▰▰▰▰");
        assertThat(rule.bar(0.5)).isEqualTo("<green>▰▰▰▰▰</green><dark_gray>▰▰▰▰▰");
        assertThat(rule.bar(1.0)).isEqualTo("<green>▰▰▰▰▰▰▰▰▰▰");
        assertThat(rule.bar(7.0)).isEqualTo("<green>▰▰▰▰▰▰▰▰▰▰");
    }

    @Test
    @DisplayName("the sheet: class (with its path), level, experience to the next level, hit dice left")
    void sheet() {
        String text = rule.sheet("Sorcerer", "Wild Magic", 4, 1_000, 2_700, 0.35, 2, 3, 6);
        assertThat(text).startsWith("<gold><bold>Your adventurer</bold></gold>")
                .contains("<white>Sorcerer</white> <gray>· Wild Magic")
                .contains("<gray>level <white>4")
                .contains("<gray>xp <white>1000</white><gray>/2700")
                .contains("<gray>hit dice <white>2</white><gray>/3 (d6)")
                .contains("▰");
    }

    @Test
    @DisplayName("no path yet reads as the class alone; the highest level has no next, so no bar")
    void sheetEdges() {
        String text = rule.sheet("Fighter", null, 20, 400_000, 0, 1.0, 0, 20, 10);
        assertThat(text).contains("<white>Fighter</white>").doesNotContain("·").doesNotContain("▰")
                .contains("<gray>level <white>20 <gold>(highest)").contains("<gray>xp <white>400000");
    }

    @Test
    @DisplayName("no adventurer yet points at where to become one")
    void none() {
        assertThat(rule.none()).isEqualTo("<gold><bold>Your adventurer</bold></gold>\n<gray>No class chosen yet <dark_gray>· <white>/adventurer");
    }

    @Test
    @DisplayName("the top: colours 1 gold, 2 gray, 3 bronze, the rest white; level and class beside the name")
    void top() {
        String text = rule.top(List.of(new ScreenLineRule.Row("Ada <b>", "Sorcerer", 7, 23_500),
                new ScreenLineRule.Row("Bo", "Fighter", 5, 7_000), new ScreenLineRule.Row("Cy", "Rogue", 3, 1_000),
                new ScreenLineRule.Row("Di", "Rogue", 1, 0)));
        assertThat(text).startsWith("<gold><bold>Top adventurers</bold></gold>")
                .contains("\n<gold>1. Ada \\<b> <dark_gray>· <gray>Sorcerer lv 7")
                .contains("\n<gray>2. Bo <dark_gray>· <gray>Fighter lv 5")
                .contains("\n<#cd7f32>3. Cy <dark_gray>· <gray>Rogue lv 3")
                .contains("\n<white>4. Di <dark_gray>· <gray>Rogue lv 1");
        assertThat(rule.top(List.of())).contains("<gray>Nobody has set out yet");
    }
}
