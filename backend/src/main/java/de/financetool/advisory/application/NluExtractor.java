package de.financetool.advisory.application;

import de.financetool.advisory.adapter.NluCacheRepository;
import de.financetool.advisory.adapter.PromptFiles;
import de.financetool.advisory.domain.NluExtraction;
import de.financetool.advisory.domain.PhrasingPrompt;
import de.financetool.advisory.domain.TurnState;
import de.financetool.llm.LlmMessage;
import de.financetool.llm.LlmModel;
import de.financetool.llm.LlmPort;
import de.financetool.llm.LlmResult;
import de.financetool.llm.LlmRole;
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
 * and few-shot examples from {@code config/prompts/}; every user message in a {@code <user_message>} slot. The answer
 * is data only: normalised, cached, then grounded. A cache hit makes no model call.
 */
@Component
class NluExtractor {

    /** {@code numPredict} of the NLU call, as in the experiments. */
    static final int NUM_PREDICT = 900;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, Object> SCHEMA = NluExtraction.schema();

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

    TurnState.Nlu extract(AgentConfiguration agent, String message) {
        PromptFiles.Prompt system = prompts.nluSystem();
        PromptFiles.Prompt fewShot = prompts.nluFewShot();
        Map<String, String> hashes = new TreeMap<>(Map.of(system.fileName(), system.hash(), fewShot.fileName(),
                fewShot.hash()));
        LlmModel model = llm.model(LlmRole.NLU);
        String key = sha256(model.tag() + "|" + model.digest() + "|" + sha256(system.hash() + "|" + fewShot.hash())
                + "|" + message);

        Optional<NluCacheRepository.Entry> cached = cache.find(key);
        NluExtraction raw;
        boolean parseOk;
        String tag = model.tag();
        String digest = model.digest();
        if (cached.isPresent()) {
            raw = NluExtraction.normalize(parse(cached.get().extraction()).orElse(Map.of()));
            parseOk = cached.get().parseOk();
        } else {
            LlmResult result = llm.generate(LlmRole.NLU, messages(system.text(), fewShot.text(), message),
                    decoding(agent.decoding()), SCHEMA);
            Optional<Map<String, Object>> parsed = parse(result.text());
            raw = NluExtraction.normalize(parsed.orElse(Map.of()));
            parseOk = parsed.isPresent();
            tag = result.modelTag();
            digest = result.digest();
            cache.put(key, new NluCacheRepository.Entry(JSON.writeValueAsString(raw.toMap()), parseOk));
        }
        NluExtraction.Grounded grounded = raw.ground(message);
        return new TurnState.Nlu(grounded.extraction(), grounded.notes(), tag, digest, new TreeMap<>(hashes),
                cached.isPresent(), parseOk);
    }

    /** System prompt, the few-shot pairs as user and assistant turns ({@code json.dumps} of each answer), then the message. */
    static List<LlmMessage> messages(String system, String fewShot, String message) {
        List<LlmMessage> out = new ArrayList<>();
        out.add(LlmMessage.system(system));
        List<?> pairs;
        try {
            pairs = JSON.readValue(fewShot, List.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("config/prompts/" + PromptFiles.NLU_FEW_SHOT + " is no JSON list", e);
        }
        for (Object pair : pairs) {
            if (!(pair instanceof List<?> p) || p.size() != 2 || !(p.get(0) instanceof String question)) {
                throw new IllegalStateException("config/prompts/" + PromptFiles.NLU_FEW_SHOT
                        + ": every example is a [message, extraction] pair");
            }
            out.add(LlmMessage.user(slot(question)));
            out.add(LlmMessage.assistant(PythonJson.dumps(p.get(1))));
        }
        out.add(LlmMessage.user(slot(message)));
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
