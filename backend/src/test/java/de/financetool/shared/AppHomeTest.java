package de.financetool.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppHomeTest {

    @TempDir
    Path home;

    @Test
    void resolvesTheThreeDirectoriesBelowHome() throws IOException {
        createDirectories("config", "data", "models");

        AppHome appHome = new AppHome(home.toString());

        assertThat(appHome.config()).isEqualTo(home.toAbsolutePath().normalize().resolve("config"));
        assertThat(appHome.data()).endsWith(Path.of("data"));
        assertThat(appHome.models()).endsWith(Path.of("models"));
    }

    @Test
    void missingDirectoryStopsStartupNamingThePath() throws IOException {
        createDirectories("config", "data");

        assertThatThrownBy(() -> new AppHome(home.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(home.resolve("models").toString());
    }

    @Test
    void repositoryRootIsAValidHome() {
        assertThat(new AppHome("..").models()).isDirectory();
    }

    private void createDirectories(String... names) throws IOException {
        for (String name : names) {
            Files.createDirectories(home.resolve(name));
        }
    }
}
