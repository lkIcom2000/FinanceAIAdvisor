package de.financetool.advisory.application;

import de.financetool.advisory.adapter.ChatRepository;
import de.financetool.advisory.adapter.PromptFiles;
import de.financetool.advisory.domain.BlockRouting;
import de.financetool.advisory.domain.ChainResult;
import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.CheckChain;
import de.financetool.advisory.domain.CitationMarkers;
import de.financetool.advisory.domain.ClassInstruction;
import de.financetool.advisory.domain.Classification;
import de.financetool.advisory.domain.Classifier;
import de.financetool.advisory.domain.ComponentFlags;
import de.financetool.advisory.domain.EntityRegistry;
import de.financetool.advisory.domain.ExposureFilter;
import de.financetool.advisory.domain.NoValidSource;
import de.financetool.advisory.domain.NumberLedger;
import de.financetool.advisory.domain.PhrasingPrompt;
import de.financetool.advisory.domain.Premise;
import de.financetool.advisory.domain.ProfileCompleteness;
import de.financetool.advisory.domain.PromptPayload;
import de.financetool.advisory.domain.RegenerationFix;
import de.financetool.advisory.domain.HandoverReason;
import de.financetool.advisory.domain.ResponseOutcome;
import de.financetool.advisory.domain.TurnStage;
import de.financetool.advisory.domain.TurnState;
import de.financetool.audit.AuditEntry;
import de.financetool.audit.AuditKind;
import de.financetool.audit.AuditWriter;
import de.financetool.audit.EvidenceItem;
import de.financetool.audit.EvidenceStore;
import de.financetool.escalation.EscalationCases;
import de.financetool.knowledge.RuleParamPort;
import de.financetool.llm.LlmMessage;
import de.financetool.llm.LlmPort;
import de.financetool.llm.LlmResult;
import de.financetool.llm.LlmRole;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.config.AgentConfiguration;
import de.financetool.shared.config.ComplianceLevel;
import de.financetool.shared.config.DataExposureLevel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** No turn reads a Rule Parameter until Story 3.7's tool plan; then an invalid one routes to ESCALATE. */
    static final boolean RULE_PARAMETER_ERROR = false;

    private final ChatRepository chats;
    private final PromptFiles prompts;
    private final LlmPort llm;
    private final AuditWriter audit;
    private final EvidenceStore evidence;
    private final NluExtractor nluExtractor;
    private final EntityRegistry registry;
    private final ProfileCompleteness completeness;
    private final EscalationCases escalations;
    private final RuleParamPort ruleParameters;

    TurnOrchestrator(ChatRepository chats, PromptFiles prompts, LlmPort llm, AuditWriter audit,
            EvidenceStore evidence, NluExtractor nluExtractor, EntityRegistry registry,
            ProfileCompleteness completeness, EscalationCases escalations, RuleParamPort ruleParameters) {
        this.chats = chats;
        this.prompts = prompts;
        this.llm = llm;
        this.audit = audit;
        this.evidence = evidence;
        this.nluExtractor = nluExtractor;
        this.registry = registry;
        this.completeness = completeness;
        this.escalations = escalations;
        this.ruleParameters = ruleParameters;
    }

    /** Runs the turn for the saved user message {@code question} and releases the agent message. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ChatMessage run(RequestContext context, PersonaScope scope, ChatMessage question, String fixtureVersion) {
        TurnState turn = TurnState.start(question.text());
        turn = dialogState(scope, turn);
        turn = nlu(context, scope, turn);
        turn = classify(turn);
        turn = toolPlan(turn);
        turn = kbRetrieve(turn);
        turn = exposureFilter(context, scope, question, turn);
        turn = llmDraft(context, turn);
        turn = shadowChecks(context, turn);
        turn = ruleEngine(context, turn);
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
    TurnState nlu(RequestContext context, PersonaScope scope, TurnState turn) {
        return turn.withNlu(nluExtractor.extract(context.agentConfig(), scope, turn.userText()))
                .ran(TurnStage.NLU);
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
        return turn.withPassages(List.of()).passedThrough(TurnStage.KB_RETRIEVE);
    }

    /**
     * Applies the Data Exposure Level to the message, the last released turns, the profile context and the Core
     * results, and fits the phrasing prompt into the context budget (FR-43, FR-45, AD-5); the system part ends with
     * the class instruction. No Core results, passages or profile exist yet: those inputs stay empty. The turn's
     * {@link NumberLedger} is built from the same inputs plus the Rule Parameters valid at the as-of date.
     */
    TurnState exposureFilter(RequestContext context, PersonaScope scope, ChatMessage question, TurnState turn) {
        AgentConfiguration agent = context.agentConfig();
        List<PromptPayload.HistoryTurn> history =
                chats.releasedTurnsBefore(scope, question.id(), ExposureFilter.HISTORY_TURNS);
        PromptPayload payload = ExposureFilter.apply(level(agent.dataExposure()), turn.userText(),
                turn.nlu().extraction().depersonalizedQuestion(), history, Map.of(), "", turn.passages());
        PromptFiles.Prompt rolePrompt = prompts.rolePrompt(agent.rolePrompt());
        PromptFiles.Prompt rules = prompts.complianceRules();
        PromptFiles.Prompt instruction = prompts.classInstruction(turn.messageClass());
        String classInstruction = ClassInstruction.fill(instruction.text(), turn.classification(), null, "",
                level(agent.dataExposure()));
        PhrasingPrompt phrasing = PhrasingPrompt.build(rolePrompt.text(), rules.text(), classInstruction, payload,
                agent.decoding().numCtx(), agent.decoding().numPredict());
        Map<String, String> hashes = Map.of(rolePrompt.fileName(), rolePrompt.hash(), rules.fileName(), rules.hash(),
                instruction.fileName(), instruction.hash());
        Map<String, BigDecimal> parameters = new LinkedHashMap<>();
        ruleParameters.allValid(context.asOf()).forEach(p -> parameters.put(p.key(), p.value()));
        NumberLedger ledger = NumberLedger.of(turn.userText(), payload, phrasing.droppedHistoryTurns(),
                passagesInPrompt(payload, phrasing), parameters);
        return turn.withPrompt(new TurnState.Prompt(payload, phrasing, new TreeMap<>(hashes), ledger))
                .ran(TurnStage.EXPOSURE_FILTER);
    }

    /** The domain's exposure level for the configured one; exhaustive, so a new level does not compile unmapped. */
    static ExposureFilter.Level level(DataExposureLevel configured) {
        return switch (configured) {
            case AGGREGATED -> ExposureFilter.Level.AGGREGATED;
            case NONE -> ExposureFilter.Level.NONE;
        };
    }

    /**
     * The phrasing role drafts the answer: prompt files as system message, everything else in the user part. A blank
     * draft fails the turn only if block routing would release it.
     */
    TurnState llmDraft(RequestContext context, TurnState turn) {
        PhrasingPrompt phrasing = turn.prompt().phrasing();
        LlmResult result = llm.generate(LlmRole.PHRASING,
                List.of(LlmMessage.system(phrasing.system()), LlmMessage.user(phrasing.user())),
                context.agentConfig().decoding(), null);
        return turn.withDraft(new TurnState.Draft(result.text() == null ? "" : result.text(), result.modelTag(), result.digest(),
                result.truncated())).ran(TurnStage.LLM_DRAFT);
    }

    /**
     * Stage 7: the Check Chain runs on the first draft at every compliance level; these are the shadow results. Until
     * something changes them, they are also the final results. With retrieval off, citation markers the model added
     * anyway are removed first ({@link CitationMarkers}); the first draft in the record keeps them.
     */
    TurnState shadowChecks(RequestContext context, TurnState turn) {
        ComponentFlags flags = ComponentFlags.of(level(context.agentConfig().compliance()));
        boolean retrieval = context.agentConfig().retrieval();
        CitationMarkers.Stripped draft = retrieval ? new CitationMarkers.Stripped(turn.draft().text(), 0)
                : CitationMarkers.strip(turn.draft().text());
        ChainResult shadow = CheckChain.run(draft.text(), facts(turn, retrieval));
        return turn.withChecks(TurnState.Checks.shadow(flags, draft.text(), draft.removed(), shadow))
                .ran(TurnStage.SHADOW_CHECKS);
    }

    /**
     * Stage 8: in Shadow Mode (level {@code A}) the Rule Engine only records. Enforcing, a repairable failure (a failed
     * Number Check, a missing or invalid citation) gets exactly one regeneration with the targeted fix: the previous
     * messages, the draft as the assistant's answer and the fix as the user's, with the same decoding; the chain then
     * runs again on the regenerated text.
     */
    TurnState ruleEngine(RequestContext context, TurnState turn) {
        TurnState.Checks checks = turn.checks();
        if (checks.flags().shadowMode() || RULE_PARAMETER_ERROR) {
            return turn.ran(TurnStage.RULE_ENGINE);
        }
        Optional<String> fix = RegenerationFix.of(checks.shadow().failed(), passagesInPrompt(turn),
                turn.prompt().payload().toolResults());
        if (fix.isEmpty()) {
            return turn.ran(TurnStage.RULE_ENGINE);
        }
        PhrasingPrompt phrasing = turn.prompt().phrasing();
        LlmResult result = llm.generate(LlmRole.PHRASING,
                List.of(LlmMessage.system(phrasing.system()), LlmMessage.user(phrasing.user()),
                        LlmMessage.assistant(turn.draft().text()), LlmMessage.user(fix.get())),
                context.agentConfig().decoding(), null);
        String text = result.text() == null ? "" : result.text();
        TurnState.Regeneration regeneration = new TurnState.Regeneration(fix.get(), text, result.truncated());
        return turn.withChecks(checks.withRegeneration(regeneration, CheckChain.run(text, facts(turn, context.agentConfig().retrieval()))))
                .ran(TurnStage.RULE_ENGINE);
    }

    /**
     * Stage 9: the Block-only Judge is not implemented (Story 3.9 is skipped). Under {@code A+B+C} the record says
     * so ({@code judgeNotImplemented}); there is never a verdict.
     */
    TurnState judge(TurnState turn) {
        return turn.withChecks(turn.checks().withJudgeNotImplemented(turn.checks().flags().judge()))
                .passedThrough(TurnStage.JUDGE);
    }

    /**
     * Stage 10: with routing on, {@link BlockRouting} decides on the final results: a routed turn releases its
     * template (the no-valid-source one with a related in-scope chip) and keeps no final results; otherwise the
     * (regenerated) draft is released with the class as outcome. With routing off (level {@code A}) the outcome is
     * the class and the draft is released. A blank text that would be released fails the turn.
     */
    TurnState blockRouting(TurnState turn) {
        TurnState.Checks checks = turn.checks();
        if (!checks.flags().routing()) {
            return turn.withOutcome(ResponseOutcome.of(turn.messageClass()), releasable(checks.draftText()))
                    .passedThrough(TurnStage.BLOCK_ROUTING);
        }
        BlockRouting.Route route = BlockRouting.route(checks.finalResults().failed(), turn.classification(),
                RULE_PARAMETER_ERROR);
        TurnState routed = turn.withChecks(checks.withRoute(route));
        if (route.released()) {
            String text = checks.regenerated() ? checks.regeneration().text() : checks.draftText();
            return routed.withOutcome(ResponseOutcome.of(turn.messageClass()), releasable(text))
                    .ran(TurnStage.BLOCK_ROUTING);
        }
        List<String> chips = route.noValidSource()
                ? List.of(NoValidSource.suggestion(turn.nlu().extraction().topic(), turn.userText()))
                : List.of();
        return routed.withOutcome(route.outcome(), route.text(), chips, route.noValidSource())
                .ran(TurnStage.BLOCK_ROUTING);
    }

    private static String releasable(String text) {
        if (text.isBlank()) {
            throw new IllegalStateException("The phrasing model returned an empty answer");
        }
        return text.strip();
    }

    /**
     * What the Check Chain needs besides the text, with the turn's ledger for the Number Check; no Core category
     * selection exists yet (Epic 6).
     * {@code SOURCE_VALIDITY} applies only with retrieval on, as in the experiment.
     */
    private static CheckChain.Facts facts(TurnState turn, boolean retrievalOn) {
        return CheckChain.Facts.of(turn.classification(), turn.userText(), null, retrievalOn, passagesInPrompt(turn),
                turn.prompt().ledger());
    }

    /**
     * The passages the phrasing prompt holds after the budget ({@code len(passages_used)}): the valid citation range;
     * none means no valid source, also when the budget dropped every retrieved passage.
     */
    static int passagesInPrompt(TurnState turn) {
        return passagesInPrompt(turn.prompt().payload(), turn.prompt().phrasing());
    }

    private static int passagesInPrompt(PromptPayload payload, PhrasingPrompt phrasing) {
        return Math.max(0, payload.passages().size() - phrasing.droppedPassages());
    }

    /** The domain's compliance level for the configured one; exhaustive, so a new level does not compile unmapped. */
    static ComponentFlags.Level level(ComplianceLevel configured) {
        return switch (configured) {
            case A -> ComponentFlags.Level.A;
            case A_B -> ComponentFlags.Level.A_B;
            case A_B_C -> ComponentFlags.Level.A_B_C;
        };
    }

    /**
     * Writes the agent message, its evidence, an {@code ESCALATE} turn's case for a human advisor and the Audit
     * Record; the turn id is the agent message's id.
     */
    ChatMessage auditAndRelease(RequestContext context, PersonaScope scope, ChatMessage question,
            String fixtureVersion, TurnState turn) {
        TurnState released = turn.ran(TurnStage.AUDIT_RELEASE);
        UUID turnId = UUID.randomUUID();
        // Under level A the results are shadow results: they stay in the Audit Record, the card shows no checks.
        List<ChatMessage.Check> shownChecks = released.checks().flags().shadowMode() ? List.of()
                : released.checks().finalResults().applicable().stream().map(ChatMessage.Check::of).toList();
        ChatMessage answer = new ChatMessage(turnId, ChatMessage.Author.AGENT, released.finalText(),
                released.outcome(), question.id(), released.suggestions(), shownChecks);
        chats.insert(scope, chats.conversation(scope), answer);
        UUID caseId = null;
        if (released.outcome() == ResponseOutcome.ESCALATE) {
            Optional<HandoverReason> reason = HandoverReason.of(released.checks().route().route());
            caseId = escalations.openForTurn(scope, snapshot(turnId, question, released.checks(),
                    reason.map(HandoverReason::name).orElse(null)));
            answer = answer.withHandover(new ChatMessage.Handover(reason.map(HandoverReason::text).orElse(null),
                    ChatMessage.Handover.Status.OPEN));
        }
        List<EvidenceItem> items = List.of();
        evidence.putIfAbsent(items);
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
                        items.stream().map(EvidenceItem::provId).toList(), checks.shadow().results(),
                        checks.finalResults().results(), checks.routedOn().results(),
                        route == null ? BlockRouting.RELEASED : route.route(),
                        route == null ? List.of() : route.fired(), caseId, checks.flags().shadowMode(), checks.flags(),
                        checks.judgeNotImplemented(), checks.citationMarkersRemoved(), draft.modelTag(), draft.modelDigest(), prompt.promptHashes(),
                        draft.truncated() || phrasing.truncated() || (regeneration != null && regeneration.truncated()),
                        phrasing.droppedHistoryTurns(), phrasing.droppedPassages(),
                        context.agentConfig().dataExposure(), prompt.payload().messageWithheld(), draft.text(),
                        released.finalText(), regeneration != null, regeneration == null ? null : regeneration.fix(),
                        regeneration == null ? null : regeneration.text(), context.agentConfig().compliance().value(),
                        released.passThroughStages(), prompt.ledger().entries())));
        return answer;
    }

    /**
     * What the case keeps: the question as written, the draft routing decided on and the block reasons (the route,
     * then {@code CHECK_ID:reason_code} per failed check it was routed on).
     */
    private static EscalationCases.TurnSnapshot snapshot(UUID turnId, ChatMessage question, TurnState.Checks checks,
            String reasonClass) {
        String draft = checks.regenerated() ? checks.regeneration().text() : checks.draftText();
        List<String> reasons = new ArrayList<>();
        reasons.add(checks.route().route());
        checks.routedOn().failed().forEach(r -> reasons.add(r.checkId().name() + ":" + r.reasonCode()));
        return new EscalationCases.TurnSnapshot(turnId, question.text(), draft, reasons, reasonClass);
    }
}
