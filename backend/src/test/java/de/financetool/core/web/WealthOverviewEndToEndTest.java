package de.financetool.core.web;

import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.financetool.PostgresIntegrationTest;
import de.financetool.personas.PersonaClock;
import de.financetool.shared.domain.PersonaId;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.support.TransactionTemplate;

/** Story 2.4: {@code GET /api/overview/wealth} end to end, with every figure stored once and traceable. */
class WealthOverviewEndToEndTest extends PostgresIntegrationTest {

    static final PersonaId JONAS = new PersonaId("p-jonas");

    @Autowired
    PersonaClock clock;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void jonasAtTheFixtureStart() {
        transaction.executeWithoutResult(tx -> clock.setSimulatedDate(JONAS, LocalDate.of(2026, 10, 15)));
    }

    String wealth(MockHttpSession session) throws Exception {
        return mvc.perform(get("/api/overview/wealth").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    String provenance(MockHttpSession session, String provId) throws Exception {
        return mvc.perform(get("/api/provenance/{id}", provId).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    static BigDecimal decimal(String body, String path) {
        return new BigDecimal((String) JsonPath.read(body, path));
    }

    @Test
    void jonasSeesHisHoldingHisSavingsPlanAndARangeOverThirtyYears() throws Exception {
        MockHttpSession jonas = login(mvc, "p-jonas");
        String body = wealth(jonas);

        assertThat((String) JsonPath.read(body, "$.holdings[0].name")).isEqualTo("World equity ETF");
        assertThat((String) JsonPath.read(body, "$.holdings[0].assetClass")).isEqualTo("Equity ETF");
        assertThat((String) JsonPath.read(body, "$.holdings[0].units")).isEqualTo("17.842300");
        assertThat((String) JsonPath.read(body, "$.holdings[0].value.value")).isEqualTo("2004.76");
        assertThat((String) JsonPath.read(body, "$.savingsPlans[0].monthlyAmount.value")).isEqualTo("100.00");
        assertThat((Integer) JsonPath.read(body, "$.savingsPlans[0].dayOfMonth")).isEqualTo(15);

        assertThat((Integer) JsonPath.read(body, "$.projection.years")).isEqualTo(30);
        assertThat((String) JsonPath.read(body, "$.projection.startingValue.value")).isEqualTo("2004.76");
        assertThat((String) JsonPath.read(body, "$.projection.monthlyContribution.value")).isEqualTo("100.00");
        assertThat((String) JsonPath.read(body, "$.projection.paidIn.value")).isEqualTo("38004.76");
        assertThat(decimal(body, "$.projection.p10.value")).isLessThan(decimal(body, "$.projection.p50.value"));
        assertThat(decimal(body, "$.projection.p50.value")).isLessThan(decimal(body, "$.projection.p90.value"));
        assertThat((List<Integer>) JsonPath.read(body, "$.projection.points[*].year"))
                .hasSize(30).startsWith(1).endsWith(30);
        assertThat((String) JsonPath.read(body, "$.projection.points[29].p50"))
                .isEqualTo(JsonPath.read(body, "$.projection.p50.value"));
        assertThat((String) JsonPath.read(body, "$.projection.provId"))
                .isEqualTo(JsonPath.read(body, "$.projection.p50.provId"));
        assertThat((String) JsonPath.read(body, "$.projection.wholeEuros.p50")).matches("\\d{1,3}(,\\d{3})*");
        assertThat((List<String>) JsonPath.read(body, "$.projection.assumptions")).hasSize(5)
                .first().asString().startsWith("Returns: normally distributed monthly returns");

        // Same call again: identical figures and keys, each stored exactly once for Jonas with origin overview.
        String again = wealth(jonas);
        assertThat(again).isEqualTo(body);
        List<String> provIds = JsonPath.read(body, "$..provId");
        assertThat(provIds).hasSizeGreaterThanOrEqualTo(9).allSatisfy(id -> assertThat(id).matches("[0-9a-f]{16}"));
        for (String provId : provIds.stream().distinct().toList()) {
            assertThat(jdbc.queryForList("SELECT origin FROM evidence_items WHERE prov_id = ? AND persona_id = ?",
                    String.class, provId, "p-jonas")).containsExactly("overview");
        }
    }

    @Test
    void theProjectionDrillsDownToItsTotalsAndTheHolding() throws Exception {
        MockHttpSession jonas = login(mvc, "p-jonas");
        String body = wealth(jonas);
        String projection = provenance(jonas, JsonPath.read(body, "$.projection.provId"));

        assertThat((String) JsonPath.read(projection, "$.source.description"))
                .startsWith("Calculated by Fine's calculation engine (goal projection)");
        assertThat((List<String>) JsonPath.read(projection, "$.inputs[?(@.provId)].provId")).containsExactly(
                JsonPath.read(body, "$.projection.startingValue.provId"),
                JsonPath.read(body, "$.projection.monthlyContribution.provId"));
        assertThat((List<String>) JsonPath.read(projection, "$.ruleParameters[*].id")).containsExactly(
                "projection_default_horizon_years", "projection_equity_mean_return", "projection_equity_volatility",
                "projection_inflation", "reference_ter_world_equity_etf");
        assertThat((List<String>) JsonPath.read(projection, "$.assumptions")).hasSize(5);

        String starting = provenance(jonas, JsonPath.read(body, "$.projection.startingValue.provId"));
        assertThat((String) JsonPath.read(starting, "$.inputs[0].provId"))
                .isEqualTo(JsonPath.read(body, "$.holdings[0].value.provId"));
        String monthly = provenance(jonas, JsonPath.read(body, "$.projection.monthlyContribution.provId"));
        assertThat((String) JsonPath.read(monthly, "$.inputs[1].provId"))
                .isEqualTo(JsonPath.read(body, "$.savingsPlans[0].monthlyAmount.provId"));
        assertThat((String) JsonPath.read(provenance(jonas, JsonPath.read(body, "$.holdings[0].value.provId")),
                "$.value")).isEqualTo("2004.76");
    }

    @Test
    void marcoHasNoInvestmentsAndNoProjection() throws Exception {
        mvc.perform(get("/api/overview/wealth").session(login(mvc, "p-marco")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings").isEmpty())
                .andExpect(jsonPath("$.savingsPlans").isEmpty())
                .andExpect(jsonPath("$.projection").doesNotExist());
    }

    @Test
    void withoutAPersonaSessionTheEndpointIsClosed() throws Exception {
        mvc.perform(get("/api/overview/wealth")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/overview/wealth").session(login(mvc, "advisor-kaya")))
                .andExpect(status().isForbidden());
    }
}
