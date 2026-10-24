package de.raindancer.modules.dnd;

import de.raindancer.modules.api.ModuleCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DndCommandsTest {

    @Test
    @DisplayName("the command is declared without anything live, with its aliases and options")
    void declared() {
        List<ModuleCommand> declared = DndCommands.declared();
        assertThat(declared).hasSize(1);
        assertThat(declared.getFirst().name()).isEqualTo("adventurer");
        assertThat(declared.getFirst().names()).contains("dnd", "character");
        assertThat(declared.getFirst().options()).contains("rest", "stance", "admin reset <player>");
        assertThat(declared.getFirst().handler()).isNotNull();
    }

    @Test
    @DisplayName("running it while the module is stopped says so rather than throwing a null")
    void stopped() {
        DndCommands.stopped();
        assertThat(DndCommands.isRunning()).isFalse();
        assertThatThrownBy(() -> DndCommands.declared().getFirst().handler().execute(null, new String[]{"rest"}))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not running");
    }
}
