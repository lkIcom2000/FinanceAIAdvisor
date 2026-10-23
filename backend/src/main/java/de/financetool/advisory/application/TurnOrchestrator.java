package de.financetool.advisory.application;

import de.financetool.advisory.adapter.ChatRepository;
import de.financetool.advisory.adapter.PromptFiles;
import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.ClassInstruction;
import de.financetool.advisory.domain.Classification;
import de.financetool.advisory.domain.Classifier;
import de.financetool.advisory.domain.EntityRegistry;
import de.financetool.advisory.domain.ExposureFilter;
import de.financetool.advisory.domain.MessageClass;
import de.financetool.advisory.domain.NoValidSource;
import de.financetool.advisory.domain.PhrasingPrompt;
import de.financetool.advisory.domain.Premise;
import de.financetool.advisory.domain.ProfileCompleteness;
import de.financetool.advisory.domain.PromptPayload;
import de.financetool.advisory.domain.ResponseOutcome;
import de.financetool.advisory.domain.TurnStage;
import de.financetool.advisory.domain.TurnState;
import de.financetool.audit.AuditEntry;
import de.financetool.audit.AuditKind;
import de.financetool.audit.AuditWriter;
import de.financetool.audit.EvidenceItem;
import de.financetool.audit.EvidenceStore;
import de.financetool.llm.LlmMessage;
import de.financetool.llm.LlmPort;
import de.financetool.llm.LlmResult;
import de.financetool.llm.LlmRole;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.config.AgentConfiguration;
import de.financetool.shared.config.DataExposureLevel;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only component that runs a turn (AR6): consults the dialog state, then runs the 11 stages in the fixed order
 * of {@link TurnStage}, each as one method. Stages that later stories implement are explicit pass-throughs and are
 * named in the Audit Record. The agent message, its evidence items and the {@code RESPONSE} record are written in
 * the caller's transaction: if any write fails, nothing is released.
 */
@Service
public class TurnOrchestrator {

    private final ChatRepository chats;
    private final PromptFiles prompts;
    private final LlmPort llm;
    private final AuditWriter audit;
    private final EvidenceStore evidence;
    private final NluExtractor nluExtractor;
    private final EntityRegistry registry;
    private final ProfileCompleteness completeness;

    TurnOrchestrator(ChatRepository chats, PromptFiles prompts, LlmPort llm, AuditWriter audit,
            EvidenceStore evidence, NluExtractor nluExtractor, EntityRegistry registry,
            ProfileCompleteness completeness) {
        this.chats = chats;
        this.prompts = prompts;
        this.llm = llm;
        this.audit = audit;
        this.evidence = evidence;
        this.nluExtractor = nluExtractor;
        this.registry = registry;
        this.completeness = completeness;
    }

    /** Runs the turn for the saved user message {@code question} and releases the agent message. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ChatMessage run(RequestContext context, PersonaScope scope, ChatMessage question, String fixtureVersion) {
        TurnState turn = TurnState.start(question.text());
        turn = dialogState(scope, turn);
        turn = nlu(context, turn);
        turn = classify(turn);
        turn = toolPlan(turn);
        turn = kbRetrieve(turn);
        turn = exposureFilter(context, scope, question, turn);
        turn = llmDraft(context, turn);
        turn = shadowChecks(turn);
        turn = ruleEngine(turn);
        turn = judge(turn);
        turn = blockRouting(turn);
        return auditAndRelease(context, scope, question, fixtureVersion, turn);
    }

    /** Consulted first. No dialog flow exists yet (parameter proposals, assessments), so the state only passes on. */
    TurnState dialogState(PersonaScope scope, TurnState turn) {
        return turn.withDialogState(chats.dialogState(scope).orElse(TurnState.NO_DIALOG_STATE))
                .passedThrough(TurnStage.DIALOG_STATE);
    }

    /**
     * The NLU role extracts the message's data (AD-4): cached by model, prompt and message, then grounded by the
     * deterministic rules. A failed model call fails the turn.
     */
    TurnState nlu(RequestContext context, TurnState turn) {
        return turn.withNlu(nluExtractor.extract(context.agentConfig(), turn.userText())).ran(TurnStage.NLU);
    }

    /**
     * Pure rules pick exactly one class (FR-9): no stored profile until Epic 6, and nothing is computable until the
     * tool plan exists (Story 3.7).
     */
    TurnState classify(TurnState turn) {
        Classification classification = Classifier.classify(turn.nlu().extraction(), Map.of(), completeness,
                registry, false, turn.userText());
        return turn.withClassification(classification).ran(TurnStage.CLASSIFY);
    }

    /** Pass-through: no tools are planned yet. */
    TurnState toolPlan(TurnState turn) {
        return turn.passedThrough(TurnStage.TOOL_PLAN);
    }

    /** Pass-through until the Knowledge Base exists (Epic 4). */
    TurnState kbRetrieve(TurnState turn) {
        return turn.passedThrough(TurnStage.KB_RETRIEVE);
    }

