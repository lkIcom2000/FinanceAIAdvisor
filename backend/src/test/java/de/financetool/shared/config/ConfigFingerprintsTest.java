package de.financetool.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigFingerprintsTest {

    @TempDir
    Path home;

    @BeforeEach
    void writeFileSet() throws IOException {
        write("config/agent/reference.yaml", "agent:\n  id: reference\n");
        write("config/prompts/role-advisor.txt", "You are an advisor.\n");
        write("config/autonomy.yaml", "autonomy:\n  investmentShareOfLiquidAssets: 0.05\n");
        write("config/profile-minimums.yaml", "profileMinimums: {}\n");
        write("data/rules/ecb.yaml", "key: ecb\n");
    }

    @Test
    void changingAnyByteChangesTheFingerprintAndThatFilesHash() throws IOException {
        ConfigFingerprint before = ConfigFingerprints.compute(home, "reference");

        write("config/autonomy.yaml", "autonomy:\n  investmentShareOfLiquidAssets: 0.06\n");
        ConfigFingerprint after = ConfigFingerprints.compute(home, "reference");

        assertThat(after.value()).isNotEqualTo(before.value());
        assertThat(after.fileHashes().get("config/autonomy.yaml"))
                .isNotEqualTo(before.fileHashes().get("config/autonomy.yaml"));
        assertThat(after.fileHashes().get("config/prompts/role-advisor.txt"))
                .isEqualTo(before.fileHashes().get("config/prompts/role-advisor.txt"));
    }

    @Test
    void crlfAndLfGiveTheSameFingerprint() throws IOException {
        ConfigFingerprint lf = ConfigFingerprints.compute(home, "reference");

        write("config/agent/reference.yaml", "agent:\r\n  id: reference\r\n");

        assertThat(ConfigFingerprints.compute(home, "reference")).isEqualTo(lf);
    }

    @Test
    void coversTheAd14FileSetInSortedOrderAndSkipsOtherFiles() throws IOException {
        write("config/agent/other.yaml", "agent:\n  id: other\n");
        write("config/unrelated.yaml", "x: 1\n");
        write("config/letters/termination.html", "<p>{{name}}</p>\n");

        ConfigFingerprint fingerprint = ConfigFingerprints.compute(home, "reference");

        assertThat(fingerprint.fileHashes().keySet()).containsExactly(
                "config/agent/reference.yaml",
                "config/autonomy.yaml",
                "config/letters/termination.html",
                "config/profile-minimums.yaml",
                "config/prompts/role-advisor.txt",
                "data/rules/ecb.yaml");
        assertThat(fingerprint.value()).matches("[0-9a-f]{64}");
    }

    @Test
    void renamingAFileChangesTheFingerprint() throws IOException {
        ConfigFingerprint before = ConfigFingerprints.compute(home, "reference");

        Files.move(home.resolve("data/rules/ecb.yaml"), home.resolve("data/rules/ecb-2026.yaml"));

        assertThat(ConfigFingerprints.compute(home, "reference").value()).isNotEqualTo(before.value());
    }

    @Test
    void theRepositoryConfigurationIsFingerprinted() {
        ConfigFingerprint repo = ConfigFingerprints.compute(Path.of(".."), "reference");
        assertThat(repo.fileHashes()).containsKeys(
                "config/agent/reference.yaml", "config/autonomy.yaml", "config/profile-minimums.yaml",
                "config/prompts/role-advisor.txt");
    }

    private void write(String relative, String content) throws IOException {
        Path file = home.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
