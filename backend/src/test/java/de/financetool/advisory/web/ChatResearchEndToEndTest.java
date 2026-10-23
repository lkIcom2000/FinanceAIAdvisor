package de.financetool.advisory.web;

import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.financetool.PostgresIntegrationTest;
import de.financetool.advisory.domain.NoValidSource;
import de.financetool.testing.StubLlmConfiguration.StubLlm;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;

/**
 * Story 3.1 under the research profile: the first real {@code AuditWriter} caller's record in the audit export
 * (Epic 1 A7), and persona reset removing the chat rows while the audit stays.
 */
@ActiveProfiles("research")
class ChatResearchEndToEndTest extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    String[] turn(MockHttpSession session, String text) throws Exception {
        String saved = mvc.perform(post("/api/chat/messages").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String questionId = JsonPath.read(saved, "$.id");
        String answer = mvc.perform(post("/api/chat/messages/{id}/answer", questionId).session(session).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new String[] {questionId, JsonPath.read(answer, "$.id")};
    }

    @Test
    void theExportHoldsExactlyTheTurnsResponseRecord() throws Exception {
        String turnId = turn(login(mvc, "p-lena"), "What is an ETF?")[1];

        String export = mvc.perform(get("/api/research/audit/export").session(login(mvc, "researcher")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> lines = Arrays.stream(export.split("\n"))
                .filter(line -> line.contains(turnId))
                .toList();

        assertThat(lines).singleElement().satisfies(line -> {
            Map<String, Object> record = JsonPath.read(line, "$");
            assertThat(record).containsEntry("kind", "RESPONSE").containsEntry("turnId", turnId)
                    .containsEntry("personaId", "p-lena");
            assertThat((String) JsonPath.read(line, "$.payload.body.outcome")).isEqualTo("INFORM");
            assertThat((String) JsonPath.read(line, "$.payload.body.finalText")).isEqualTo(NoValidSource.TEXT);
            assertThat((String) JsonPath.read(line, "$.payload.body.firstDraft")).isEqualTo(StubLlm.DEFAULT_TEXT);
        });
    }

    @Test
    void resetRemovesThePersonasChatButNotItsAudit() throws Exception {
        MockHttpSession marco = login(mvc, "p-marco");
        String turnId = turn(marco, "What is a savings plan?")[1];
        turn(login(mvc, "p-lena"), "What is a bond?");
        long lenaMessages = count("SELECT count(*) FROM messages WHERE persona_id = 'p-lena'");

        mvc.perform(post("/api/research/personas/{id}/reset", "p-marco").session(login(mvc, "researcher"))
                .with(csrf())).andExpect(status().isNoContent());

        for (String table : List.of("messages", "conversations", "dialog_states")) {
            assertThat(count("SELECT count(*) FROM " + table + " WHERE persona_id = 'p-marco'")).as(table).isZero();
        }
        assertThat(count("SELECT count(*) FROM messages WHERE persona_id = 'p-lena'")).isEqualTo(lenaMessages);
        assertThat(count("SELECT count(*) FROM audit_records WHERE turn_id = '" + turnId + "'::uuid")).isEqualTo(1);
        mvc.perform(get("/api/chat").session(marco)).andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").isEmpty());
    }

    long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
