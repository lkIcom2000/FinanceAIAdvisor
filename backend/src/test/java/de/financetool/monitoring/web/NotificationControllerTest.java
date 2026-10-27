package de.financetool.monitoring.web;

import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.financetool.PostgresIntegrationTest;
import de.financetool.monitoring.Monitoring;
import de.financetool.research.application.ResetPersonaUseCase;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContextFactory;
import de.financetool.shared.domain.PersonaId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Story 8.3: the Notifications API on top of a real monitoring run. */
@ActiveProfiles("research")
class NotificationControllerTest extends PostgresIntegrationTest {

    static final PersonaId LENA = new PersonaId("p-lena");
    static final PersonaId MARCO = new PersonaId("p-marco");

    @Autowired
    Monitoring monitoring;

    @Autowired
    RequestContextFactory contexts;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ResetPersonaUseCase resetPersona;

    /** The test database is shared by every test class: leave the personas as the fixtures define them. */
    @AfterEach
    void restoreFixtureState() {
        Stream.of(LENA, MARCO).forEach(resetPersona::reset);
    }

    Monitoring.Run run(PersonaId persona) {
        resetPersona.reset(persona);
        PersonaScope scope = new PersonaScope(persona);
        return transaction.execute(tx -> monitoring.run(contexts.forPersona(scope, "run-" + UUID.randomUUID()),
                scope));
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> list(MockHttpSession session) throws Exception {
        String json = mvc.perform(get("/api/notifications").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readValue(json, List.class);
    }

    @Test
    void uj1LenaSeesHerThreeNotificationsNewestFirstWithFiguresAndStatus() throws Exception {
        Monitoring.Run run = run(LENA);

        List<Map<String, Object>> items = list(login(mvc, "p-lena"));

        assertThat(items).extracting(i -> UUID.fromString((String) i.get("id")))
                .containsExactlyElementsOf(run.notificationIds().reversed());
        assertThat(items).extracting(i -> i.get("status"))
                .containsExactly("FOR_YOUR_INFORMATION", "RECOMMENDATION", "NEEDS_YOUR_OK");
        assertThat(items).allSatisfy(i -> {
            assertThat(i.get("read")).isEqualTo(false);
            assertThat((List<Map<String, Object>>) i.get("figures")).isNotEmpty().allSatisfy(f ->
                    assertThat(f).containsKeys("provId", "label", "value", "unit"));
        });
        assertThat(items.get(1).get("question")).asString().startsWith("Should I pay my premium for");
        assertThat(items.get(0)).doesNotContainKey("question");
        assertThat(items.get(2)).doesNotContainKey("question");
    }

    @Test
    @SuppressWarnings("unchecked")
    void figuresAreTheStoredEvidenceInCitedOrderWithoutMissingOrForeignKeys() throws Exception {
        String marcos = jdbc.queryForObject("SELECT prov_id FROM evidence_items WHERE prov_id = ANY(?) LIMIT 1",
                String.class, (Object) cited(run(MARCO).notificationIds().getFirst()).toArray(String[]::new));
        UUID id = run(LENA).notificationIds().getFirst();
        List<String> own = cited(id);
        List<String> planted = new java.util.ArrayList<>(own.reversed());
        planted.add(1, "00000000deadbeef");
        planted.add(marcos);
        jdbc.update("UPDATE notifications SET cited_prov_ids = ?::jsonb WHERE id = ?",
                JsonMapper.builder().build().writeValueAsString(planted), id);

        Map<String, Object> item = list(login(mvc, "p-lena")).stream()
                .filter(i -> i.get("id").equals(id.toString())).findFirst().orElseThrow();
        List<Map<String, Object>> figures = (List<Map<String, Object>>) item.get("figures");

        assertThat(figures).extracting(f -> f.get("provId")).containsExactlyElementsOf(own.reversed());
        for (Map<String, Object> f : figures) {
            Map<String, Object> stored = JsonMapper.builder().build().readValue(jdbc.queryForObject(
                    "SELECT content::text FROM evidence_items WHERE prov_id = ?", String.class, f.get("provId")),
                    Map.class);
            assertThat(f.get("label")).isEqualTo(stored.get("label"));
            assertThat(new java.math.BigDecimal((String) f.get("value")))
                    .isEqualByComparingTo(new java.math.BigDecimal(stored.get("value").toString()));
            assertThat(f.get("unit")).isEqualTo(stored.get("unit"));
        }
    }

    List<String> cited(UUID notificationId) {
        return List.of(JsonMapper.builder().build().readValue(jdbc.queryForObject(
                "SELECT cited_prov_ids::text FROM notifications WHERE id = ?", String.class, notificationId),
                String[].class));
    }

    @Test
    void anExecutedActionShowsDoneForYou() throws Exception {
        Monitoring.Run run = run(LENA);
        jdbc.update("UPDATE actions SET state = 'EXECUTED' WHERE id = ?", run.actions().getFirst().actionId());

        List<Map<String, Object>> items = list(login(mvc, "p-lena"));
        assertThat(items).extracting(i -> i.get("status"))
                .containsExactly("FOR_YOUR_INFORMATION", "RECOMMENDATION", "DONE_FOR_YOU");
        assertThat(items.get(2).get("body")).asString()
                .endsWith(" You confirmed it, and Fine has made the transfer.");
        assertThat(items.get(1).get("body")).asString().doesNotContain("You confirmed it");
    }

    @Test
    void notificationsAfterTheSimulatedDateStayHiddenUntilTheDateReachesThem() throws Exception {
        Monitoring.Run run = run(LENA);
        UUID later = run.notificationIds().getFirst();
        jdbc.update("UPDATE notifications SET as_of = as_of + 1 WHERE id = ?", later);

        assertThat(list(login(mvc, "p-lena"))).extracting(i -> i.get("id"))
                .hasSize(2).doesNotContain(later.toString());
    }

    @Test
    void markingReadIsIdempotentAndKeepsTheFirstReadTime() throws Exception {
        UUID id = run(LENA).notificationIds().getFirst();
        MockHttpSession lena = login(mvc, "p-lena");

        mvc.perform(post("/api/notifications/{id}/read", id).session(lena).with(csrf()))
                .andExpect(status().isNoContent());
        Object first = jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", Object.class, id);
        mvc.perform(post("/api/notifications/{id}/read", id).session(lena).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", Object.class, id))
                .isNotNull().isEqualTo(first);
        assertThat(list(lena)).filteredOn(i -> i.get("id").equals(id.toString()))
                .singleElement().satisfies(i -> assertThat(i.get("read")).isEqualTo(true));
    }

    @Test
    void anotherPersonasOrAnUnknownIdIsNotFoundAndNothingChanges() throws Exception {
        UUID marcos = run(MARCO).notificationIds().getFirst();
        MockHttpSession lena = login(mvc, "p-lena");

        for (String id : List.of(marcos.toString(), UUID.randomUUID().toString(), "not-an-id")) {
            mvc.perform(post("/api/notifications/{id}/read", id).session(lena).with(csrf()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.instance").value("/api/notifications"));
        }
        assertThat(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", Object.class, marcos))
                .isNull();
    }

    @Test
    void aFreshPersonaHasNoNotifications() throws Exception {
        resetPersona.reset(LENA);

        mvc.perform(get("/api/notifications").session(login(mvc, "p-lena")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }
}
