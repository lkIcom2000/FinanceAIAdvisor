package de.financetool.advisory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Number Check table (Story 3.6, at least 20 cases): {@code numbers/cases.json} holds the expectations of the
 * experiment's {@code numbercheck.check}; a case with {@code experimentPassed} is a deliberate deviation (see
 * {@link NumberCheck}).
 */
class NumberCheckTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    static Stream<Arguments> cases() {
        try (InputStream in = NumberCheckTest.class.getResourceAsStream("/numbers/cases.json")) {
            List<Map<String, Object>> cases = JSON.readValue(in, new TypeReference<>() {
            });
            assertThat(cases).hasSizeGreaterThanOrEqualTo(20);
            return cases.stream().map(c -> Arguments.of(c.get("name"), c));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @SuppressWarnings("unchecked")
    void matchesTheExperimentsNumberCheck(String name, Map<String, Object> c) {
        List<BigDecimal> sourced = ((List<String>) c.get("sourced")).stream().map(BigDecimal::new).toList();
        CheckResult result = NumberCheck.check((String) c.get("text"), sourced);

        assertThat(result.checkId()).isEqualTo(CheckId.NUMBER_MATCH);
        assertThat(result.applicable()).isTrue();
        assertThat(result.passed()).isEqualTo(c.get("passed"));
        if (c.containsKey("experimentPassed")) {
            assertThat(c.get("experimentPassed")).as("a deviation differs from the experiment")
                    .isEqualTo(!(Boolean) c.get("passed"));
        }
        assertThat(result.reasonCode()).isEqualTo((Boolean) c.get("passed") ? "" : "number_mismatch");
        assertThat(result.details().get("checked")).isEqualTo(c.get("checked"));
        List<Map<String, Object>> mismatches = (List<Map<String, Object>>) result.details().get("mismatches");
        assertThat(mismatches).extracting(m -> Map.of("text", m.get("text"), "value", m.get("value"), "approximate",
                m.get("approximate"))).isEqualTo(c.get("mismatches"));
    }

    @Test
    void theTableCoversEveryRuleOfTheAcceptanceCriteria() {
        List<String> names = cases().map(a -> (String) a.get()[0]).toList();
        assertThat(names).anyMatch(n -> n.startsWith("rounded")).anyMatch(n -> n.startsWith("about"))
                .anyMatch(n -> n.contains("percent")).anyMatch(n -> n.contains("date excluded"))
                .anyMatch(n -> n.startsWith("list positions")).anyMatch(n -> n.startsWith("article"))
                .anyMatch(n -> n.startsWith("paragraph")).contains("English format", "German format");
    }

    @Test
    void aPercentagesFractionIsComparedAtTwoMoreDecimals() {
        assertThat(NumberCheck.check("A 4% return.", List.of(new BigDecimal("0.04"))).passed()).isTrue();
        assertThat(NumberCheck.check("A 4.25% return.", List.of(new BigDecimal("0.0425"))).passed()).isTrue();
        assertThat(NumberCheck.check("A 4.3% return.", List.of(new BigDecimal("0.0425"))).passed()).isFalse();
        assertThat(NumberCheck.check("A 5% return.", List.of(new BigDecimal("0.04"))).passed()).isFalse();
    }
}
