package de.financetool.research.web;

import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.financetool.PostgresIntegrationTest;
import de.financetool.audit.EvidenceOrigin;
import de.financetool.core.CoreGateway;
import de.financetool.research.application.ResetPersonaUseCase;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.RequestContextFactory;
import de.financetool.shared.domain.PersonaId;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/** Story 8.5: the researcher prepares a persona for a study session from a named preset. */
@ActiveProfiles("research")
class StudyResearchControllerTest extends PostgresIntegrationTest {

    static final PersonaId JONAS = new PersonaId("p-jonas");
    static final PersonaId MARCO = new PersonaId("p-marco");
    static final PersonaId LENA = new PersonaId("p-lena");

    @Autowired
    ResetPersonaUseCase resetPersona;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    RequestContextFactory contexts;

    @Autowired
    CoreGateway core;

    /** The test database is shared by every test class: leave the personas as the fixtures define them. */
    @AfterEach
    void restoreFixtureState() {
        Stream.of(JONAS, MARCO, LENA).forEach(resetPersona::reset);
    }

    ResultActions prepare(String presetId, MockHttpSession session) throws Exception {
        return mvc.perform(post("/api/research/study/prepare/{id}", presetId).session(session).with(csrf()));
    }

    @Test
    void jonasPresetResetsDatesAndMonitorsAndSaysWhatHeWillSee() throws Exception {
        MockHttpSession researcher = login(mvc, "researcher");
        dirty(JONAS);

        prepare("uj4-duplicate-coverage-jonas", researcher).andExpect(status().isOk())
                .andExpect(jsonPath("$.presetId").value("uj4-duplicate-coverage-jonas"))
                .andExpect(jsonPath("$.title").value("UJ-4 duplicate coverage (Jonas)"))
                .andExpect(jsonPath("$.personaId").value("p-jonas"))
                .andExpect(jsonPath("$.simulatedDate").value("2026-10-15"))
                .andExpect(jsonPath("$.notifications", hasSize(2)))
                .andExpect(jsonPath("$.activeRules").value(0))
                .andExpect(jsonPath("$.summary")
                        .value("2 notifications (1 Recommendation, 1 Needs your OK), 0 rules active"));

        assertThat(jdbc.queryForObject("select simulated_date::text from persona_state where persona_id = 'p-jonas'",
                String.class)).isEqualTo("2026-10-15");
        assertThat(jdbc.queryForList("select type from triggers where persona_id = 'p-jonas'", String.class))
                .containsExactlyInAnyOrder("DUPLICATE_COVERAGE", "EF_BELOW_TARGET");
    }

    @Test
    void marcoPresetShowsTheFundTopUpThatNeedsHisOk() throws Exception {
        prepare("uj2-advice-marco", login(mvc, "researcher")).andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications[*].status").value(hasItem("NEEDS_YOUR_OK")));
    }

    @Test
    void theSamePresetPreparedTwiceGivesTheSameState() throws Exception {
        MockHttpSession researcher = login(mvc, "researcher");
        for (PersonaId persona : List.of(JONAS, MARCO)) {
            String preset = persona.equals(JONAS) ? "uj4-duplicate-coverage-jonas" : "uj2-advice-marco";
            String firstSummary = summary(prepare(preset, researcher));
            Map<String, Object> first = state(persona);
            dirty(persona);
            String secondSummary = summary(prepare(preset, researcher));
            Map<String, Object> second = state(persona);

            assertThat(secondSummary).as(preset).isEqualTo(firstSummary).doesNotStartWith("0 notifications");

            assertThat(second).as(preset).isEqualTo(first);
            assertThat((List<?>) first.get("triggers")).as(preset).isNotEmpty();
        }
    }

    @Test
    void anUnknownPresetIs404AndAParticipantIsForbidden() throws Exception {
        prepare("nope", login(mvc, "researcher")).andExpect(status().isNotFound());
        prepare("uj4-duplicate-coverage-jonas", login(mvc, "p-jonas")).andExpect(status().isForbidden());
    }

