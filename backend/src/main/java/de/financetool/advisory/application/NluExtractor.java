package de.financetool.advisory.application;

import de.financetool.advisory.adapter.NluCacheRepository;
import de.financetool.advisory.adapter.PromptFiles;
import de.financetool.advisory.domain.NluExtraction;
import de.financetool.advisory.domain.PhrasingPrompt;
import de.financetool.advisory.domain.PromptPayload;
import de.financetool.advisory.domain.TurnState;
import de.financetool.llm.LlmMessage;
import de.financetool.llm.LlmModel;
import de.financetool.llm.LlmPort;
import de.financetool.llm.LlmResult;
import de.financetool.llm.LlmRole;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.config.AgentConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The NLU stage's work (AD-4, AD-5), as {@code Nlu.extract} of {@code experiments/finlab/nlu.py}: the {@code nlu}
 * role at temperature 0 with the configured seed and the extraction schema as Ollama {@code format}; system prompt
 * and few-shot examples from {@code config/prompts/}; every user message in a {@code <user_message>} slot, the
 * current one after the previous released turn as an {@code EARLIER CONVERSATION} data block (FR-63; deviation from
 * {@code nlu.py}, which E4 ran single-turn). The answer
 * is data only: normalised, cached per persona (only a parseable answer), then grounded. A cache hit makes no model
 * call.
 */
@Component
class NluExtractor {

    /** {@code numPredict} of the NLU call, as in the experiments. */
    static final int NUM_PREDICT = 900;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, Object> SCHEMA = NluExtraction.schema();
    private static final String SCHEMA_HASH = sha256(PythonJson.dumps(SCHEMA));

    private final PromptFiles prompts;
    private final LlmPort llm;
    private final NluCacheRepository cache;

    NluExtractor(PromptFiles prompts, LlmPort llm, NluCacheRepository cache) {
        this.prompts = prompts;
        this.llm = llm;
        this.cache = cache;
    }

    /** The decoding of the NLU call: temperature 0, the configured seed, {@code numPredict} 900. */
    static AgentConfiguration.Decoding decoding(AgentConfiguration.Decoding configured) {
        return new AgentConfiguration.Decoding(0.0, configured.topP(), configured.seed(), configured.numCtx(),
                NUM_PREDICT);
    }

    /** {@link #extract(AgentConfiguration, PersonaScope, String, PromptPayload.HistoryTurn)} without a previous turn. */
    TurnState.Nlu extract(AgentConfiguration agent, PersonaScope scope, String message) {
        return extract(agent, scope, message, null);
    }

    /**
     * @param previous the previous released turn as the exposure filter let it through; {@code null} without one.
     *     It is dropped if the prompt with it would exceed {@code numCtx − 900 − 200} estimated tokens.
     */
    TurnState.Nlu extract(AgentConfiguration agent, PersonaScope scope, String message,
            PromptPayload.HistoryTurn previous) {
        PromptFiles.Prompt system = prompts.nluSystem();
        PromptFiles.Prompt fewShot = prompts.nluFewShot();
        Map<String, String> hashes = new TreeMap<>(Map.of(system.fileName(), system.hash(), fewShot.fileName(),
                fewShot.hash()));
        LlmModel model = llm.model(LlmRole.NLU);
        AgentConfiguration.Decoding decoding = decoding(agent.decoding());
        List<PromptFiles.FewShot> fewShots = prompts.nluFewShots();
        String earlier = earlier(system.text(), fewShots, message, previous, decoding.numCtx());
        String key = key(scope, model, decoding, sha256(system.hash() + "|" + fewShot.hash()), message, earlier);

        Optional<NluCacheRepository.Entry> cached = cache.find(scope, key);
        NluExtraction raw;
        boolean parseOk;
        String tag = model.tag();
        String digest = model.digest();
        if (cached.isPresent()) {
            raw = NluExtraction.normalize(parse(cached.get().extraction()).orElse(Map.of()));
            parseOk = cached.get().parseOk();
        } else {
            LlmResult result = llm.generate(LlmRole.NLU, messages(system.text(), fewShots, message, earlier),
                    decoding, SCHEMA);
            Optional<Map<String, Object>> parsed = parse(result.text());
            raw = NluExtraction.normalize(parsed.orElse(Map.of()));
            parseOk = parsed.isPresent();
            tag = result.modelTag();
            digest = result.digest();
            // An unparseable answer is not cached: the next turn asks the model again.
            if (parseOk) {
                cache.put(scope, key, new NluCacheRepository.Entry(JSON.writeValueAsString(raw.toMap()), true));
            }
        }
        NluExtraction.Grounded grounded = raw.ground(message);
        return new TurnState.Nlu(grounded.extraction(), grounded.notes(), tag, digest, new TreeMap<>(hashes),
                cached.isPresent(), parseOk);
    }

