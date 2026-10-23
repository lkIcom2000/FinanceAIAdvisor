package de.financetool.llm.adapter;

import de.financetool.llm.Embeddings;
import de.financetool.llm.LlmMessage;
import de.financetool.llm.LlmModel;
import de.financetool.llm.LlmPort;
import de.financetool.llm.LlmResult;
import de.financetool.llm.LlmRole;
import de.financetool.shared.config.AgentConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link LlmPort} on Ollama (AD-5, AD-18). Besides {@code RequestContextService}, the only reader of the Agent
 * Configuration: the model tag per role is infrastructure binding, fixed for the lifetime of the process.
 */
class OllamaLlmAdapter implements LlmPort {

    /** Smoke call for {@code nlu} and {@code judge} (AD-18). */
    static final Map<String, Object> SMOKE_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("ok", Map.of("type", "boolean")),
            "required", List.of("ok"));
    static final String SMOKE_PROMPT = "Answer with the JSON object {\"ok\": true}.";

    private final OllamaClient ollama;
    private final AgentConfiguration agent;
    private final Optional<ModelLock> lock;
    private final String url;

    OllamaLlmAdapter(OllamaClient ollama, AgentConfiguration agent, Optional<ModelLock> lock, String url) {
        this.ollama = ollama;
        this.agent = agent;
        this.lock = lock;
        this.url = url;
    }

    String tag(LlmRole role) {
        return switch (role) {
            case PHRASING -> agent.llm().phrasing();
            case NLU -> agent.llm().nlu();
            case JUDGE -> agent.llm().judge();
        };
    }

    private String digest(String tag) {
        return lock.flatMap(l -> l.find(tag)).map(ModelLock.Entry::digest).orElse("");
    }

    @Override
    public LlmResult generate(LlmRole role, List<LlmMessage> messages, AgentConfiguration.Decoding decoding,
            Map<String, Object> schema) {
        String tag = tag(role);
        List<Map<String, String>> chat = new ArrayList<>();
        for (LlmMessage message : messages) {
            chat.add(Map.of("role", message.author().name().toLowerCase(Locale.ROOT), "content", message.content()));
        }
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", decoding.temperature());
        options.put("top_p", decoding.topP());
        options.put("seed", decoding.seed());
        options.put("num_ctx", decoding.numCtx());
        options.put("num_predict", decoding.numPredict());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", tag);
        body.put("messages", chat);
        body.put("stream", false);
        body.put("options", options);
        if (schema != null) {
            body.put("format", schema);
        }

        long start = System.nanoTime();
        OllamaClient.ChatResponse response = ollama.chat(body);
        Duration latency = Duration.ofNanos(System.nanoTime() - start);

        int promptTokens = response.promptEvalCount() == null ? 0 : response.promptEvalCount();
        boolean truncated = "length".equals(response.doneReason()) || promptTokens >= decoding.numCtx();
        String text = response.message() == null || response.message().content() == null
                ? "" : response.message().content();
        return new LlmResult(text, tag, digest(tag), decoding, promptTokens, truncated, latency);
    }

    @Override
    public LlmModel model(LlmRole role) {
        String tag = tag(role);
        return new LlmModel(tag, digest(tag));
    }

    @Override
    public Embeddings embed(List<String> texts) {
        String tag = agent.llm().embedding();
        return new Embeddings(List.copyOf(ollama.embed(tag, texts).embeddings()), tag, digest(tag));
    }

    /**
     * Fails fast unless every bound model (the three roles plus the embedding model) is pinned in the lock with the
     * digest Ollama reports, and {@code nlu} and {@code judge} answer the smoke schema (AD-18).
     */
    void verify() {
        ModelLock pinned = lock.orElseThrow(() -> new IllegalStateException(
                "models/" + ModelLock.FILE + " is missing; run models/update-lock.sh"));
        Map<String, String> served = new LinkedHashMap<>();
        try {
            for (OllamaClient.TagsResponse.Tag t : ollama.tags().models()) {
                served.put(t.name(), t.digest());
                served.putIfAbsent(t.model(), t.digest());
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException("Ollama at " + url + " does not answer: " + e.getMessage(), e);
        }
        Set<String> bound = new LinkedHashSet<>(List.of(agent.llm().phrasing(), agent.llm().nlu(),
                agent.llm().judge(), agent.llm().embedding()));
        for (String tag : bound) {
            ModelLock.Entry entry = pinned.find(tag).orElseThrow(() -> new IllegalStateException(
                    "model " + tag + " is bound in agent configuration " + agent.id() + " but not in models/"
                            + ModelLock.FILE));
            String actual = served.getOrDefault(tag, served.get(tag + ":latest"));
            if (!entry.digest().equals(actual)) {
                throw new IllegalStateException("model " + tag + ": lock digest " + entry.digest()
                        + " but Ollama serves " + (actual == null ? "nothing" : actual));
            }
        }
        for (LlmRole role : List.of(LlmRole.NLU, LlmRole.JUDGE)) {
            smoke(role);
        }
    }

    private void smoke(LlmRole role) {
        AgentConfiguration.Decoding configured = agent.decoding();
        AgentConfiguration.Decoding decoding = new AgentConfiguration.Decoding(configured.temperature(),
                configured.topP(), configured.seed(), configured.numCtx(), 32);
        LlmResult result = generate(role, List.of(LlmMessage.user(SMOKE_PROMPT)), decoding, SMOKE_SCHEMA);
        boolean valid;
        try {
            JsonNode node = JsonMapper.builder().build().readTree(result.text());
            valid = node.isObject() && node.has("ok") && node.get("ok").isBoolean();
        } catch (JacksonException e) {
            valid = false;
        }
        if (!valid) {
            throw new IllegalStateException("smoke call for role " + role + " (model " + result.modelTag()
                    + ") returned no valid JSON for the smoke schema: " + result.text());
        }
    }
}
