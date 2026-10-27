package de.financetool.research.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.actions.domain.RuleTemplate;
import de.financetool.monitoring.Monitoring;
import de.financetool.shared.fixtures.PersonaFixtureCatalog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Story 8.5: study presets are files, validated at startup. */
class StudyPresetsTest {

    static final Path DATA = Path.of("..", "data");
    static final PersonaFixtureCatalog FIXTURES = new PersonaFixtureCatalog(DATA.resolve("personas"));

    @TempDir
    Path directory;

    @Test
    void theRepositoryHoldsTheUj4AndUj2Presets() {
        StudyPresets presets = new StudyPresets(DATA.resolve(StudyPresets.DIRECTORY), FIXTURES);

        assertThat(presets.ids()).contains("uj4-duplicate-coverage-jonas", "uj2-advice-marco");
        assertThat(presets.find("uj4-duplicate-coverage-jonas")).hasValueSatisfying(p -> {
            assertThat(p.title()).isEqualTo("UJ-4 duplicate coverage (Jonas)");
            assertThat(p.personaId()).isEqualTo("p-jonas");
            assertThat(p.simulatedDate()).isEqualTo(LocalDate.of(2026, 10, 15));
            assertThat(p.setup()).isEmpty();
        });
        assertThat(presets.find("uj2-advice-marco")).hasValueSatisfying(
                p -> assertThat(p.title()).isEqualTo("UJ-2 advice (Marco)"));
        assertThat(presets.find("uj1-shortfall-lena")).hasValueSatisfying(p -> {
            assertThat(p.title()).isEqualTo("UJ-1 shortfall, 15 Oct (Lena)");
            assertThat(p.mandates()).singleElement()
                    .satisfies(m -> assertThat(m.template()).isEqualTo(RuleTemplate.SAFETY_CUSHION));
        });
        assertThat(presets.find("nope")).isEmpty();
    }

    @Test
    void onlyAMandateStepWithAKnownTemplateAndValidBoundsPassesStartup() throws IOException {
        write("x", "\"p-jonas\"", "\"2026-10-15\"", "[{\"type\": \"document\"}]");
        assertThatThrownBy(() -> new StudyPresets(directory, FIXTURES)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("x.json").hasMessageContaining("unknown setup step document");

        for (String step : List.of("{\"type\": \"mandate\"}",
                "{\"type\": \"mandate\", \"template\": \"NOPE\", \"maxPerAction\": \"1\", \"maxPerMonth\": \"1\","
                        + " \"floor\": \"0\"}",
                "{\"type\": \"mandate\", \"template\": \"SAFETY_CUSHION\", \"maxPerAction\": \"0\","
                        + " \"maxPerMonth\": \"1\", \"floor\": \"0\"}")) {
            write("x", "\"p-jonas\"", "\"2026-10-15\"", "[" + step + "]");
            assertThatThrownBy(() -> new StudyPresets(directory, FIXTURES)).as(step)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("x.json");
        }

        String cushion = "{\"type\": \"mandate\", \"template\": \"SAFETY_CUSHION\", \"maxPerAction\": \"300.00\","
                + " \"maxPerMonth\": \"600.00\", \"floor\": \"2000.00\"}";
        write("x", "\"p-jonas\"", "\"2026-10-15\"", "[" + cushion + ", " + cushion + "]");
        assertThatThrownBy(() -> new StudyPresets(directory, FIXTURES)).hasMessageContaining("two mandate steps");

        write("x", "\"p-jonas\"", "\"2026-10-15\"", "[" + cushion + "]");
        assertThat(new StudyPresets(directory, FIXTURES).find("x").orElseThrow().mandates()).singleElement()
                .satisfies(m -> {
                    assertThat(m.template()).isEqualTo(RuleTemplate.SAFETY_CUSHION);
                    assertThat(m.bounds().floor()).isEqualByComparingTo("2000.00");
                });
    }

    @Test
    void anUnknownPersonaADateBeforeTheFixtureAndAnUnknownFieldFailStartup() throws IOException {
        write("x", "\"p-nobody\"", "\"2026-10-15\"", "[]");
        assertThatThrownBy(() -> new StudyPresets(directory, FIXTURES)).hasMessageContaining("no persona fixture");

        write("x", "\"p-jonas\"", "\"2026-10-14\"", "[]");
        assertThatThrownBy(() -> new StudyPresets(directory, FIXTURES)).hasMessageContaining("before the persona");

        Files.writeString(directory.resolve("x.json"), "{\"id\": \"x\", \"title\": \"X\", \"personaId\": \"p-jonas\","
                + " \"simulatedDate\": \"2026-10-15\", \"setup\": [], \"extra\": 1}");
        assertThatThrownBy(() -> new StudyPresets(directory, FIXTURES)).hasMessageContaining("is not valid");
    }

    @Test
    void theIdMustBeTheFileName() throws IOException {
        Files.writeString(directory.resolve("y.json"), "{\"id\": \"x\", \"title\": \"X\", \"personaId\": \"p-jonas\","
                + " \"simulatedDate\": \"2026-10-15\", \"setup\": []}");

        assertThatThrownBy(() -> new StudyPresets(directory, FIXTURES)).hasMessageContaining("id must be the file name");
    }

    @Test
    void theSummaryCountsByStatusInChipOrder() {
        assertThat(PrepareStudyUseCase.summary(List.of(
                new Monitoring.RunNotification(UUID.randomUUID(), "DONE_FOR_YOU"),
                new Monitoring.RunNotification(UUID.randomUUID(), "RECOMMENDATION")), 1))
                .isEqualTo("2 notifications (1 Recommendation, 1 Done for you), 1 rule active");
        assertThat(PrepareStudyUseCase.summary(List.of(), 0)).isEqualTo("0 notifications, 0 rules active");
    }

    private void write(String id, String persona, String date, String setup) throws IOException {
        Files.writeString(directory.resolve(id + ".json"), "{\"id\": \"" + id + "\", \"title\": \"X\", \"personaId\": "
                + persona + ", \"simulatedDate\": " + date + ", \"setup\": " + setup + "}");
    }
}
