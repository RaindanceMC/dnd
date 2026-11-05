package de.raindancer.modules.skills.skill.building;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlueprintCommandTest {

    @Test
    @DisplayName("paste <name> [rotate 90|180|270]: no turn, a quarter turn, or a refusal")
    void turning() {
        assertThat(BlueprintCommand.turn(new String[]{"blueprint", "paste", "hut"})).contains(0);
        assertThat(BlueprintCommand.turn(new String[]{"blueprint", "paste", "hut", "rotate", "90"})).contains(1);
        assertThat(BlueprintCommand.turn(new String[]{"blueprint", "paste", "hut", "rotate", "270"})).contains(3);
        assertThat(BlueprintCommand.turn(new String[]{"blueprint", "paste", "hut", "rotate", "45"})).isEmpty();
        assertThat(BlueprintCommand.turn(new String[]{"blueprint", "paste", "hut", "spin", "90"})).isEmpty();
        assertThat(BlueprintCommand.turn(new String[]{"blueprint", "paste", "hut", "rotate"})).isEmpty();
    }

    @Test
    @DisplayName("the subcommands are offered as they are typed")
    void suggestions() {
        assertThat(BlueprintCommand.suggest(null, new String[]{"blueprint", "p"})).containsExactly("paste");
        assertThat(BlueprintCommand.suggest(null, new String[]{"blueprint", ""}))
                .containsExactly("mark", "save", "list", "paste", "undo", "delete");
        assertThat(BlueprintCommand.suggest(null, new String[]{"blueprint", "paste", "hut", "r"}))
                .containsExactly("rotate");
        assertThat(BlueprintCommand.suggest(null, new String[]{"blueprint", "paste", "hut", "rotate", "1"}))
                .containsExactly("180");
    }
}
