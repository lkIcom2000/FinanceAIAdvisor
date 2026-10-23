package de.financetool.testing;

import de.financetool.llm.Embeddings;
import de.financetool.llm.LlmMessage;
import de.financetool.llm.LlmModel;
import de.financetool.llm.LlmPort;
import de.financetool.llm.LlmResult;
import de.financetool.llm.LlmRole;
import de.financetool.shared.config.AgentConfiguration;
import java.time.Duration;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The {@link LlmPort} of every integration test (no Ollama in tests): a programmable stub, imported by
 * {@code PostgresIntegrationTest} so that all integration tests share one context. Tests that change its behaviour
 * call {@link StubLlm#reset()} afterwards.
 *
 * <p>Answers depend on the role: the phrasing role answers {@link StubLlm#answer(String)}; the NLU role answers the
 * JSON programmed for the message ({@link StubLlm#nluAnswer(String, String)}), else the recorded E1 pilot extraction
 * of that question ({@link PilotNlu}), else {@link StubLlm#nluDefault(String)} (an empty object by default).
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubLlmConfiguration {

    @Bean
    @Primary
    StubLlm stubLlm() {
        return new StubLlm();
    }

    /** One recorded {@code generate} call. */
    public record Call(LlmRole role, List<LlmMessage> messages, AgentConfiguration.Decoding decoding,
            Map<String, Object> schema) {
    }

    public static class StubLlm implements LlmPort {

        public static final String DEFAULT_TEXT = "An ETF is a fund that is traded on an exchange like a share.";
        public static final String DEFAULT_NLU = "{}";
        public static final String MODEL_TAG = "stub-model:1b";
        public static final String DIGEST = "sha256:5706e1b5";

        private static final String SLOT_START = "<user_message>";
        private static final String SLOT_END = "</user_message>";

        private final List<Call> calls = new CopyOnWriteArrayList<>();
        private final Map<String, String> nluAnswers = new ConcurrentHashMap<>();
        private final Deque<String> queued = new ConcurrentLinkedDeque<>();
        private volatile String text;
        private volatile String nluDefault;
        private volatile String digest;
        private volatile RuntimeException failure;
        private volatile LlmRole failingRole;

        StubLlm() {
            reset();
        }

        /** Back to the default answers, and forgets the recorded calls. */
        public void reset() {
            text = DEFAULT_TEXT;
            nluDefault = DEFAULT_NLU;
            nluAnswers.clear();
            queued.clear();
            digest = DIGEST;
            failure = null;
            failingRole = null;
            calls.clear();
        }

        /** The phrasing role's answer. */
        public void answer(String value) {
            text = value;
        }

        /**
         * The phrasing role's next answers, one per call in this order (a draft, then its regeneration); after them
         * it answers {@link #answer(String)} again.
         */
        public void answers(String... values) {
            queued.clear();
            queued.addAll(List.of(values));
        }

        /** The NLU role's answer for exactly this user message. */
        public void nluAnswer(String message, String json) {
            nluAnswers.put(message, json);
        }

        /** The NLU role's answer for messages without a programmed or recorded one. */
        public void nluDefault(String value) {
            nluDefault = value;
        }

        /** Reports this model digest in its results and bindings. */
        public void digest(String value) {
            digest = value;
        }

        /** Every following call throws, like an unreachable Ollama. */
        public void fail(RuntimeException value) {
            fail(value, null);
        }

        /** Every following call of {@code role} (every call if {@code null}) throws. */
        public void fail(RuntimeException value, LlmRole role) {
            failure = value;
            failingRole = role;
        }

        public List<Call> calls() {
            return List.copyOf(calls);
        }

        public List<Call> calls(LlmRole role) {
            return calls.stream().filter(call -> call.role() == role).toList();
        }

        @Override
        public LlmResult generate(LlmRole role, List<LlmMessage> messages, AgentConfiguration.Decoding decoding,
                Map<String, Object> schema) {
            calls.add(new Call(role, List.copyOf(messages), decoding, schema));
            if (failure != null && (failingRole == null || failingRole == role)) {
                throw failure;
            }
            String next = role == LlmRole.PHRASING ? queued.poll() : null;
            String answer = role == LlmRole.NLU ? nluAnswer(messages) : next != null ? next : text;
            return new LlmResult(answer, MODEL_TAG, digest, decoding, 42, false, Duration.ofMillis(5));
        }

        private String nluAnswer(List<LlmMessage> messages) {
            String content = messages.getLast().content();
            String message = content.startsWith(SLOT_START) && content.endsWith(SLOT_END)
                    ? content.substring(SLOT_START.length(), content.length() - SLOT_END.length()) : content;
            String programmed = nluAnswers.get(message);
            if (programmed != null) {
                return programmed;
            }
            String recorded = PilotNlu.answerFor(message);
            return recorded != null ? recorded : nluDefault;
        }

        @Override
        public LlmModel model(LlmRole role) {
            return new LlmModel(MODEL_TAG, digest);
        }

        @Override
        public Embeddings embed(List<String> texts) {
            throw new UnsupportedOperationException("no embeddings in the stub");
        }
    }
}
