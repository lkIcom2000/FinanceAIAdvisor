package de.financetool.advisory.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the phrasing role may see of a turn after the exposure filter (FR-43): every text in it is already filtered
 * for the configured level.
 *
 * @param message the user's message as shown to the model
 * @param history the earlier released turns, oldest first
 * @param profile the customer profile context (empty at level {@code NONE})
 * @param toolResults the Deterministic Core results as text, passed in full at every level (empty without tools)
 * @param passages the Knowledge Base passages, in retrieval order
 */
public record PromptPayload(
        String message,
        List<HistoryTurn> history,
        Map<String, Object> profile,
        String toolResults,
        List<Passage> passages) {

    /** One released turn: the user's message and the agent's answer to it. */
    public record HistoryTurn(String userText, String agentText) {

        public HistoryTurn {
            Objects.requireNonNull(userText, "userText");
            Objects.requireNonNull(agentText, "agentText");
        }
    }

    /** One Knowledge Base passage with its citation (title, source, version, validity, page). */
    public record Passage(String citation, String text) {

        public Passage {
            Objects.requireNonNull(citation, "citation");
            Objects.requireNonNull(text, "text");
        }
    }

    public PromptPayload {
        Objects.requireNonNull(message, "message");
        history = List.copyOf(history);
        profile = Collections.unmodifiableMap(new LinkedHashMap<>(profile));
        toolResults = toolResults == null ? "" : toolResults;
        passages = List.copyOf(passages);
    }
}
