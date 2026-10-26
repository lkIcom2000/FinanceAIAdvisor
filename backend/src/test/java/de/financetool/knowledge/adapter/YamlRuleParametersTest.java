package de.financetool.knowledge.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.knowledge.NoValidRuleParameterException;
import de.financetool.knowledge.RuleParameter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlRuleParametersTest {

    static final Path FILE = Path.of("..", "data").resolve(YamlRuleParameters.FILE);
    static final LocalDate AS_OF = LocalDate.of(2026, 10, 15);

    @Test
    void theCommittedParametersAreValidAtTheFixtureDate() {
        YamlRuleParameters parameters = new YamlRuleParameters(FILE);

        RuleParameter band = parameters.valid("forecast_variable_spending_band", AS_OF);
        assertThat(band.value()).isEqualByComparingTo("0.15");
        assertThat(band.unit()).isEqualTo("FRACTION");
        assertThat(band.version()).isEqualTo("1");
        assertThat(band.validFrom()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(band.validTo()).isNull();
        assertThat(parameters.valid("emergency_fund_months_min", AS_OF).value()).isEqualByComparingTo("3");
        assertThat(parameters.valid("emergency_fund_months_max", AS_OF).value()).isEqualByComparingTo("6");
    }

    @Test
    void aKeyThatIsNotValidAtTheDateIsRefused(@TempDir Path dir) throws IOException {
        Path file = write(dir, """
                parameters:
                  - key: k
                    value: 1
                    unit: COUNT
                    valid_from: 2027-01-01
                    valid_to: null
                    source: test
                    version: "2"
                  - key: k
                    value: 0.5
                    unit: COUNT
                    valid_from: 2020-01-01
                    valid_to: 2025-12-31
                    source: test
                    version: "1"
                """);
        YamlRuleParameters parameters = new YamlRuleParameters(file);

        assertThat(parameters.valid("k", LocalDate.of(2025, 12, 31)).version()).isEqualTo("1");
        assertThat(parameters.valid("k", LocalDate.of(2027, 1, 1)).version()).isEqualTo("2");
        assertThatThrownBy(() -> parameters.valid("k", AS_OF)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No version of Rule Parameter k is valid on 2026-10-15");
        assertThatThrownBy(() -> parameters.valid("unknown", AS_OF)).isInstanceOf(IllegalStateException.class);
        assertThat(parameters.allValid(LocalDate.of(2027, 1, 1))).extracting(RuleParameter::version)
                .containsExactly("2");
        assertThat(parameters.allValid(AS_OF)).isEmpty();
    }

    @Test
    void overlappingVersionsOrMissingFieldsStopLoading(@TempDir Path dir) throws IOException {
        Path overlap = write(dir, """
                parameters:
                  - {key: k, value: 1, unit: COUNT, valid_from: 2020-01-01, valid_to: null, source: s, version: "1"}
                  - {key: k, value: 2, unit: COUNT, valid_from: 2024-01-01, valid_to: null, source: s, version: "2"}
                """);
        assertThatThrownBy(() -> new YamlRuleParameters(overlap)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("overlap");

        Path missing = write(dir, """
                parameters:
                  - {key: k, value: 1, unit: COUNT, valid_to: null, source: s, version: "1"}
                """);
        assertThatThrownBy(() -> new YamlRuleParameters(missing)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid Rule Parameter k");
    }

    @Test
    void theSubsidyRulesEndWithTheYearAndTheEcbRatesAreVersioned() {
        YamlRuleParameters parameters = new YamlRuleParameters(FILE);

        RuleParameter basic = parameters.valid("riester_basic_allowance", AS_OF);
        assertThat(basic.value()).isEqualByComparingTo("175");
        assertThat(basic.validTo()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThatThrownBy(() -> parameters.valid("riester_basic_allowance", LocalDate.of(2027, 1, 1)))
                .isInstanceOf(NoValidRuleParameterException.class);
        de.financetool.core.domain.RiesterSubsidy.PARAMETERS
                .forEach(key -> assertThat(parameters.valid(key, AS_OF).validTo()).hasYear(2026));
        assertThat(parameters.valid("ecb_deposit_facility_rate", AS_OF).value()).isEqualByComparingTo("0.02");
        assertThat(parameters.valid("ecb_deposit_facility_rate", LocalDate.of(2025, 5, 1)).version())
                .isEqualTo("2025-04-23");
        assertThat(parameters.valid("ecb_main_refinancing_rate", AS_OF).value()).isEqualByComparingTo("0.0215");
        assertThat(parameters.valid("ecb_marginal_lending_rate", AS_OF).value()).isEqualByComparingTo("0.024");
    }

    @Test
    void aVersionThatAppearsTwiceStopsLoading(@TempDir Path dir) throws IOException {
        Path edited = write(dir, """
                parameters:
                  - {key: k, value: 1, unit: COUNT, valid_from: 2020-01-01, valid_to: 2020-12-31, source: s, version: "1"}
                  - {key: k, value: 2, unit: COUNT, valid_from: 2021-01-01, valid_to: null, source: s, version: "1"}
                """);
        assertThatThrownBy(() -> new YamlRuleParameters(edited)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("appears twice");
    }

    private static Path write(Path dir, String yaml) throws IOException {
        Path file = Files.createTempFile(dir, "rules", ".yaml");
        Files.writeString(file, yaml);
        return file;
    }
}
