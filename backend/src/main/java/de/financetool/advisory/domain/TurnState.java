package de.financetool.advisory.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * What a turn knows after each stage: immutable, every stage returns a new state. {@code stagesRun} records the order
 * the stages actually ran in, {@code passThroughStages} those that did nothing but pass the state on.
 *
 * @param dialogState the persona's dialog state as consulted first ({@code NONE} without one)
 * @param messageClass set by the classify stage
 * @param prompt the phrasing prompt, set by the exposure filter stage
 * @param draft set by the LLM draft stage
 * @param outcome set by block routing
 * @param finalText the released text, set by block routing
 */
public record TurnState(
        String userText,
        String dialogState,
        MessageClass messageClass,
        Prompt prompt,
        Draft draft,
        ResponseOutcome outcome,
        String finalText,
        List<TurnStage> stagesRun,
        List<TurnStage> passThroughStages) {

    public static final String NO_DIALOG_STATE = "NONE";

    /**
     * The phrasing prompt after exposure filter and budget.
     *
     * @param payload what the exposure filter let through
     * @param phrasing the system and user parts within the budget
     * @param promptHashes file name of each prompt file in the system part to the sha256 of its LF bytes
     */
    public record Prompt(PromptPayload payload, PhrasingPrompt phrasing, SortedMap<String, String> promptHashes) {

        public Prompt {
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(phrasing, "phrasing");
            promptHashes = Collections.unmodifiableSortedMap(new TreeMap<>(promptHashes));
        }
    }

    /** The phrasing role's first draft with what the Audit Record needs about the call. */
    public record Draft(String text, String modelTag, String modelDigest, boolean truncated) {

        public Draft {
            Objects.requireNonNull(text, "text");
        }
    }

    public TurnState {
        Objects.requireNonNull(userText, "userText");
        stagesRun = List.copyOf(stagesRun);
        passThroughStages = List.copyOf(passThroughStages);
    }

    public static TurnState start(String userText) {
        return new TurnState(userText, null, null, null, null, null, null, List.of(), List.of());
    }

    /** This state after {@code stage} ran and did nothing. */
    public TurnState passedThrough(TurnStage stage) {
        return new TurnState(userText, dialogState, messageClass, prompt, draft, outcome, finalText,
                plus(stagesRun, stage), plus(passThroughStages, stage));
    }

    /** This state after {@code stage} ran with the given result. */
    public TurnState ran(TurnStage stage) {
        return new TurnState(userText, dialogState, messageClass, prompt, draft, outcome, finalText,
                plus(stagesRun, stage), passThroughStages);
    }

    public TurnState withDialogState(String state) {
        return new TurnState(userText, state, messageClass, prompt, draft, outcome, finalText, stagesRun,
                passThroughStages);
    }

    public TurnState withClass(MessageClass value) {
        return new TurnState(userText, dialogState, value, prompt, draft, outcome, finalText, stagesRun,
                passThroughStages);
    }

    public TurnState withPrompt(Prompt value) {
        return new TurnState(userText, dialogState, messageClass, value, draft, outcome, finalText, stagesRun,
                passThroughStages);
    }

    public TurnState withDraft(Draft value) {
        return new TurnState(userText, dialogState, messageClass, prompt, value, outcome, finalText, stagesRun,
                passThroughStages);
    }

    public TurnState withOutcome(ResponseOutcome value, String text) {
        return new TurnState(userText, dialogState, messageClass, prompt, draft, value, text, stagesRun,
                passThroughStages);
    }

    private static List<TurnStage> plus(List<TurnStage> stages, TurnStage stage) {
        List<TurnStage> result = new ArrayList<>(stages);
        result.add(stage);
        return result;
    }
}
