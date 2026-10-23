package de.financetool.llm;

import de.financetool.shared.config.AgentConfiguration;
import java.util.List;
import java.util.Map;

/**
 * Module API: the only way to call a model (AD-5). The model behind each role comes from the active Agent
 * Configuration; output is text only and never interpreted as an instruction.
 */
public interface LlmPort {

    /**
     * @param schema a JSON schema the output must follow (passed as Ollama {@code format}), or {@code null}
     */
    LlmResult generate(LlmRole role, List<LlmMessage> messages, AgentConfiguration.Decoding decoding,
            Map<String, Object> schema);

    /** The model bound to {@code role}, without calling it (for cache keys that include the model digest). */
    LlmModel model(LlmRole role);

    /** Embeddings with the configured embedding model; an over-long text fails instead of being cut. */
    Embeddings embed(List<String> texts);
}
