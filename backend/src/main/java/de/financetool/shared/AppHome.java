package de.financetool.shared;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The application home ({@code APP_HOME}): the only place {@code config/}, {@code data/} and {@code models/} are read
 * from (AD-14). The repository root locally, {@code /app} in the image. Startup fails if a directory is missing.
 *
 * @param home the configured location, absolute or relative to the working directory
 */
@ConfigurationProperties("app")
public record AppHome(String home) {

    static final List<String> REQUIRED_DIRECTORIES = List.of("config", "data", "models");

    public AppHome {
        if (home == null || home.isBlank()) {
            throw new IllegalStateException("app.home (APP_HOME) must be set");
        }
        Path root = Path.of(home).toAbsolutePath().normalize();
        for (String directory : REQUIRED_DIRECTORIES) {
            Path path = root.resolve(directory);
            if (!Files.isDirectory(path)) {
                throw new IllegalStateException(
                        "APP_HOME " + root + " is missing the directory " + path + " (required: " + REQUIRED_DIRECTORIES + ")");
            }
        }
    }

    public Path root() {
        return Path.of(home).toAbsolutePath().normalize();
    }

    public Path config() {
        return root().resolve("config");
    }

    public Path data() {
        return root().resolve("data");
    }

    public Path models() {
        return root().resolve("models");
    }
}
