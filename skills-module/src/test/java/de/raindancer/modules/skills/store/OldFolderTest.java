package de.raindancer.modules.skills.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OldFolderTest {

    @TempDir
    Path plugins;

    @Test
    @DisplayName("the first start as RainsSkills takes RainsAdventurers' files along; the old folder stays as it was")
    void carriesTheOldFolderOver() throws IOException {
        Path old = Files.createDirectories(plugins.resolve("RainsAdventurers"));
        Files.writeString(old.resolve("config.yml"), "loot-percent: 7\n");
        Files.createDirectories(old.resolve("help"));
        Files.writeString(old.resolve("help/own.yml"), "x: 1\n");
        Path own = plugins.resolve("RainsSkills");

        assertThat(OldFolder.carryOver(own)).isEqualTo(2);
        assertThat(own.resolve("config.yml")).hasContent("loot-percent: 7");
        assertThat(own.resolve("help/own.yml")).hasContent("x: 1");
        assertThat(old.resolve("config.yml")).as("nothing is taken away").exists();
    }

    @Test
    @DisplayName("the resource pack the old plugin wrote out is left behind: the new one writes its own on every start")
    void notThePack() throws IOException {
        Path old = Files.createDirectories(plugins.resolve("RainsAdventurers"));
        Files.writeString(old.resolve("config.yml"), "a: 1\n");
        Files.createDirectories(old.resolve("pack/looks/assets"));
        Files.writeString(old.resolve("pack/looks/assets/old.png"), "x");
        Path own = plugins.resolve("RainsSkills");

        assertThat(OldFolder.carryOver(own)).isEqualTo(1);
        assertThat(own.resolve("pack")).doesNotExist();
    }

    @Test
    @DisplayName("once RainsSkills has a config of its own, nothing is copied again — not over the owner's changes")
    void onlyOnce() throws IOException {
        Path old = Files.createDirectories(plugins.resolve("RainsAdventurers"));
        Files.writeString(old.resolve("config.yml"), "loot-percent: 7\n");
        Path own = Files.createDirectories(plugins.resolve("RainsSkills"));
        Files.writeString(own.resolve("config.yml"), "loot-percent: 50\n");

        assertThat(OldFolder.carryOver(own)).isZero();
        assertThat(own.resolve("config.yml")).hasContent("loot-percent: 50");
    }

    @Test
    @DisplayName("no old folder, or a folder of another name (inside the modules host), is left alone")
    void nothingToCarry() throws IOException {
        assertThat(OldFolder.carryOver(plugins.resolve("RainsSkills"))).isZero();
        Files.createDirectories(plugins.resolve("RainsAdventurers"));
        Files.writeString(plugins.resolve("RainsAdventurers/config.yml"), "a: 1\n");
        assertThat(OldFolder.carryOver(plugins.resolve("modules").resolve("dnd"))).isZero();
        assertThat(OldFolder.carryOver(null)).isZero();
    }
}
