package de.financetool.advisory.application;

import de.financetool.advisory.adapter.ChatRepository;
import de.financetool.advisory.domain.BlockRouting;
import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.Classification;
import de.financetool.advisory.domain.HandoverReason;
import de.financetool.advisory.domain.PhrasingPrompt;
import de.financetool.advisory.domain.Premise;
import de.financetool.advisory.domain.ResponseOutcome;
import de.financetool.advisory.domain.ResponseTemplates;
import de.financetool.advisory.domain.TurnStage;
import de.financetool.advisory.domain.TurnState;
import de.financetool.audit.AuditEntry;
import de.financetool.audit.AuditKind;
import de.financetool.audit.AuditWriter;
import de.financetool.escalation.EscalationCases;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.SourcedNumber;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Stage 11 of a turn ({@link TurnOrchestrator}): the agent message, its case and the Audit Record.
 */
@Component
class ReleaseStage {

    private final ChatRepository chats;
    private final AuditWriter audit;
    private final EscalationCases escalations;
    private final AssessmentCards assessments;
    private final Recommendations recommendations;

    ReleaseStage(ChatRepository chats, AuditWriter audit, EscalationCases escalations, AssessmentCards assessments,
            Recommendations recommendations) {
        this.chats = chats;
        this.audit = audit;
        this.escalations = escalations;
        this.assessments = assessments;
        this.recommendations = recommendations;
    }

