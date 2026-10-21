package de.financetool.llm;

import de.financetool.shared.config.AgentConfiguration;
import java.time.Duration;

/**
 * One model answer with everything the Audit Record needs (AD-5, AD-13).
 *
 * @param digest the model's pinned digest from {@code models/lock.json}
 * @param truncated the answer stopped at {@code numPredict} or the prompt filled {@code numCtx}
 */
public record LlmResult(String text, String modelTag, String digest, AgentConfiguration.Decoding decoding,
        int promptTokens, boolean truncated, Duration latency) {
}
