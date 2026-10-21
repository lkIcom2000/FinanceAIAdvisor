package de.financetool.llm.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import de.financetool.llm.Embeddings;
import de.financetool.llm.LlmMessage;
import de.financetool.llm.LlmResult;
import de.financetool.llm.LlmRole;
import de.financetool.shared.config.AgentConfiguration;
import de.financetool.shared.config.ComplianceLevel;
import de.financetool.shared.config.DataExposureLevel;
import de.financetool.shared.config.DerivedFrom;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The Ollama adapter against a mock Ollama (Story 1.6 matrix). */
class OllamaLlmAdapterTest {

    static final String URL = "http://ollama:11434";
    static final AgentConfiguration.Decoding DECODING = new AgentConfiguration.Decoding(0.0, 0.9, 42L, 4096, 700);

    static AgentConfiguration agent(String phrasing) {
        return new AgentConfiguration("reference",
                new DerivedFrom("none", "none", LocalDate.of(2026, 10, 20), "PRD starting value"),
                new AgentConfiguration.Llm(phrasing, "nlu-model", "judge-model", "bge-m3"), true, "advisor",
                ComplianceLevel.A_B_C, DataExposureLevel.AGGREGATED, DECODING,
                new AgentConfiguration.Seeds(20261020L, 42L));
    }

    static final ModelLock LOCK = new ModelLock(List.of(
            new ModelLock.Entry("phrase-a", "sha256:a", "gguf-a"),
            new ModelLock.Entry("phrase-b", "sha256:b", "gguf-b"),
            new ModelLock.Entry("nlu-model", "sha256:n", "gguf-n"),
            new ModelLock.Entry("judge-model", "sha256:j", "gguf-j"),
            new ModelLock.Entry("bge-m3", "sha256:e", "gguf-e")));

    MockRestServiceServer server;

    OllamaLlmAdapter adapter(AgentConfiguration agent, Optional<ModelLock> lock) {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        server = MockRestServiceServer.bindTo(builder).build();
        return new OllamaLlmAdapter(new OllamaClient(builder.build()), agent, lock, URL);
    }

    static String chat(String content, int promptTokens, String doneReason) {
        return "{\"model\":\"x\",\"message\":{\"role\":\"assistant\",\"content\":" + quote(content) + "},"
                + "\"done\":true,\"done_reason\":\"" + doneReason + "\",\"prompt_eval_count\":" + promptTokens + "}";
    }

