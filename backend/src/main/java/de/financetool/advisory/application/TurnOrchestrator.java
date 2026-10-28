package de.financetool.advisory.application;

import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.TurnStage;
import de.financetool.advisory.domain.TurnState;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.SimulationSpec;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only component that runs a turn (AR6): consults the dialog state, then runs the 11 stages in the fixed order
 * of {@link TurnStage}, each as one method. It only sequences: the stage logic lives in {@link ClassifyStage},
 * {@link ToolPlanStage}, {@link PromptStage}, {@link DraftStage}, {@link CheckStages}, {@link BlockRoutingStage} and
 * {@link ReleaseStage}. Stages that later stories implement are explicit pass-throughs and are named in the Audit
 * Record. The agent message, its evidence items and the {@code RESPONSE} record are written in
 * the caller's transaction: if any write fails, nothing is released.
 */
@Service
public class TurnOrchestrator {

    /** A turn that continues an open card (AD-24): its answer replies to the card, not to the question. */
    public sealed interface Continuation permits Confirmation, SavedAssessment {

        /** The card message the answer replies to. */
        ChatMessage card();
    }

    /**
     * A confirmed parameter proposal: Calculate runs the turn of the proposal's question with this spec.
     *
     * @param proposal the proposal message (agent, status {@code OPEN})
     * @param spec the proposal's spec with the values the persona confirmed or changed
     */
    public record Confirmation(ChatMessage proposal, SimulationSpec spec) implements Continuation {

        public Confirmation {
            Objects.requireNonNull(proposal, "proposal");
            Objects.requireNonNull(spec, "spec");
        }

        @Override
        public ChatMessage card() {
            return proposal;
        }
    }

    /**
     * A saved assessment card: the answers are stored, so the card's question runs again on the stored profile.
     *
     * @param card the assessment message (agent, status {@code OPEN})
     */
    public record SavedAssessment(ChatMessage card) implements Continuation {

        public SavedAssessment {
            Objects.requireNonNull(card, "card");
        }
    }

    private final DialogStates dialogStates;
    private final NluExtractor nluExtractor;
    private final ClassifyStage classifyStage;
    private final ToolPlanStage toolPlanStage;
    private final PromptStage promptStage;
    private final DraftStage draftStage;
    private final CheckStages checkStages;
    private final BlockRoutingStage blockRoutingStage;
    private final ReleaseStage releaseStage;

    TurnOrchestrator(DialogStates dialogStates, NluExtractor nluExtractor, ClassifyStage classifyStage,
            ToolPlanStage toolPlanStage, PromptStage promptStage, DraftStage draftStage, CheckStages checkStages,
            BlockRoutingStage blockRoutingStage, ReleaseStage releaseStage) {
        this.dialogStates = dialogStates;
        this.nluExtractor = nluExtractor;
        this.classifyStage = classifyStage;
        this.toolPlanStage = toolPlanStage;
        this.promptStage = promptStage;
        this.draftStage = draftStage;
        this.checkStages = checkStages;
        this.blockRoutingStage = blockRoutingStage;
        this.releaseStage = releaseStage;
    }


    /** Runs the turn for the saved user message {@code question} and releases the agent message. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ChatMessage run(RequestContext context, PersonaScope scope, ChatMessage question, String fixtureVersion) {
        return run(context, scope, question, null, fixtureVersion);
    }

    /**
     * Calculate on a parameter proposal: runs the turn of the proposal's {@code question} again with the confirmed
     * spec and releases the calculated answer as the proposal's reply.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ChatMessage calculate(RequestContext context, PersonaScope scope, ChatMessage question,
            Confirmation confirmation, String fixtureVersion) {
        return run(context, scope, question, Objects.requireNonNull(confirmation, "confirmation"), fixtureVersion);
    }

    /**
     * Save on an assessment card: the answers are stored already; runs the turn of the card's {@code question} again
     * on the stored profile and releases the answer as the card's reply.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ChatMessage resume(RequestContext context, PersonaScope scope, ChatMessage question,
            SavedAssessment saved, String fixtureVersion) {
        return run(context, scope, question, Objects.requireNonNull(saved, "saved"), fixtureVersion);
    }

    private ChatMessage run(RequestContext context, PersonaScope scope, ChatMessage question,
            Continuation continuation, String fixtureVersion) {
        TurnState turn = TurnState.start(question.text());
        turn = dialogState(scope, turn, continuation);
        turn = nlu(context, scope, turn);
        turn = classify(context, scope, turn, continuation instanceof Confirmation c ? c : null);
        turn = toolPlan(context, scope, turn);
        turn = kbRetrieve(turn);
        turn = exposureFilter(context, scope, question, turn);
        turn = llmDraft(context, turn);
        turn = shadowChecks(context, turn);
        turn = ruleEngine(context, turn);
        turn = judge(turn);
        turn = blockRouting(turn);
        return auditAndRelease(context, scope, question, continuation, fixtureVersion, turn);
    }

    /** Stage 1, consulted first (AD-24): see {@link DialogStates}. */
    TurnState dialogState(PersonaScope scope, TurnState turn, Continuation continuation) {
        return dialogStates.consult(scope, turn, continuation);
    }

    /**
     * The NLU role extracts the message's data (AD-4): cached by model, prompt and message, then grounded by the
     * deterministic rules. A failed model call fails the turn.
     */
    TurnState nlu(RequestContext context, PersonaScope scope, TurnState turn) {
        return turn.withNlu(nluExtractor.extract(context.agentConfig(), scope, turn.userText()))
                .ran(TurnStage.NLU);
    }

    /** Stage 2: see {@link ClassifyStage}. */
    TurnState classify(RequestContext context, PersonaScope scope, TurnState turn, Confirmation confirmation) {
        return classifyStage.classify(context, scope, turn, confirmation);
    }

    /** Stage 3: see {@link ToolPlanStage}. */
    TurnState toolPlan(RequestContext context, PersonaScope scope, TurnState turn) {
        return toolPlanStage.toolPlan(context, scope, turn);
    }


    /** Pass-through until the Knowledge Base exists (Epic 4). */
    TurnState kbRetrieve(TurnState turn) {
        return turn.withPassages(List.of()).passedThrough(TurnStage.KB_RETRIEVE);
    }

    /** Stage 5: see {@link PromptStage}. */
    TurnState exposureFilter(RequestContext context, PersonaScope scope, ChatMessage question, TurnState turn) {
        return promptStage.exposureFilter(context, scope, question, turn);
    }

    /** Stage 6: see {@link DraftStage}. */
    TurnState llmDraft(RequestContext context, TurnState turn) {
        return draftStage.llmDraft(context, turn);
    }

    /** Stage 7: see {@link CheckStages}. */
    TurnState shadowChecks(RequestContext context, TurnState turn) {
        return checkStages.shadowChecks(context, turn);
    }

    /** Stage 8: see {@link CheckStages}. */
    TurnState ruleEngine(RequestContext context, TurnState turn) {
        return checkStages.ruleEngine(context, turn);
    }

    /** Stage 9: see {@link CheckStages}. */
    TurnState judge(TurnState turn) {
        return checkStages.judge(turn);
    }

    /** Stage 10: see {@link BlockRoutingStage}. */
    TurnState blockRouting(TurnState turn) {
        return blockRoutingStage.blockRouting(turn);
    }

    /** Stage 11: see {@link ReleaseStage}. */
    ChatMessage auditAndRelease(RequestContext context, PersonaScope scope, ChatMessage question,
            Continuation continuation, String fixtureVersion, TurnState turn) {
        return releaseStage.auditAndRelease(context, scope, question, continuation, fixtureVersion, turn);
    }
}
