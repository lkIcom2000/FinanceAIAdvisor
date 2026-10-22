package de.financetool.testing;

import de.financetool.llm.Embeddings;
import de.financetool.llm.LlmMessage;
import de.financetool.llm.LlmPort;
import de.financetool.llm.LlmResult;
import de.financetool.llm.LlmRole;
import de.financetool.shared.config.AgentConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The {@link LlmPort} of every integration test (no Ollama in tests): a programmable stub, imported by
 * {@code PostgresIntegrationTest} so that all integration tests share one context. Tests that change its behaviour
 * call {@link StubLlm#reset()} afterwards.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubLlmConfiguration {

    @Bean
    @Primary
    StubLlm stubLlm() {
        return new StubLlm();
    }

    /** One recorded {@code generate} call. */
    public record Call(LlmRole role, List<LlmMessage> messages, AgentConfiguration.Decoding decoding) {
    }

    public static class StubLlm implements LlmPort {

        public static final String DEFAULT_TEXT = "An ETF is a fund that is traded on an exchange like a share.";
        public static final String MODEL_TAG = "stub-model:1b";
        public static final String DIGEST = "sha256:5706e1b5";

        private final List<Call> calls = new CopyOnWriteArrayList<>();
        private volatile String text;
        private volatile String digest;
        private volatile RuntimeException failure;

        StubLlm() {
            reset();
        }

        /** Back to the default answer, and forgets the recorded calls. */
        public void reset() {
            text = DEFAULT_TEXT;
            digest = DIGEST;
            failure = null;
            calls.clear();
        }

        public void answer(String value) {
            text = value;
        }

        /** Reports this model digest in its results. */
        public void digest(String value) {
            digest = value;
        }

        /** Every following call throws, like an unreachable Ollama. */
        public void fail(RuntimeException value) {
            failure = value;
        }

        public List<Call> calls() {
            return List.copyOf(calls);
        }

        @Override
        public LlmResult generate(LlmRole role, List<LlmMessage> messages, AgentConfiguration.Decoding decoding,
                Map<String, Object> schema) {
            calls.add(new Call(role, List.copyOf(messages), decoding));
            if (failure != null) {
                throw failure;
            }
            return new LlmResult(text, MODEL_TAG, digest, decoding, 42, false, Duration.ofMillis(5));
        }

        @Override
        public Embeddings embed(List<String> texts) {
            throw new UnsupportedOperationException("no embeddings in the stub");
        }
    }
}