    static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    void generateUsesTheTagOfTheRoleAndTheDecoding() {
        OllamaLlmAdapter adapter = adapter(agent("phrase-a"), Optional.of(LOCK));
        server.expect(requestTo(URL + "/api/chat")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("phrase-a"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("Hello"))
                .andExpect(jsonPath("$.options.seed").value(42))
                .andExpect(jsonPath("$.options.num_ctx").value(4096))
                .andExpect(jsonPath("$.options.num_predict").value(700))
                .andExpect(jsonPath("$.format").doesNotExist())
                .andRespond(withSuccess(chat("Hi", 12, "stop"), MediaType.APPLICATION_JSON));

        LlmResult result = adapter.generate(LlmRole.PHRASING,
                List.of(LlmMessage.system("Be brief."), LlmMessage.user("Hello")), DECODING, null);

        assertThat(result.text()).isEqualTo("Hi");
        assertThat(result.modelTag()).isEqualTo("phrase-a");
        assertThat(result.digest()).isEqualTo("sha256:a");
        assertThat(result.promptTokens()).isEqualTo(12);
        assertThat(result.truncated()).isFalse();
        assertThat(result.decoding()).isEqualTo(DECODING);
        assertThat(result.latency()).isNotNull();
        server.verify();
    }

    @Test
    void aSchemaIsPassedAsFormat() {
        OllamaLlmAdapter adapter = adapter(agent("phrase-a"), Optional.of(LOCK));
        server.expect(requestTo(URL + "/api/chat"))
                .andExpect(jsonPath("$.model").value("nlu-model"))
                .andExpect(jsonPath("$.format.type").value("object"))
                .andExpect(jsonPath("$.format.required[0]").value("ok"))
                .andRespond(withSuccess(chat("{\"ok\":true}", 5, "stop"), MediaType.APPLICATION_JSON));

        adapter.generate(LlmRole.NLU, List.of(LlmMessage.user("x")), DECODING, OllamaLlmAdapter.SMOKE_SCHEMA);
        server.verify();
    }

    @Test
    void lengthStopsAndFullContextsAreTruncated() {
        OllamaLlmAdapter adapter = adapter(agent("phrase-a"), Optional.of(LOCK));
        server.expect(requestTo(URL + "/api/chat"))
                .andRespond(withSuccess(chat("cut", 10, "length"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "/api/chat"))
                .andRespond(withSuccess(chat("full", 4096, "stop"), MediaType.APPLICATION_JSON));

        assertThat(adapter.generate(LlmRole.PHRASING, List.of(LlmMessage.user("x")), DECODING, null).truncated())
                .isTrue();
        assertThat(adapter.generate(LlmRole.PHRASING, List.of(LlmMessage.user("x")), DECODING, null).truncated())
                .isTrue();
    }

    @Test
    void anotherConfigurationSwitchesThePhrasingModelWithoutCodeChange() {
        for (String tag : List.of("phrase-a", "phrase-b")) {
            OllamaLlmAdapter adapter = adapter(agent(tag), Optional.of(LOCK));
            server.expect(requestTo(URL + "/api/chat")).andExpect(jsonPath("$.model").value(tag))
                    .andRespond(withSuccess(chat("ok", 3, "stop"), MediaType.APPLICATION_JSON));

            LlmResult result = adapter.generate(LlmRole.PHRASING, List.of(LlmMessage.user("x")), DECODING, null);

            assertThat(result.modelTag()).isEqualTo(tag);
            assertThat(result.digest()).isEqualTo(LOCK.find(tag).orElseThrow().digest());
        }
    }

    @Test
    void embedUsesTheEmbeddingModelWithoutTruncation() {
        OllamaLlmAdapter adapter = adapter(agent("phrase-a"), Optional.of(LOCK));
        server.expect(requestTo(URL + "/api/embed"))
                .andExpect(content().json("{\"model\":\"bge-m3\",\"input\":[\"a\",\"b\"],\"truncate\":false}"))
                .andRespond(withSuccess("{\"embeddings\":[[0.1,0.2],[0.3,0.4]]}", MediaType.APPLICATION_JSON));

        Embeddings embeddings = adapter.embed(List.of("a", "b"));

        assertThat(embeddings.vectors()).hasSize(2);
        assertThat(embeddings.vectors().get(1)).containsExactly(0.3f, 0.4f);
        assertThat(embeddings.modelTag()).isEqualTo("bge-m3");
        assertThat(embeddings.digest()).isEqualTo("sha256:e");
    }

    static String tags(Map<String, String> digests) {
        StringBuilder json = new StringBuilder("{\"models\":[");
        digests.forEach((tag, digest) -> json.append("{\"name\":\"").append(tag).append("\",\"model\":\"")
                .append(tag).append("\",\"digest\":\"").append(digest).append("\"},"));
        json.setLength(json.length() - 1);
        return json.append("]}").toString();
    }

    static final Map<String, String> SERVED = Map.of("phrase-a", "sha256:a", "nlu-model", "sha256:n",
            "judge-model", "sha256:j", "bge-m3", "sha256:e");

    @Test
    void verifyPassesWhenEveryBoundModelMatchesTheLockAndTheSmokeCallsAnswer() {
        OllamaLlmAdapter adapter = adapter(agent("phrase-a"), Optional.of(LOCK));
        server.expect(requestTo(URL + "/api/tags"))
                .andRespond(withSuccess(tags(SERVED), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "/api/chat")).andExpect(jsonPath("$.model").value("nlu-model"))
                .andRespond(withSuccess(chat("{\"ok\": true}", 20, "stop"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "/api/chat")).andExpect(jsonPath("$.model").value("judge-model"))
                .andRespond(withSuccess(chat("{\"ok\": false}", 20, "stop"), MediaType.APPLICATION_JSON));

        adapter.verify();
        server.verify();
    }

    /** Ollama lists a pulled untagged model as {@code <name>:latest}; the configuration binds plain {@code bge-m3}. */
    @Test
    void verifyMatchesAnUntaggedBindingToTheLatestTag() {
        OllamaLlmAdapter adapter = adapter(agent("phrase-a"), Optional.of(LOCK));
        Map<String, String> served = new java.util.HashMap<>(SERVED);
        served.remove("bge-m3");
        served.put("bge-m3:latest", "sha256:e");
        server.expect(requestTo(URL + "/api/tags")).andRespond(withSuccess(tags(served), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "/api/chat"))
                .andRespond(withSuccess(chat("{\"ok\": true}", 20, "stop"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "/api/chat"))
                .andRespond(withSuccess(chat("{\"ok\": true}", 20, "stop"), MediaType.APPLICATION_JSON));

        adapter.verify();
        server.verify();
    }

    @Test
    void verifyFailsOnADigestMismatch() {
        OllamaLlmAdapter adapter = adapter(agent("phrase-a"), Optional.of(LOCK));
        Map<String, String> served = new java.util.HashMap<>(SERVED);
        served.put("judge-model", "sha256:other");
        server.expect(requestTo(URL + "/api/tags")).andRespond(withSuccess(tags(served), MediaType.APPLICATION_JSON));

        assertThatThrownBy(adapter::verify).hasMessageContaining("judge-model")
                .hasMessageContaining("sha256:j").hasMessageContaining("sha256:other");
    }

    @Test
    void verifyFailsWhenABoundModelIsNotLocked() {
        OllamaLlmAdapter adapter = adapter(agent("phrase-unlocked"), Optional.of(LOCK));
        server.expect(requestTo(URL + "/api/tags")).andRespond(withSuccess(tags(SERVED), MediaType.APPLICATION_JSON));

        assertThatThrownBy(adapter::verify).hasMessageContaining("phrase-unlocked")
                .hasMessageContaining("not in models/lock.json");
    }

    @Test
    void verifyFailsWithoutALockFile() {
        assertThatThrownBy(adapter(agent("phrase-a"), Optional.empty())::verify)
                .hasMessageContaining("models/lock.json is missing");
    }

    @Test
    void verifyFailsWhenASmokeCallReturnsNoValidJson() {
        OllamaLlmAdapter adapter = adapter(agent("phrase-a"), Optional.of(LOCK));
        server.expect(requestTo(URL + "/api/tags")).andRespond(withSuccess(tags(SERVED), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "/api/chat"))
                .andRespond(withSuccess(chat("Sure! ok", 20, "stop"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(adapter::verify).hasMessageContaining("NLU").hasMessageContaining("nlu-model");
    }

    @Test
    void verifyFailsNamingTheUrlWhenOllamaDoesNotAnswer() {
        OllamaLlmAdapter adapter = adapter(agent("phrase-a"), Optional.of(LOCK));
        server.expect(requestTo(URL + "/api/tags")).andRespond(withServerError());

        assertThatThrownBy(adapter::verify).hasMessageContaining(URL);
    }
}
