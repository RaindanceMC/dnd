package de.raindancer.modules.skills.skill;

import de.raindancer.modules.api.WordingContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The five skills' own wording: the same rules as messages.yml, and every key the code sends is written down. */
class CraftWordingTest implements WordingContract {

    private static final Path WORDING = Path.of("src/main/resources/de/raindancer/modules/skills/messages-craft.yml");
    private static final Path SOURCE = Path.of("src/main/java/de/raindancer/modules/skills/skill");

    @Override
    public Path moduleSource() {
        return SOURCE;
    }

    @Override
    public Path messagesFile() {
        return WORDING;
    }

    @Override
    public Path seriousFile() {
        return WORDING.resolveSibling("messages-craft-serious.yml");
    }

    @Override
    public int fewestWordingLines() {
        return 40;
    }

    @Test
    @DisplayName("every dnd.* key the five skills use is in messages-craft.yml")
    void everyKeyWritten() throws IOException {
        String wording = Files.readString(WORDING);
        Pattern used = Pattern.compile("\"dnd\\.(artisan|blueprint|enchanting|exploration)\\.([a-z-]+)\"");
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher found = used.matcher(Files.readString(file));
                while (found.find()) {
                    if (!wording.contains("\n    " + found.group(2) + ":")) {
                        missing.add(found.group());
                    }
                }
            }
        }
        assertThat(missing).isEmpty();
    }
}
