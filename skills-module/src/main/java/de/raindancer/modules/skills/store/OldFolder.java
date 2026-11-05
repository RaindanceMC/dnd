package de.raindancer.modules.skills.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The plugin was RainsAdventurers until 0.13.0, so a server that updates has its settings in plugins/RainsAdventurers.
 * On the first start as RainsSkills they are copied over — all but the resource pack it wrote out; the old folder is
 * left as it is.
 */
public final class OldFolder {

    static final String OLD = "RainsAdventurers";
    static final String NEW = "RainsSkills";

    private OldFolder() {
    }

    /** @return how many files were copied */
    public static int carryOver(Path own) {
        if (own == null || own.getFileName() == null || !NEW.equals(own.getFileName().toString())
                || Files.exists(own.resolve("config.yml"))) {
            return 0;
        }
        Path old = own.resolveSibling(OLD);
        if (!Files.isDirectory(old)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(old)) {
            // Not the resource pack it wrote out: the plugin writes its own on every start, and anything stale there
            // would be sent to every player.
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(file -> !old.relativize(file).getName(0).toString().equals("pack")).toList();
            int copied = 0;
            for (Path file : files) {
                Path target = own.resolve(old.relativize(file).toString());
                if (Files.exists(target)) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(file, target);
                copied++;
            }
            return copied;
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }
}
