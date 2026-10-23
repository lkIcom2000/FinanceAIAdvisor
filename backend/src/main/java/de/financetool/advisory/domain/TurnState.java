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
 * @param nlu the grounded NLU extraction, set by the NLU stage
 * @param classification set by the classify stage
 * @param prompt the phrasing prompt, set by the exposure filter stage
 * @param draft set by the LLM draft stage
 * @param outcome set by block routing
 * @param finalText the released text, set by block routing
 * @param suggestions the released suggestion chips (0–3), set by block routing
 * @param noValidSource whether block routing released the no-valid-source template
 */
public record TurnState(
        String userText,
        String dialogState,
        Nlu nlu,
        Classification classification,
        Prompt prompt,
        Draft draft,
        ResponseOutcome outcome,
        String finalText,
        List<String> suggestions,
        boolean noValidSource,
        List<TurnStage> stagesRun,
        List<TurnStage> passThroughStages) {

    public static final String NO_DIALOG_STATE = "NONE";

    /**
     * The NLU stage's result.
     *
     * @param extraction the grounded extraction
     * @param groundingNotes what the grounding rules changed
     * @param modelTag the NLU model's tag
     * @param modelDigest the NLU model's pinned digest
     * @param promptHashes file name of each NLU prompt file to the sha256 of its LF bytes
     * @param cacheHit whether the extraction came from the NLU cache without a model call
     * @param parseOk whether the model's answer was a JSON object
     */
    public record Nlu(NluExtraction extraction, List<String> groundingNotes, String modelTag, String modelDigest,
            SortedMap<String, String> promptHashes, boolean cacheHit, boolean parseOk) {

        public Nlu {
            Objects.requireNonNull(extraction, "extraction");
            groundingNotes = List.copyOf(groundingNotes);
            promptHashes = Collections.unmodifiableSortedMap(new TreeMap<>(promptHashes));
        }
    }

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
        suggestions = List.copyOf(suggestions);
        stagesRun = List.copyOf(stagesRun);
        passThroughStages = List.copyOf(passThroughStages);
    }

    public static TurnState start(String userText) {
        return new TurnState(userText, null, null, null, null, null, null, null, List.of(), false, List.of(),
                List.of());
    }

    /** The class, once the classify stage ran. */
    public MessageClass messageClass() {
        return classification == null ? null : classification.messageClass();
    }

    /** This state after {@code stage} ran and did nothing. */
    public TurnState passedThrough(TurnStage stage) {
        return new TurnState(userText, dialogState, nlu, classification, prompt, draft, outcome, finalText,
                suggestions, noValidSource, plus(stagesRun, stage), plus(passThroughStages, stage));
    }

    /** This state after {@code stage} ran with the given result. */
    public TurnState ran(TurnStage stage) {
        return new TurnState(userText, dialogState, nlu, classification, prompt, draft, outcome, finalText,
                suggestions, noValidSource, plus(stagesRun, stage), passThroughStages);
    }

    public TurnState withDialogState(String state) {
        return new TurnState(userText, state, nlu, classification, prompt, draft, outcome, finalText, suggestions,
                noValidSource, stagesRun, passThroughStages);
    }

    public TurnState withNlu(Nlu value) {
        return new TurnState(userText, dialogState, value, classification, prompt, draft, outcome, finalText,
                suggestions, noValidSource, stagesRun, passThroughStages);
    }

    public TurnState withClassification(Classification value) {
        return new TurnState(userText, dialogState, nlu, value, prompt, draft, outcome, finalText, suggestions,
                noValidSource, stagesRun, passThroughStages);
    }

    public TurnState withPrompt(Prompt value) {
        return new TurnState(userText, dialogState, nlu, classification, value, draft, outcome, finalText,
                suggestions, noValidSource, stagesRun, passThroughStages);
    }

    public TurnState withDraft(Draft value) {
        return new TurnState(userText, dialogState, nlu, classification, prompt, value, outcome, finalText,
                suggestions, noValidSource, stagesRun, passThroughStages);
    }

    public TurnState withOutcome(ResponseOutcome value, String text) {
        return withOutcome(value, text, List.of(), false);
    }

    /**
     * @param chips the suggestion chips released with the text
     * @param fromNoValidSource whether the text is the no-valid-source template
     */
    public TurnState withOutcome(ResponseOutcome value, String text, List<String> chips, boolean fromNoValidSource) {
        return new TurnState(userText, dialogState, nlu, classification, prompt, draft, value, text, chips,
                fromNoValidSource, stagesRun, passThroughStages);
    }

    private static List<TurnStage> plus(List<TurnStage> stages, TurnStage stage) {
        List<TurnStage> result = new ArrayList<>(stages);
        result.add(stage);
        return result;
    }
}