    /**
     * Applies the Data Exposure Level to the message, the last released turns, the profile context and the Core
     * results, and fits the phrasing prompt into the context budget (FR-43, FR-45, AD-5); the system part ends with
     * the class instruction. No Core results, passages or profile exist yet: those inputs stay empty.
     */
    TurnState exposureFilter(RequestContext context, PersonaScope scope, ChatMessage question, TurnState turn) {
        AgentConfiguration agent = context.agentConfig();
        List<PromptPayload.HistoryTurn> history =
                chats.releasedTurnsBefore(scope, question.id(), ExposureFilter.HISTORY_TURNS);
        PromptPayload payload = ExposureFilter.apply(level(agent.dataExposure()), turn.userText(),
                turn.nlu().extraction().depersonalizedQuestion(), history, Map.of(), "", List.of());
        PromptFiles.Prompt rolePrompt = prompts.rolePrompt(agent.rolePrompt());
        PromptFiles.Prompt rules = prompts.complianceRules();
        PromptFiles.Prompt instruction = prompts.classInstruction(turn.messageClass());
        String classInstruction = ClassInstruction.fill(instruction.text(), turn.classification(), null, "");
        PhrasingPrompt phrasing = PhrasingPrompt.build(rolePrompt.text(), rules.text(), classInstruction, payload,
                agent.decoding().numCtx(), agent.decoding().numPredict());
        Map<String, String> hashes = Map.of(rolePrompt.fileName(), rolePrompt.hash(), rules.fileName(), rules.hash(),
                instruction.fileName(), instruction.hash());
        return turn.withPrompt(new TurnState.Prompt(payload, phrasing, new TreeMap<>(hashes)))
                .ran(TurnStage.EXPOSURE_FILTER);
    }

    /** The domain's exposure level for the configured one; exhaustive, so a new level does not compile unmapped. */
    static ExposureFilter.Level level(DataExposureLevel configured) {
        return switch (configured) {
            case AGGREGATED -> ExposureFilter.Level.AGGREGATED;
            case NONE -> ExposureFilter.Level.NONE;
        };
    }

    /** The phrasing role drafts the answer: prompt files as system message, everything else in the user part. */
    TurnState llmDraft(RequestContext context, TurnState turn) {
        PhrasingPrompt phrasing = turn.prompt().phrasing();
        LlmResult result = llm.generate(LlmRole.PHRASING,
                List.of(LlmMessage.system(phrasing.system()), LlmMessage.user(phrasing.user())),
                context.agentConfig().decoding(), null);
        if (result.text() == null || result.text().isBlank()) {
            throw new IllegalStateException("The phrasing model returned an empty draft");
        }
        return turn.withDraft(new TurnState.Draft(result.text(), result.modelTag(), result.digest(),
                result.truncated())).ran(TurnStage.LLM_DRAFT);
    }

    /** Pass-through until the Check Chain exists (Story 3.4). */
    TurnState shadowChecks(TurnState turn) {
        return turn.passedThrough(TurnStage.SHADOW_CHECKS);
    }

    /** Pass-through until the Rule Engine exists (Story 3.4). */
    TurnState ruleEngine(TurnState turn) {
        return turn.passedThrough(TurnStage.RULE_ENGINE);
    }

    /** Pass-through while the Judge is skipped (Story 3.9). */
    TurnState judge(TurnState turn) {
        return turn.passedThrough(TurnStage.JUDGE);
    }

    /**
     * Pass-through routing (the outcome is the class, the released text the draft), except that an {@code INFORM}
     * turn without a Knowledge Base passage releases the no-valid-source template with one related in-scope chip
     * (FR-13); its draft stays recorded as first draft.
     */
    TurnState blockRouting(TurnState turn) {
        if (turn.messageClass() == MessageClass.INFORM && turn.prompt().payload().passages().isEmpty()) {
            return turn.withOutcome(ResponseOutcome.INFORM, NoValidSource.TEXT,
                    List.of(NoValidSource.suggestion(turn.nlu().extraction().topic())), true)
                    .ran(TurnStage.BLOCK_ROUTING);
        }
        return turn.withOutcome(ResponseOutcome.of(turn.messageClass()), turn.draft().text().strip())
                .passedThrough(TurnStage.BLOCK_ROUTING);
    }

    /** Writes the agent message, its evidence and the Audit Record; the turn id is the agent message's id. */
    ChatMessage auditAndRelease(RequestContext context, PersonaScope scope, ChatMessage question,
            String fixtureVersion, TurnState turn) {
        TurnState released = turn.ran(TurnStage.AUDIT_RELEASE);
        UUID turnId = UUID.randomUUID();
        ChatMessage answer = new ChatMessage(turnId, ChatMessage.Author.AGENT, released.finalText(),
                released.outcome(), question.id(), released.suggestions());
        chats.insert(scope, chats.conversation(scope), answer);
        List<EvidenceItem> items = List.of();
        evidence.putIfAbsent(items);
        TurnState.Draft draft = released.draft();
        TurnState.Prompt prompt = released.prompt();
        PhrasingPrompt phrasing = prompt.phrasing();
        TurnState.Nlu nlu = released.nlu();
        Classification classification = released.classification();
        audit.write(context, new AuditEntry(AuditKind.RESPONSE, turnId, null, fixtureVersion,
                new ResponseAuditBody(released.messageClass(), released.outcome(), classification.reasons(),
                        classification.missingFields(),
                        classification.unverifiedPremises().stream().map(Premise::toMap).toList(),
                        classification.outOfScope(), nlu.extraction().toMap(), nlu.groundingNotes(), nlu.modelTag(),
                        nlu.modelDigest(), nlu.promptHashes(), nlu.cacheHit(), nlu.parseOk(),
                        nlu.extraction().instructionOverrideAttempt(), released.noValidSource(),
                        items.stream().map(EvidenceItem::provId).toList(), List.of(), draft.modelTag(),
                        draft.modelDigest(), prompt.promptHashes(), draft.truncated() || phrasing.truncated(),
                        phrasing.droppedHistoryTurns(), phrasing.droppedPassages(),
                        context.agentConfig().dataExposure(), prompt.payload().messageWithheld(), draft.text(),
                        released.finalText(), false, context.agentConfig().compliance().value(),
                        released.passThroughStages())));
        return answer;
    }
}
