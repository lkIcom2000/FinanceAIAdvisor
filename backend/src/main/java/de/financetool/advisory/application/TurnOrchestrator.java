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
import de.financetool.advisory.domain.MessageClass;
import de.financetool.advisory.domain.ResponseTemplates;
import de.financetool.advisory.domain.ToolPlan;
import de.financetool.advisory.domain.ToolResults;
import de.financetool.advisory.domain.ResponseOutcome;
import de.financetool.advisory.domain.TurnStage;
import de.financetool.advisory.domain.TurnState;
import de.financetool.audit.AuditEntry;
import de.financetool.audit.AuditKind;
import de.financetool.audit.AuditWriter;
import de.financetool.escalation.EscalationCases;
import de.financetool.knowledge.NoValidRuleParameterException;
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
import de.financetool.shared.domain.SimulationSpec;
import de.financetool.shared.domain.SourcedNumber;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /**
     * A confirmed parameter proposal: Calculate runs the turn of the proposal's question with this spec.
     *
     * @param proposal the proposal message (agent, status {@code OPEN})
     * @param spec the proposal's spec with the values the persona confirmed or changed
     */
    public record Confirmation(ChatMessage proposal, SimulationSpec spec) {

        public Confirmation {
            Objects.requireNonNull(proposal, "proposal");
            Objects.requireNonNull(spec, "spec");
        }
    }

    private final ChatRepository chats;
    private final PromptFiles prompts;
    private final LlmPort llm;
    private final AuditWriter audit;
    private final NluExtractor nluExtractor;
    private final EntityRegistry registry;
    private final ProfileCompleteness completeness;
    private final EscalationCases escalations;
    private final RuleParamPort ruleParameters;
    private final ToolRegistry tools;

    TurnOrchestrator(ChatRepository chats, PromptFiles prompts, LlmPort llm, AuditWriter audit,
            NluExtractor nluExtractor, EntityRegistry registry,
            ProfileCompleteness completeness, EscalationCases escalations, RuleParamPort ruleParameters,
            ToolRegistry tools) {
        this.chats = chats;
        this.prompts = prompts;
        this.llm = llm;
        this.audit = audit;
        this.nluExtractor = nluExtractor;
        this.registry = registry;
        this.completeness = completeness;
        this.escalations = escalations;
        this.ruleParameters = ruleParameters;
        this.tools = tools;
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

    private ChatMessage run(RequestContext context, PersonaScope scope, ChatMessage question,
            Confirmation confirmation, String fixtureVersion) {
        TurnState turn = TurnState.start(question.text());
        turn = dialogState(scope, turn, confirmation);
        turn = nlu(context, scope, turn);
        turn = classify(context, turn, confirmation);
        turn = toolPlan(context, turn);
        turn = kbRetrieve(turn);
        turn = exposureFilter(context, scope, question, turn);
        turn = llmDraft(context, turn);
        turn = shadowChecks(context, turn);
        turn = ruleEngine(context, turn);
        turn = judge(turn);
        turn = blockRouting(turn);
        return auditAndRelease(context, scope, question, confirmation, fixtureVersion, turn);
    }

    /**
     * Consulted first (AD-24). An open parameter proposal ends here: a new question cancels it, Calculate marks it
     * calculated; either way the dialog state returns to {@code NONE}. Without an open proposal the state passes on.
     *
     * @throws CalculateUseCase.ProposalClosedException if Calculate's proposal is no longer the open one
     */
    TurnState dialogState(PersonaScope scope, TurnState turn, Confirmation confirmation) {
        TurnState consulted = turn.withDialogState(chats.dialogState(scope).orElse(TurnState.NO_DIALOG_STATE));
        Optional<UUID> open = chats.openProposal(scope);
        if (confirmation != null) {
            UUID proposalId = confirmation.proposal().id();
            if (!open.equals(Optional.of(proposalId)) || !chats.updateProposalStatus(scope, proposalId,
                    ChatMessage.Proposal.Status.OPEN, ChatMessage.Proposal.Status.CALCULATED)) {
                throw new CalculateUseCase.ProposalClosedException();
            }
        } else if (open.isPresent()) {
            chats.updateProposalStatus(scope, open.get(), ChatMessage.Proposal.Status.OPEN,
                    ChatMessage.Proposal.Status.CANCELLED);
        } else {
            return consulted.passedThrough(TurnStage.DIALOG_STATE);
        }
        chats.saveDialogState(scope, chats.conversation(scope), null);
        return consulted.ran(TurnStage.DIALOG_STATE);
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
     * Pure rules pick exactly one class (FR-9); no stored profile until Epic 6. A message is computable if the pure
     * {@link ToolPlan} has every parameter (the experiment plans before it classifies); a portfolio from account
     * data needs investments above zero, so their value is read here. A confirmed proposal is {@code COMPUTE}.
     */
    TurnState classify(RequestContext context, TurnState turn, Confirmation confirmation) {
        if (confirmation != null) {
            Classification confirmed = new Classification(MessageClass.COMPUTE,
                    List.of("parameter proposal confirmed"), List.of(), List.of(), List.of(),
                    turn.nlu().extraction().topic());
            return turn.withClassification(confirmed)
                    .withTools(new TurnState.Tools(null, null, confirmation.spec(), false, List.of(), false))
                    .ran(TurnStage.CLASSIFY);
        }
        Optional<ToolPlan.Planned> planned = ToolPlan.plan(turn.nlu().extraction(), turn.userText());
        SourcedNumber accountPortfolio = null;
        if (planned.isPresent() && planned.get().portfolioFromAccount()) {
            accountPortfolio = tools.accountPortfolio(context).filter(f -> f.value().signum() > 0).orElse(null);
            if (accountPortfolio == null) {
                planned = Optional.empty();
            }
        }
        Classification classification = Classifier.classify(turn.nlu().extraction(), Map.of(), completeness,
                registry, planned.isPresent(), turn.userText());
        return turn.withClassification(classification)
                .withTools(new TurnState.Tools(planned.orElse(null), accountPortfolio, null, false, List.of(), false))
                .ran(TurnStage.CLASSIFY);
    }

    /**
     * Stage 3, only for {@code COMPUTE}: parameters from the message become a proposal and nothing is computed
     * (AD-24); a confirmed spec runs its whitelisted tool through the {@link ToolRegistry}. A tool missing a valid
     * Rule Parameter version marks the turn for {@code ESCALATE}.
     */
    TurnState toolPlan(RequestContext context, TurnState turn) {
        TurnState.Tools planned = turn.tools();
        if (turn.messageClass() != MessageClass.COMPUTE) {
            return turn.passedThrough(TurnStage.TOOL_PLAN);
        }
        if (planned.spec() == null) {
            SimulationSpec spec = ToolPlan.spec(planned.planned(), planned.accountPortfolio());
            return turn.withTools(new TurnState.Tools(planned.planned(), planned.accountPortfolio(), spec, true,
                    List.of(), false)).ran(TurnStage.TOOL_PLAN);
        }
        try {
            return turn.withTools(new TurnState.Tools(null, null, planned.spec(), false,
                    tools.run(context, planned.spec()), false)).ran(TurnStage.TOOL_PLAN);
        } catch (NoValidRuleParameterException e) {
            return turn.withTools(new TurnState.Tools(null, null, planned.spec(), false, List.of(), true))
                    .ran(TurnStage.TOOL_PLAN);
        }
    }

    /** Pass-through until the Knowledge Base exists (Epic 4). */
    TurnState kbRetrieve(TurnState turn) {
        return turn.withPassages(List.of()).passedThrough(TurnStage.KB_RETRIEVE);
    }

    /**
     * Applies the Data Exposure Level to the message, the last released turns, the profile context and the Core
     * results, and fits the phrasing prompt into the context budget (FR-43, FR-45, AD-5); the system part ends with
     * the class instruction. The Core results go in as {@code TOOL RESULTS}; no passages or profile exist yet. The
     * turn's {@link NumberLedger} is built from the same inputs plus the Rule Parameters valid at the as-of date, with
     * every Core number first (label: its {@code provId}).
     */
    TurnState exposureFilter(RequestContext context, PersonaScope scope, ChatMessage question, TurnState turn) {
        AgentConfiguration agent = context.agentConfig();
        List<PromptPayload.HistoryTurn> history =
                chats.releasedTurnsBefore(scope, question.id(), ExposureFilter.HISTORY_TURNS);
        PromptPayload payload = ExposureFilter.apply(level(agent.dataExposure()), turn.userText(),
                turn.nlu().extraction().depersonalizedQuestion(), history, Map.of(),
                ToolResults.format(turn.tools().results()), turn.passages());
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
        NumberLedger ledger = NumberLedger.of(turn.tools().numbers(), turn.userText(), payload,
                phrasing.droppedHistoryTurns(), passagesInPrompt(payload, phrasing), parameters);
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
     * draft fails the turn only if block routing would release it. A parameter proposal is not phrased: its draft is
     * the fixed proposal text.
     */
    TurnState llmDraft(RequestContext context, TurnState turn) {
        if (turn.tools().proposal()) {
            return turn.withDraft(new TurnState.Draft(ResponseTemplates.PARAMETER_PROPOSAL, null, null, false))
                    .passedThrough(TurnStage.LLM_DRAFT);
        }
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
        if (checks.flags().shadowMode() || turn.tools().ruleParameterError()) {
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
            ResponseOutcome outcome = ResponseOutcome.of(turn.messageClass());
            return turn.withOutcome(outcome, releasable(checks.draftText()), chips(outcome, turn), false)
                    .passedThrough(TurnStage.BLOCK_ROUTING);
        }
        BlockRouting.Route route = BlockRouting.route(checks.finalResults().failed(), turn.classification(),
                turn.tools().ruleParameterError());
        TurnState routed = turn.withChecks(checks.withRoute(route));
        if (route.released()) {
            String text = checks.regenerated() ? checks.regeneration().text() : checks.draftText();
            ResponseOutcome outcome = ResponseOutcome.of(turn.messageClass());
            return routed.withOutcome(outcome, releasable(text), chips(outcome, turn), false)
                    .ran(TurnStage.BLOCK_ROUTING);
        }
        List<String> chips = route.noValidSource()
                ? List.of(NoValidSource.suggestion(turn.nlu().extraction().topic(), turn.userText()))
                : chips(route.outcome(), turn);
        return routed.withOutcome(route.outcome(), route.text(), chips, route.noValidSource())
                .ran(TurnStage.BLOCK_ROUTING);
    }

    /**
     * The fixed suggestion chips of an outcome (UX-DR11): a refusal offers the in-scope next steps, a trap the related
     * in-scope question of its topic; other outcomes have none.
     */
    private static List<String> chips(ResponseOutcome outcome, TurnState turn) {
        return switch (outcome) {
            case REFUSE_OUT_OF_SCOPE -> ResponseTemplates.REFUSAL_SUGGESTIONS;
            case DETECT_TRAP -> List.of(NoValidSource.suggestion(turn.nlu().extraction().topic(), turn.userText()));
            default -> List.of();
        };
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
     * Record; the turn id is the agent message's id. A released proposal opens the {@code PARAMETER_PROPOSAL} dialog
     * state; a calculated answer replies to its proposal and lists its figures, unless block routing released a
     * template.
     */
    ChatMessage auditAndRelease(RequestContext context, PersonaScope scope, ChatMessage question,
            Confirmation confirmation, String fixtureVersion, TurnState turn) {
        TurnState released = turn.ran(TurnStage.AUDIT_RELEASE);
        UUID turnId = UUID.randomUUID();
        TurnState.Tools turnTools = released.tools();
        boolean proposal = turnTools.proposal() && released.outcome() == ResponseOutcome.COMPUTE;
        // Under level A the results are shadow results: they stay in the Audit Record, the card shows no checks. A
        // proposal is fixed text; its card shows the parameters instead.
        List<ChatMessage.Check> shownChecks = released.checks().flags().shadowMode() || proposal ? List.of()
                : released.checks().finalResults().applicable().stream().map(ChatMessage.Check::of).toList();
        boolean draftReleased = released.checks().route() == null || released.checks().route().released();
        List<SourcedNumber> figures = turnTools.spec() != null && !turnTools.proposal() && draftReleased
                ? ToolRegistry.figures(turnTools.spec(), turnTools.results()) : List.of();
        ChatMessage answer = new ChatMessage(turnId, ChatMessage.Author.AGENT, released.finalText(),
                released.outcome(), confirmation == null ? question.id() : confirmation.proposal().id(),
                released.suggestions(), shownChecks, null,
                proposal ? new ChatMessage.Proposal(turnTools.spec(), ChatMessage.Proposal.Status.OPEN) : null,
                figures, released.outcome() == ResponseOutcome.DETECT_TRAP
                        ? ResponseTemplates.premiseCallout(released.classification().unverifiedPremises(),
                                released.userText()) : null);
        UUID conversation = chats.conversation(scope);
        chats.insert(scope, conversation, answer);
        if (proposal) {
            chats.saveDialogState(scope, conversation, turnId);
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
                        turnTools.spec(), proposal, turnTools.ruleParameterError())));
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
