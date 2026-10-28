package de.financetool.advisory.domain;

import de.financetool.shared.domain.SourcedNumber;
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
 * @param messageWithheld whether level {@code NONE} withheld the message entirely (no depersonalised question)
 * @param history the previous released turn, already filtered; empty without one (FR-63)
 * @param profile the customer profile context (empty at level {@code NONE})
 * @param toolResults the Deterministic Core results as text, passed in full at every level (empty without tools)
 * @param passages the Knowledge Base passages, in retrieval order
 */
public record PromptPayload(
        String message,
        boolean messageWithheld,
        List<HistoryTurn> history,
        Map<String, Object> profile,
        String toolResults,
        List<Passage> passages) {

    /**
     * One released turn as the model may see it: the user's message and the agent's answer to it.
     *
     * @param coreFigures the agent message's Core figures as stored (AD-2): the only numbers of the turn the Number
     *     Check sources, and only while the budget keeps the turn
     */
    public record HistoryTurn(String userText, String agentText, List<SourcedNumber> coreFigures) {

        public HistoryTurn {
            Objects.requireNonNull(userText, "userText");
            Objects.requireNonNull(agentText, "agentText");
            coreFigures = coreFigures == null ? List.of() : List.copyOf(coreFigures);
        }

        /** A turn without Core figures. */
        public HistoryTurn(String userText, String agentText) {
            this(userText, agentText, List.of());
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

    /** A payload whose message was not withheld. */
    public PromptPayload(String message, List<HistoryTurn> history, Map<String, Object> profile, String toolResults,
            List<Passage> passages) {
        this(message, false, history, profile, toolResults, passages);
    }
}