    @Test
    void lenaPresetSetsUpHerRuleAndFineMovesTheMoneyItself() throws Exception {
        prepare("uj1-shortfall-lena", login(mvc, "researcher")).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRules").value(1))
                .andExpect(jsonPath("$.summary").value("3 notifications (1 For your information, 1 Recommendation,"
                        + " 1 Done for you), 1 rule active"));

        Map<String, Object> action = jdbc.queryForMap("select a.id, a.state, a.level, a.mandate_id, m.template"
                + " from actions a join mandates m on m.id = a.mandate_id where a.persona_id = 'p-lena'");
        assertThat(action).containsEntry("state", "EXECUTED").containsEntry("level", "L3")
                .containsEntry("template", "SAFETY_CUSHION");
        assertThat(jdbc.queryForObject("select count(*) from transactions where persona_id = 'p-lena'"
                + " and origin = 'BANK_PORT'", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from actions where persona_id = 'p-lena'", Long.class))
                .isOne();

        mvc.perform(get("/api/notifications").session(login(mvc, "p-lena"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.status == 'DONE_FOR_YOU')].title")
                        .value(hasItem("I moved 90.00 EUR to your current account")))
                .andExpect(jsonPath("$[?(@.status == 'DONE_FOR_YOU')].body").value(hasItem(startsWith(
                        "I moved 90.00 EUR from your overnight deposit to your current account, as your rule"
                                + " \"Keep a safety cushion in my current account\" allows. Your forecast shows"))))
                .andExpect(jsonPath("$[?(@.status == 'DONE_FOR_YOU')].body")
                        .value(hasItem(not(containsString("You confirmed it")))));
    }

    @ParameterizedTest
    @ValueSource(strings = { "uj4-duplicate-coverage-jonas", "uj2-advice-marco", "uj1-shortfall-lena" })
    void everyPresetPrepares(String presetId) throws Exception {
        prepare(presetId, login(mvc, "researcher")).andExpect(status().isOk());
    }

    /** A later date and a stray Trigger: only a reset removes both. */
    void dirty(PersonaId persona) {
        jdbc.update("update persona_state set simulated_date = date '2026-12-01' where persona_id = ?", persona.value());
        jdbc.update("insert into triggers (id, persona_id, type, subject_id, period, as_of)"
                + " values (gen_random_uuid(), ?, 'RULE_CHANGE', 'stray', '2099', date '2026-12-01')", persona.value());
    }

    static String summary(ResultActions result) throws Exception {
        result.andExpect(status().isOk());
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.summary");
    }

    /** Triggers, Actions, Notifications and Core outputs without generated ids and timestamps. */
    Map<String, Object> state(PersonaId persona) {
        String id = persona.value();
        List<Map<String, Object>> triggers = jdbc.queryForList("select type, subject_id, period, as_of from triggers"
                + " where persona_id = ? order by type, subject_id, period", id);
        List<Map<String, Object>> actions = jdbc.queryForList("select type, regulatory_class, amount,"
                + " source_account_id, target_account_id, state, level, capping_criteria::text, as_of, reason"
                + " from actions where persona_id = ? order by type, amount", id);
        List<Map<String, Object>> notifications = jdbc.queryForList("select n.outcome, n.title, n.body,"
                + " n.cited_prov_ids::text, n.as_of, n.question, t.type as trigger_type, a.type as action_type"
                + " from notifications n join triggers t on t.id = n.trigger_id left join actions a on a.id = n.action_id"
                + " where n.persona_id = ? order by n.title, n.body", id);
        List<Object> coreOutputs = transaction.execute(tx -> {
            RequestContext context = contexts.forPersona(new PersonaScope(persona), null);
            return List.of(core.liquidity(context, EvidenceOrigin.MONITORING),
                    core.insurance(context, EvidenceOrigin.MONITORING),
                    core.profileFigures(context, EvidenceOrigin.MONITORING));
        });
        return Map.of("triggers", triggers, "actions", actions, "notifications", notifications, "core", coreOutputs);
    }
}