    /**
     * Writes the agent message, its evidence, an {@code ESCALATE} turn's case for a human advisor and the Audit
     * Record; the turn id is the agent message's id. A released proposal opens the {@code PARAMETER_PROPOSAL} dialog
     * state, an {@code ASK_PROFILE} answer with fields to ask the {@code ASSESSMENT} state with its card; a calculated
     * answer replies to its proposal and lists its figures, unless block routing released a template; a resumed
     * answer replies to its card.
     */
    ChatMessage auditAndRelease(RequestContext context, PersonaScope scope, ChatMessage question,
            TurnOrchestrator.Continuation continuation, String fixtureVersion, TurnState turn) {
        TurnState released = turn.ran(TurnStage.AUDIT_RELEASE);
        UUID turnId = UUID.randomUUID();
        TurnState.Tools turnTools = released.tools();
        boolean proposal = turnTools.proposal() && released.outcome() == ResponseOutcome.COMPUTE;
        // Under level A the results are shadow results: they stay in the Audit Record, the card shows no checks. A
        // proposal is fixed text; its card shows the parameters instead.
        List<ChatMessage.Check> shownChecks = released.checks().flags().shadowMode() || proposal ? List.of()
                : released.checks().finalResults().applicable().stream().map(ChatMessage.Check::of).toList();
        boolean draftReleased = released.checks().route() == null || released.checks().route().released();
        boolean releasedRecommendation = released.outcome() == ResponseOutcome.RECOMMEND_CATEGORY && draftReleased;
        boolean recommended = releasedRecommendation && turnTools.recommendation() != null;
        boolean needsStated = releasedRecommendation && turnTools.needsStatement() != null;
        List<SourcedNumber> figures = turnTools.spec() != null && !turnTools.proposal() && draftReleased
                ? ToolRegistry.figures(turnTools.spec(), turnTools.results())
                : recommended ? recommendations.chips(context, turnTools.recommendation())
                : needsStated ? turnTools.needsStatement().figures() : List.of();
        ChatMessage.Assessment assessment = released.outcome() == ResponseOutcome.ASK_PROFILE
                ? assessments.open(context, released.classification().missingFields()).orElse(null) : null;
        ChatMessage answer = new ChatMessage(turnId, ChatMessage.Author.AGENT, released.finalText(),
                released.outcome(), continuation == null ? question.id() : continuation.card().id(),
                released.suggestions(), shownChecks, null,
                proposal ? new ChatMessage.Proposal(turnTools.spec(), ChatMessage.Proposal.Status.OPEN) : null,
                figures, released.outcome() == ResponseOutcome.DETECT_TRAP
                        ? ResponseTemplates.premiseCallout(released.classification().unverifiedPremises(),
                                released.userText()) : null, assessment,
                recommended ? turnTools.statement() : null, needsStated ? turnTools.needsStatement() : null,
                draftReleased ? turnTools.range() : null);
        UUID conversation = chats.conversation(scope);
        chats.insert(scope, conversation, answer);
        if (proposal) {
            chats.saveDialogState(scope, conversation, turnId);
        } else if (assessment != null) {
            chats.saveAssessmentState(scope, conversation, turnId, assessment.keys());
        }
        UUID caseId = null;
        if (released.outcome() == ResponseOutcome.ESCALATE) {
            Optional<HandoverReason> reason = HandoverReason.of(released.checks().route().route());
            caseId = escalations.openForTurn(scope, snapshot(turnId, question, released.checks(),
                    reason.map(HandoverReason::name).orElse(null)));
            answer = answer.withHandover(new ChatMessage.Handover(reason.map(HandoverReason::text).orElse(null),
                    ChatMessage.Handover.Status.OPEN));
        }
        TurnState.Draft draft = released.draft();
        TurnState.Prompt prompt = released.prompt();
        PhrasingPrompt phrasing = prompt.phrasing();
        TurnState.Nlu nlu = released.nlu();
        Classification classification = released.classification();
        TurnState.Checks checks = released.checks();
        BlockRouting.Route route = checks.route();
        TurnState.Regeneration regeneration = checks.regeneration();
        audit.write(context, new AuditEntry(AuditKind.RESPONSE, turnId, null, fixtureVersion,
                new ResponseAuditBody(released.messageClass(), released.outcome(), classification.reasons(),
                        classification.missingFields(),
                        classification.unverifiedPremises().stream().map(Premise::toMap).toList(),
                        classification.outOfScope(), nlu.extraction().toMap(), nlu.groundingNotes(), nlu.modelTag(),
                        nlu.modelDigest(), nlu.promptHashes(), nlu.cacheHit(), nlu.parseOk(),
                        nlu.extraction().instructionOverrideAttempt(), released.noValidSource(),
                        turnTools.numbers().stream().map(SourcedNumber::provId).toList(), checks.shadow().results(),
                        checks.finalResults().results(), checks.routedOn().results(),
                        route == null ? BlockRouting.RELEASED : route.route(),
                        route == null ? List.of() : route.fired(), caseId, checks.flags().shadowMode(), checks.flags(),
                        checks.judgeNotImplemented(), checks.citationMarkersRemoved(), draft.modelTag(), draft.modelDigest(), prompt.promptHashes(),
                        draft.truncated() || phrasing.truncated() || (regeneration != null && regeneration.truncated()),
                        phrasing.droppedHistoryTurns(), phrasing.droppedPassages(),
                        context.agentConfig().dataExposure(), prompt.payload().messageWithheld(), draft.text(),
                        released.finalText(), regeneration != null, regeneration == null ? null : regeneration.fix(),
                        regeneration == null ? null : regeneration.text(), context.agentConfig().compliance().value(),
                        released.passThroughStages(), prompt.ledger().entries(), released.dialogState(),
                        turnTools.spec(), proposal, turnTools.ruleParameterError(),
                        ResponseAuditBody.categorySelection(turnTools.recommendation() == null ? null
                                : turnTools.recommendation().details()),
                        NeedsStatements.audit(turnTools.needs()))));
        return answer;
    }

    /**
     * What the case keeps: the question as written, the draft routing decided on and the block reasons (the route,
     * then {@code CHECK_ID:reason_code} per failed check it was routed on).
     */
    private static EscalationCases.TurnSnapshot snapshot(UUID turnId, ChatMessage question, TurnState.Checks checks,
            String reasonClass) {
        String draft = checks.routedDraft();
        List<String> reasons = new ArrayList<>();
        reasons.add(checks.route().route());
        checks.routedOn().failed().forEach(r -> reasons.add(r.checkId().name() + ":" + r.reasonCode()));
        return new EscalationCases.TurnSnapshot(turnId, question.text(), draft, reasons, reasonClass);
    }
}