    /**
     * The {@code EARLIER CONVERSATION} block of {@code previous}, or {@code ""} without one or when the prompt with
     * it would exceed {@code numCtx − 900 − 200} estimated tokens (the previous turn is dropped first).
     */
    static String earlier(String system, List<PromptFiles.FewShot> fewShots, String message,
            PromptPayload.HistoryTurn previous, int numCtx) {
        if (previous == null) {
            return "";
        }
        String block = PhrasingPrompt.historyBlock(List.of(previous));
        double tokens = messages(system, fewShots, message, block).stream()
                .mapToDouble(m -> PhrasingPrompt.estimatedTokens(m.content())).sum();
        return tokens <= numCtx - NUM_PREDICT - PhrasingPrompt.RESERVE_TOKENS ? block : "";
    }

    /**
     * The cache key: sha256 over persona, model tag and digest, seed, {@code numPredict}, the schema's hash, the NLU
     * prompt hash and the message.
     */
    static String key(PersonaScope scope, LlmModel model, AgentConfiguration.Decoding decoding, String promptHash,
            String message) {
        return sha256(String.join("|", scope.id(), model.tag(), model.digest(), String.valueOf(decoding.seed()),
                String.valueOf(decoding.numPredict()), SCHEMA_HASH, promptHash, message));
    }

    /**
     * The cache key with the previous turn's block as the NLU saw it; without one ({@code ""}) it is
     * {@link #key(PersonaScope, LlmModel, AgentConfiguration.Decoding, String, String)}, so earlier entries stay valid.
     */
    static String key(PersonaScope scope, LlmModel model, AgentConfiguration.Decoding decoding, String promptHash,
            String message, String earlier) {
        String key = key(scope, model, decoding, promptHash, message);
        return earlier.isEmpty() ? key : sha256(key + "|earlier|" + earlier);
    }

    /** System prompt, the few-shot pairs as user and assistant turns ({@code json.dumps} of each answer), then the message. */
    static List<LlmMessage> messages(String system, List<PromptFiles.FewShot> fewShots, String message) {
        return messages(system, fewShots, message, "");
    }

    /**
     * Like {@link #messages(String, List, String)}; a non-blank {@code earlier} block goes into the final user
     * message before the slot.
     */
    static List<LlmMessage> messages(String system, List<PromptFiles.FewShot> fewShots, String message,
            String earlier) {
        List<LlmMessage> out = new ArrayList<>();
        out.add(LlmMessage.system(system));
        for (PromptFiles.FewShot example : fewShots) {
            out.add(LlmMessage.user(slot(example.message())));
            out.add(LlmMessage.assistant(PythonJson.dumps(example.extraction())));
        }
        out.add(LlmMessage.user(earlier.isEmpty() ? slot(message) : earlier + "\n\n" + slot(message)));
        return out;
    }

    /** The message in its data slot; slot markers inside it are removed so it cannot leave the slot. */
    static String slot(String message) {
        return PhrasingPrompt.SLOT_START + PhrasingPrompt.data(message) + PhrasingPrompt.SLOT_END;
    }

    /** The model's answer as a JSON object, empty if it is none ({@code parse_ok: false}). */
    @SuppressWarnings("unchecked")
    static Optional<Map<String, Object>> parse(String text) {
        try {
            Object value = JSON.readValue(text, Object.class);
            return value instanceof Map<?, ?> map ? Optional.of((Map<String, Object>) map) : Optional.empty();
        } catch (JacksonException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
