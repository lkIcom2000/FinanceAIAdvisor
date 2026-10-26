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

/** Story 2.5: {@code GET /api/overview/home} end to end: the summary and "Your insurance". */
class HomeOverviewEndToEndTest extends PostgresIntegrationTest {

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
        transaction.executeWithoutResult(tx -> clock.setSimulatedDate(LENA, START));
    }

    String get200(MockHttpSession session, String path) throws Exception {
        return mvc.perform(get(path).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void lenasSummaryHasTheLiquidityTabsFiguresWithTheSameKeys() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        String home = get200(lena, "/api/overview/home");
        String liquidity = get200(lena, "/api/overview/liquidity");

        assertThat((String) JsonPath.read(home, "$.currentAccount.value")).isEqualTo("1974.00");
        assertThat((String) JsonPath.read(home, "$.lowestBalance.amount.value")).isEqualTo("-90.00");
        assertThat((String) JsonPath.read(home, "$.lowestBalance.date")).isEqualTo("2026-11-01");
        assertThat((String) JsonPath.read(home, "$.shortfall.wholeEuros")).isEqualTo("-90");
        assertThat((String) JsonPath.read(home, "$.shortfall.date")).isEqualTo("2026-11-01");

        for (String path : List.of("$.currentAccount.provId", "$.lowestBalance.amount.provId",
                "$.shortfall.amount.provId", "$.emergencyFund.current.provId", "$.emergencyFund.target.provId",
                "$.emergencyFund.months.provId")) {
            assertThat((String) JsonPath.read(home, path)).as(path).isEqualTo(JsonPath.read(liquidity, path));
        }
        assertThat((String) JsonPath.read(home, "$.emergencyFund.current.value"))
                .isEqualTo(JsonPath.read(liquidity, "$.emergencyFund.current.value"));

        // Lena: car and liability, nothing duplicated, no occupational disability cover.
        assertThat((List<String>) JsonPath.read(home, "$.insurance.contracts[*].type"))
                .containsExactlyInAnyOrder("Car insurance", "Private liability insurance");
        assertThat((List<String>) JsonPath.read(home, "$.insurance.contracts[?(@.type == 'Car insurance')]"
                + ".nextDueDate")).containsExactly("2026-11-01");
        assertThat((List<String>) JsonPath.read(home, "$.insurance.contracts[?(@.type == 'Car insurance')]"
                + ".paymentInterval")).containsExactly("yearly");
        assertThat((List<String>) JsonPath.read(home, "$.insurance.flags[*].kind")).containsExactly("MISSING_COVER");
        assertThat((List<String>) JsonPath.read(home, "$.insurance.flags[*].coverage"))
                .containsExactly("occupational disability");

        // Every key is stored once as evidence for Lena (overview, or turn if an assessment card keyed it first), and the
        // same call gives the same keys.
        List<String> provIds = JsonPath.read(home, "$..provId");
        for (String provId : provIds) {
            assertThat(jdbc.queryForList("SELECT origin FROM evidence_items WHERE prov_id = ? AND persona_id = ?",
                    String.class, provId, "p-lena")).as(provId).hasSize(1).first().isIn("overview", "turn");
        }
        assertThat((List<String>) JsonPath.read(get200(lena, "/api/overview/home"), "$..provId")).isEqualTo(provIds);
    }

    @Test
    void jonasSeesTheDuplicateLiabilityCoverCitingBothContracts() throws Exception {
        MockHttpSession jonas = login(mvc, "p-jonas");
        String home = get200(jonas, "/api/overview/home");

        List<String> contractIds = JsonPath.read(home, "$.insurance.contracts[*].id");
        assertThat(contractIds).hasSize(2);
        assertThat((List<String>) JsonPath.read(home, "$.insurance.flags[?(@.kind == 'DUPLICATE_COVERAGE')]"
                + ".coverage")).containsExactly("liability");
        assertThat((List<List<String>>) JsonPath.read(home, "$.insurance.flags[?(@.kind == 'DUPLICATE_COVERAGE')]"
                + ".contractIds")).singleElement().satisfies(ids ->
                        assertThat(ids).containsExactlyInAnyOrderElementsOf(contractIds));
        assertThat((List<String>) JsonPath.read(home, "$.insurance.flags[?(@.kind == 'MISSING_COVER')].coverage"))
                .containsExactly("occupational disability");

        // The family policy: paid by the parents, no premium figure.
        assertThat((List<String>) JsonPath.read(home, "$.insurance.contracts[?(@.coInsured == true)].policyholder"))
                .containsExactly("Parents of Jonas");
        assertThat((List<Object>) JsonPath.read(home, "$.insurance.contracts[?(@.coInsured == true)].premium"))
                .containsOnlyNulls();
        assertThat((List<String>) JsonPath.read(home, "$.insurance.contracts[?(@.coInsured == false)].premium.value"))
                .containsExactly("48.00");

        String provId = JsonPath.read(home, "$.insurance.flags[0].provId");
        String provenance = get200(jonas, "/api/provenance/" + provId);
        assertThat((String) JsonPath.read(provenance, "$.source.description"))
                .startsWith("Calculated by Fine's calculation engine (duplicate coverage check)");
        assertThat((List<String>) JsonPath.read(provenance, "$.inputs[*].text")).containsExactly(
                "Private liability insurance, policyholder Jonas",
                "Private liability insurance, co-insured on the policy of Parents of Jonas");
        assertThat((List<String>) JsonPath.read(provenance, "$.assumptions")).contains("Whether the family policy"
                + " still covers you depends on its conditions, such as having your own household.");

        // Another persona cannot read Jonas's flag.
        mvc.perform(get("/api/provenance/{id}", provId).session(login(mvc, "p-lena")))
                .andExpect(status().isNotFound());
    }

    @Test
    void timHasNoContractsAndBothMissingCovers() throws Exception {
        mvc.perform(get("/api/overview/home").session(login(mvc, "p-tim")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insurance.contracts").isEmpty())
                .andExpect(jsonPath("$.insurance.flags[*].kind").value(
                        org.hamcrest.Matchers.contains("MISSING_COVER", "MISSING_COVER")))
                .andExpect(jsonPath("$.insurance.flags[*].coverage").value(
                        org.hamcrest.Matchers.contains("liability", "occupational disability")));
    }

    @Test
    void withoutAPersonaSessionTheEndpointIsClosed() throws Exception {
        mvc.perform(get("/api/overview/home")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/overview/home").session(login(mvc, "advisor-kaya")))
                .andExpect(status().isForbidden());
    }
}
