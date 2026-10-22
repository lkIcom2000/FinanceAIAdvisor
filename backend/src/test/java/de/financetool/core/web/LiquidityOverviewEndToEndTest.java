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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.support.TransactionTemplate;

/** Story 2.3: {@code GET /api/overview/liquidity} end to end, with every figure stored and traceable. */
class LiquidityOverviewEndToEndTest extends PostgresIntegrationTest {

    static final PersonaId LENA = new PersonaId("p-lena");
    static final LocalDate START = LocalDate.of(2026, 10, 15);

    @Autowired
    PersonaClock clock;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void lenaAtTheFixtureStart() {
        simulatedDate(START);
    }

    void simulatedDate(LocalDate date) {
        transaction.executeWithoutResult(tx -> clock.setSimulatedDate(LENA, date));
    }

    String liquidity(MockHttpSession session) throws Exception {
        return mvc.perform(get("/api/overview/liquidity").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    String provenance(MockHttpSession session, String provId) throws Exception {
        return mvc.perform(get("/api/provenance/{id}", provId).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void lenaSeesHerBalancesTheShortfallOnTheFirstOfNovemberAndTheEmergencyFund() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        String body = liquidity(lena);

        assertThat((String) JsonPath.read(body, "$.currentAccount.value")).isEqualTo("1974.00");
        assertThat((String) JsonPath.read(body, "$.overnightDeposit.unit")).isEqualTo("EUR");
        assertThat(new BigDecimal((String) JsonPath.read(body, "$.liquidAssets.value"))).isEqualByComparingTo(
                new BigDecimal((String) JsonPath.read(body, "$.currentAccount.value"))
                        .add(new BigDecimal((String) JsonPath.read(body, "$.overnightDeposit.value"))));
        assertThat((String) JsonPath.read(body, "$.shortfall.amount.value")).isEqualTo("-90.00");
        assertThat((String) JsonPath.read(body, "$.shortfall.date")).isEqualTo("2026-11-01");
        assertThat((String) JsonPath.read(body, "$.shortfall.wholeEuros")).isEqualTo("-90");
        assertThat((String) JsonPath.read(body, "$.lowestBalance.amount.value")).isEqualTo("-90.00");
        assertThat((String) JsonPath.read(body, "$.lowestBalance.date")).isEqualTo("2026-11-01");
        assertThat((String) JsonPath.read(body, "$.emergencyFund.months.value")).isEqualTo("3");
        assertThat((List<?>) JsonPath.read(body, "$.forecast.points")).hasSize(92);
        assertThat((String) JsonPath.read(body, "$.forecast.points[0].date")).isEqualTo("2026-10-16");
        assertThat((List<?>) JsonPath.read(body, "$.forecast.assumptions")).isNotEmpty();
        assertThat((String) JsonPath.read(body, "$.forecast.provId"))
                .isEqualTo(JsonPath.read(body, "$.forecast.expectedEnd.provId"));

        // Every figure is stored as evidence for Lena, with origin overview.
        List<String> provIds = JsonPath.read(body, "$..provId");
        assertThat(provIds).hasSizeGreaterThanOrEqualTo(12).allSatisfy(id -> assertThat(id).matches("[0-9a-f]{16}"));
        for (String provId : provIds) {
            assertThat(jdbc.queryForList("SELECT origin FROM evidence_items WHERE prov_id = ? AND persona_id = ?",
                    String.class, provId, "p-lena")).containsExactly("overview");
        }
        // The same call again gives the same keys.
        assertThat((List<String>) JsonPath.read(liquidity(lena), "$..provId")).isEqualTo(provIds);
    }

    @Test
    void theForecastsInputsDrillDownToTheirOwnProvenance() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        String body = liquidity(lena);
        String forecast = provenance(lena, JsonPath.read(body, "$.forecast.provId"));

        assertThat((String) JsonPath.read(forecast, "$.source.description"))
                .startsWith("Calculated by Fine's calculation engine (cash-flow forecast)");
        assertThat((List<String>) JsonPath.read(forecast, "$.inputs[?(@.provId)].label"))
                .containsExactly("Current account balance on 15 Oct 2026", "Average monthly variable spending");
        assertThat((String) JsonPath.read(forecast, "$.ruleParameters[0].id"))
                .isEqualTo("forecast_variable_spending_band");
        assertThat((String) JsonPath.read(forecast, "$.ruleParameters[0].validFrom")).isEqualTo("2020-01-01");
        assertThat((String) JsonPath.read(forecast, "$.checks[0].id")).isEqualTo("data-confidence");
        assertThat((Boolean) JsonPath.read(forecast, "$.checks[0].passed")).isTrue();

        List<String> inputIds = JsonPath.read(forecast, "$.inputs[?(@.provId)].provId");
        assertThat(inputIds.getFirst()).isEqualTo(JsonPath.read(body, "$.currentAccount.provId"));
        assertThat(inputIds.get(1)).isEqualTo(JsonPath.read(body, "$.variableSpending.provId"));
        assertThat((String) JsonPath.read(provenance(lena, inputIds.get(1)), "$.value")).isEqualTo("930.00");
        assertThat((String) JsonPath.read(provenance(lena, inputIds.getFirst()), "$.value")).isEqualTo("1974.00");

        String shortfall = provenance(lena, JsonPath.read(body, "$.shortfall.amount.provId"));
        assertThat((String) JsonPath.read(shortfall, "$.inputs[0].provId"))
                .isEqualTo(JsonPath.read(body, "$.forecast.provId"));
        assertThat((String) JsonPath.read(shortfall, "$.checks[0].id")).isEqualTo("data-confidence");

        String target = provenance(lena, JsonPath.read(body, "$.emergencyFund.target.provId"));
        assertThat((List<String>) JsonPath.read(target, "$.ruleParameters[*].id"))
                .containsExactly("emergency_fund_months_max", "emergency_fund_months_min");
        assertThat((List<String>) JsonPath.read(target, "$.ruleParameters[*].version")).containsOnly("1");
        assertThat((String) JsonPath.read(target, "$.inputs[0].provId"))
                .isEqualTo(JsonPath.read(body, "$.overnightDeposit.provId"));
    }

    @Test
    void theShortfallPersonaAndMarcoGetTheirOwnForecast() throws Exception {
        String shortfall = liquidity(login(mvc, "p-shortfall"));
        assertThat((String) JsonPath.read(shortfall, "$.shortfall.amount.value")).isEqualTo("-82.04");
        assertThat((String) JsonPath.read(shortfall, "$.shortfall.date")).isEqualTo("2026-11-28");
        assertThat((String) JsonPath.read(shortfall, "$.shortfall.wholeEuros")).isEqualTo("-82");

        mvc.perform(get("/api/overview/liquidity").session(login(mvc, "p-marco")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortfall").doesNotExist())
                .andExpect(jsonPath("$.emergencyFund.current.value").value("300.00"))
                .andExpect(jsonPath("$.emergencyFund.target.value").value("6000.00"));

        String tim = liquidity(login(mvc, "p-tim"));
        assertThat((Object) JsonPath.read(tim, "$.overnightDeposit")).isNull();
        assertThat((String) JsonPath.read(tim, "$.emergencyFund.current.value")).isEqualTo("0.00");
        assertThat((String) JsonPath.read(tim, "$.liquidAssets.value"))
                .isEqualTo(JsonPath.read(tim, "$.currentAccount.value"));
    }

    @Test
    void aDatePastTheDueDateRollsThePremiumForward() throws Exception {
        simulatedDate(LocalDate.of(2026, 11, 5));
        MockHttpSession lena = login(mvc, "p-lena");
        String body = liquidity(lena);

        assertThat((String) JsonPath.read(body, "$.forecast.points[0].date")).isEqualTo("2026-11-06");
        String forecast = provenance(lena, JsonPath.read(body, "$.forecast.provId"));
        assertThat((List<String>) JsonPath.read(forecast, "$.inputs[*].text"))
                .contains("−620.00 EUR, next due on 1 Nov 2027");
    }

    @Test
    void withoutAPersonaSessionTheEndpointIsClosed() throws Exception {
        mvc.perform(get("/api/overview/liquidity")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/overview/liquidity").session(login(mvc, "advisor-kaya")))
                .andExpect(status().isForbidden());
    }
}
