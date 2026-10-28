package de.financetool.advisory.domain;

import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.NeedsFigures;
import de.financetool.core.domain.RecommendationFigures;
import de.financetool.shared.domain.SimulationSpec;
import de.financetool.shared.domain.SourcedNumber;
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
 * @param tools the tool plan classify evaluated and what stage 3 did with it; {@link Tools#NONE} without one
 * @param passages the Knowledge Base passages the retrieval stage found, before any budget drop (empty until Epic 4)
 * @param prompt the phrasing prompt, set by the exposure filter stage
 * @param draft set by the LLM draft stage
 * @param checks the Check Chain's results and what the Rule Engine and block routing did with them, set by the
 *     shadow checks stage and completed by the Rule Engine, Judge and block routing stages
 * @param outcome set by block routing
 * @param finalText the released text, set by block routing
 * @param suggestions the released suggestion chips (0–3), set by block routing
 * @param noValidSource whether block routing released the no-valid-source template
 * @param previous the previous released turn as the exposure filter lets it through, set by the NLU stage and given to
 *     the NLU and the phrasing prompt (FR-63); {@code null} without one
 */
public record TurnState(
        String userText,
        String dialogState,
        Nlu nlu,
        Classification classification,
        Tools tools,
        List<PromptPayload.Passage> passages,
        Prompt prompt,
        Draft draft,
        Checks checks,
        ResponseOutcome outcome,
        String finalText,
        List<String> suggestions,
        boolean noValidSource,
        List<TurnStage> stagesRun,
        List<TurnStage> passThroughStages,
        PromptPayload.HistoryTurn previous) {

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
     * The tool plan and stage 3's result.
     *
     * @param planned the calculation classify found computable; {@code null} without one
     * @param accountPortfolio the persona's investments today when the plan takes the portfolio from account data
     * @param spec the spec the turn proposes or calculates; {@code null} without a calculation
     * @param proposal whether the turn releases a parameter proposal instead of calculating (AD-24)
     * @param results the Core results of the tools run, in run order (none for a proposal)
     * @param ruleParameterError whether a tool needed a Rule Parameter without a version valid at the as-of date
     * @param ruleForLaterYear whether the invalid Rule Parameter is one for a later year the message asks about
     * @param recommendation the Core's Category Recommendation of a {@code RECOMMEND_CATEGORY} turn; {@code null}
     *     otherwise
     * @param statement the Suitability Statement built from {@code recommendation} and the effective profile;
     *     {@code null} without a recommendation
     * @param needs the Core's demands-and-needs result of a {@code RECOMMEND_CATEGORY} turn on a protection topic;
     *     {@code null} otherwise
     * @param needsStatement the demands-and-needs statement built from {@code needs}; {@code null} without one
     */
    public record Tools(ToolPlan.Planned planned, SourcedNumber accountPortfolio, SimulationSpec spec,
            boolean proposal, List<CoreResult> results, boolean ruleParameterError, boolean ruleForLaterYear,
            RecommendationFigures recommendation, ChatMessage.Recommendation statement, NeedsFigures needs,
            ChatMessage.NeedsStatement needsStatement, ChatMessage.RangeChart range) {

        public static final Tools NONE = new Tools(null, null, null, false, List.of(), false);

        public Tools(ToolPlan.Planned planned, SourcedNumber accountPortfolio, SimulationSpec spec,
                boolean proposal, List<CoreResult> results, boolean ruleParameterError) {
            this(planned, accountPortfolio, spec, proposal, results, ruleParameterError, false, null, null, null,
                    null, null);
        }

        public Tools(ToolPlan.Planned planned, SourcedNumber accountPortfolio, SimulationSpec spec,
                boolean proposal, List<CoreResult> results, boolean ruleParameterError, boolean ruleForLaterYear,
                RecommendationFigures recommendation, ChatMessage.Recommendation statement, NeedsFigures needs,
                ChatMessage.NeedsStatement needsStatement) {
            this(planned, accountPortfolio, spec, proposal, results, ruleParameterError, ruleForLaterYear,
                    recommendation, statement, needs, needsStatement, null);
        }

        /** A confirmed spec ran: its Core results in run order and, for a what-if, its range chart. */
        public static Tools calculated(SimulationSpec spec, List<CoreResult> results, ChatMessage.RangeChart range) {
            Objects.requireNonNull(spec, "spec");
            return new Tools(null, null, spec, false, results, false, false, null, null, null, null, range);
        }

        /** No calculation: the subsidy rules for the later year the message asks about are not valid. */
        public static Tools laterYearRuleMissing() {
            return new Tools(null, null, null, false, List.of(), true, true, null, null, null, null);
        }

        /** The Category Recommendation ran: its selection and cost results, in run order, and its statement. */
        public static Tools recommended(RecommendationFigures recommendation, ChatMessage.Recommendation statement) {
            Objects.requireNonNull(recommendation, "recommendation");
            Objects.requireNonNull(statement, "statement");
            List<CoreResult> results = new ArrayList<>();
            results.add(recommendation.selection());
            if (recommendation.costs() != null) {
                results.add(recommendation.costs());
            }
            return new Tools(null, null, null, false, results, false, false, recommendation, statement, null, null);
        }

        /** The demands-and-needs statement ran (protection topics): its Core result and the statement. */
        public static Tools needsStated(NeedsFigures needs, ChatMessage.NeedsStatement statement) {
            Objects.requireNonNull(needs, "needs");
            Objects.requireNonNull(statement, "statement");
            return new Tools(null, null, null, false, List.of(needs.result()), false, false, null, null, needs,
                    statement);
        }

        public Tools {
            results = List.copyOf(results);
            if (proposal && spec == null) {
                throw new IllegalArgumentException("a proposal shows a spec");
            }
        }

        /** Every number of the Core results, in run order. */
        public List<SourcedNumber> numbers() {
            return results.stream().flatMap(r -> r.numbers().stream()).toList();
        }
    }

    /**
     * The phrasing prompt after exposure filter and budget.
     *
     * @param payload what the exposure filter let through
     * @param phrasing the system and user parts within the budget
     * @param promptHashes file name of each prompt file in the system part to the sha256 of its LF bytes
     * @param ledger every number the turn has a source for; the Number Check matches the draft against it
     */
    public record Prompt(PromptPayload payload, PhrasingPrompt phrasing, SortedMap<String, String> promptHashes,
            NumberLedger ledger) {

        public Prompt {
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(phrasing, "phrasing");
            promptHashes = Collections.unmodifiableSortedMap(new TreeMap<>(promptHashes));
            Objects.requireNonNull(ledger, "ledger");
        }
    }

    /** The phrasing role's first draft with what the Audit Record needs about the call. */
    public record Draft(String text, String modelTag, String modelDigest, boolean truncated) {

        public Draft {
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * The checks of a turn.
     *
     * @param flags what the compliance level switches on
     * @param draftText the first draft as checked and released: with retrieval off, without citation markers
     * @param citationMarkersRemoved how many citation markers were removed from the first draft (retrieval off)
     * @param shadow the Check Chain on the first draft (every level)
     * @param finalResults the Check Chain on the released text: the shadow results, those of the regenerated text, or
     *     none when block routing released a template
     * @param regeneration the one regeneration of the Rule Engine; {@code null} without one
     * @param route block routing's decision; {@code null} before block routing or when routing is off (level A),
     *     except for a Rule Parameter error, which is routed at every level
     * @param routedOn the results block routing decided on when it released a template (the regenerated text's, or
     *     else the enforcing results); empty when the draft was released
     * @param judgeNotImplemented whether the level asked for the Judge, which is not implemented (Story 3.9)
     */
    public record Checks(ComponentFlags flags, String draftText, int citationMarkersRemoved, ChainResult shadow,
            ChainResult finalResults, Regeneration regeneration, BlockRouting.Route route, ChainResult routedOn,
            boolean judgeNotImplemented) {

        public Checks {
            Objects.requireNonNull(flags, "flags");
            Objects.requireNonNull(draftText, "draftText");
            Objects.requireNonNull(shadow, "shadow");
            Objects.requireNonNull(finalResults, "finalResults");
            Objects.requireNonNull(routedOn, "routedOn");
        }

        /** After the shadow checks: the final results are the shadow results until something changes them. */
        public static Checks shadow(ComponentFlags flags, String draftText, int citationMarkersRemoved,
                ChainResult shadow) {
            return new Checks(flags, draftText, citationMarkersRemoved, shadow, shadow, null, null, ChainResult.NONE,
                    false);
        }

        public Checks withRegeneration(Regeneration value, ChainResult results) {
            return new Checks(flags, draftText, citationMarkersRemoved, shadow, results, value, route, routedOn,
                    judgeNotImplemented);
        }

        public Checks withJudgeNotImplemented(boolean value) {
            return new Checks(flags, draftText, citationMarkersRemoved, shadow, finalResults, regeneration, route,
                    routedOn, value);
        }

        /**
         * Block routing's decision. A routed template keeps no final results (it is pre-approved text, not model
         * text); the results it was routed on are kept apart.
         */
        public Checks withRoute(BlockRouting.Route value) {
            return value.released()
                    ? new Checks(flags, draftText, citationMarkersRemoved, shadow, finalResults, regeneration, value,
                            ChainResult.NONE, judgeNotImplemented)
                    : new Checks(flags, draftText, citationMarkersRemoved, shadow, ChainResult.NONE, regeneration,
                            value, finalResults, judgeNotImplemented);
        }

        public boolean regenerated() {
            return regeneration != null;
        }

        /** The draft block routing decides on: the regenerated text, or the first draft if there is none or it is blank. */
        public String routedDraft() {
            return regenerated() && !regeneration.text().isBlank() ? regeneration.text() : draftText;
        }
    }

    /** The Rule Engine's one regeneration: the fix sent and the model's new text. */
    public record Regeneration(String fix, String text, boolean truncated) {

        public Regeneration {
            Objects.requireNonNull(fix, "fix");
            Objects.requireNonNull(text, "text");
        }
    }

    public TurnState {
        Objects.requireNonNull(userText, "userText");
        Objects.requireNonNull(tools, "tools");
        passages = List.copyOf(passages);
        suggestions = List.copyOf(suggestions);
        stagesRun = List.copyOf(stagesRun);
        passThroughStages = List.copyOf(passThroughStages);
    }

    public static TurnState start(String userText) {
        return new TurnState(userText, null, null, null, Tools.NONE, List.of(), null, null, null, null, null, List.of(), false,
                List.of(), List.of(), null);
    }

    /** The class, once the classify stage ran. */
    public MessageClass messageClass() {
        return classification == null ? null : classification.messageClass();
    }

    /** This state after {@code stage} ran and did nothing. */
    public TurnState passedThrough(TurnStage stage) {
        return new TurnState(userText, dialogState, nlu, classification, tools, passages, prompt, draft, checks, outcome,
                finalText, suggestions, noValidSource, plus(stagesRun, stage), plus(passThroughStages, stage), previous);
    }

    /** This state after {@code stage} ran with the given result. */
    public TurnState ran(TurnStage stage) {
        return new TurnState(userText, dialogState, nlu, classification, tools, passages, prompt, draft, checks, outcome,
                finalText, suggestions, noValidSource, plus(stagesRun, stage), passThroughStages, previous);
    }

    /** This state with the previous released turn the NLU stage filtered; {@code null} without one. */
    public TurnState withPrevious(PromptPayload.HistoryTurn value) {
        return new TurnState(userText, dialogState, nlu, classification, tools, passages, prompt, draft, checks, outcome,
                finalText, suggestions, noValidSource, stagesRun, passThroughStages, value);
    }

    /** The previous turn as a history list: empty or one turn. */
    public List<PromptPayload.HistoryTurn> history() {
        return previous == null ? List.of() : List.of(previous);
    }

    public TurnState withDialogState(String state) {
        return new TurnState(userText, state, nlu, classification, tools, passages, prompt, draft, checks, outcome,
                finalText, suggestions, noValidSource, stagesRun, passThroughStages, previous);
    }

    public TurnState withNlu(Nlu value) {
        return new TurnState(userText, dialogState, value, classification, tools, passages, prompt, draft, checks, outcome,
                finalText, suggestions, noValidSource, stagesRun, passThroughStages, previous);
    }

    public TurnState withClassification(Classification value) {
        return new TurnState(userText, dialogState, nlu, value, tools, passages, prompt, draft, checks, outcome,
                finalText, suggestions, noValidSource, stagesRun, passThroughStages, previous);
    }

    public TurnState withTools(Tools value) {
        return new TurnState(userText, dialogState, nlu, classification, value, passages, prompt, draft, checks,
                outcome, finalText, suggestions, noValidSource, stagesRun, passThroughStages, previous);
    }

    public TurnState withPassages(List<PromptPayload.Passage> value) {
        return new TurnState(userText, dialogState, nlu, classification, tools, value, prompt, draft, checks, outcome,
                finalText, suggestions, noValidSource, stagesRun, passThroughStages, previous);
    }

    public TurnState withPrompt(Prompt value) {
        return new TurnState(userText, dialogState, nlu, classification, tools, passages, value, draft, checks, outcome,
                finalText, suggestions, noValidSource, stagesRun, passThroughStages, previous);
    }

    public TurnState withDraft(Draft value) {
        return new TurnState(userText, dialogState, nlu, classification, tools, passages, prompt, value, checks, outcome,
                finalText, suggestions, noValidSource, stagesRun, passThroughStages, previous);
    }

    public TurnState withChecks(Checks value) {
        return new TurnState(userText, dialogState, nlu, classification, tools, passages, prompt, draft, value, outcome,
                finalText, suggestions, noValidSource, stagesRun, passThroughStages, previous);
    }

    public TurnState withOutcome(ResponseOutcome value, String text) {
        return withOutcome(value, text, List.of(), false);
    }

    /**
     * @param chips the suggestion chips released with the text
     * @param fromNoValidSource whether the text is the no-valid-source template
     */
    public TurnState withOutcome(ResponseOutcome value, String text, List<String> chips, boolean fromNoValidSource) {
        return new TurnState(userText, dialogState, nlu, classification, tools, passages, prompt, draft, checks, value, text, chips,
                fromNoValidSource, stagesRun, passThroughStages, previous);
    }

    private static List<TurnStage> plus(List<TurnStage> stages, TurnStage stage) {
        List<TurnStage> result = new ArrayList<>(stages);
        result.add(stage);
        return result;
    }
}
