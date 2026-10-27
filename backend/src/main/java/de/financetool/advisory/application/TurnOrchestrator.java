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
import de.financetool.advisory.domain.NumberParser;
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
import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.WhatIfProjection;
import de.financetool.escalation.EscalationCases;
import de.financetool.knowledge.NoValidRuleParameterException;
import de.financetool.knowledge.RuleParamPort;
import de.financetool.llm.LlmMessage;
import de.financetool.llm.LlmPort;
import de.financetool.llm.LlmResult;
import de.financetool.llm.LlmRole;
import de.financetool.profile.Profiles;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.config.AgentConfiguration;
import de.financetool.shared.config.ComplianceLevel;
import de.financetool.shared.config.DataExposureLevel;
import de.financetool.shared.domain.SimulationSpec;
import de.financetool.shared.domain.SourcedNumber;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final Profiles profiles;
    private final DialogStates dialogStates;
    private final AssessmentCards assessments;
    private final Recommendations recommendations;
    private final NeedsStatements needsStatements;

    TurnOrchestrator(ChatRepository chats, PromptFiles prompts, LlmPort llm, AuditWriter audit,
            NluExtractor nluExtractor, EntityRegistry registry,
            ProfileCompleteness completeness, EscalationCases escalations, RuleParamPort ruleParameters,
            ToolRegistry tools, Profiles profiles, DialogStates dialogStates, AssessmentCards assessments,
            Recommendations recommendations, NeedsStatements needsStatements) {
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
        this.profiles = profiles;
        this.dialogStates = dialogStates;
        this.assessments = assessments;
        this.recommendations = recommendations;
        this.needsStatements = needsStatements;
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

    /**
     * Pure rules pick exactly one class (FR-9) on the persona's stored profile and the facts the message states
     * (AD-23; the stated facts are never stored); a persona workspace turn (no acceptance run) follows the strict
     * FR-17 rule. A message is computable if the pure
     * {@link ToolPlan} has every parameter (the experiment plans before it classifies); a portfolio from account
     * data needs investments above zero, so their value is read here. A confirmed proposal is {@code COMPUTE}.
     */
    TurnState classify(RequestContext context, PersonaScope scope, TurnState turn, Confirmation confirmation) {
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
        Classification classification = Classifier.classify(turn.nlu().extraction(), profiles.stored(scope),
                completeness, registry, planned.isPresent(), turn.userText(), context.runId() == null);
        return turn.withClassification(classification)
                .withTools(new TurnState.Tools(planned.orElse(null), accountPortfolio, null, false, List.of(), false))
                .ran(TurnStage.CLASSIFY);
    }

    /**
     * Stage 3, only for {@code COMPUTE}: parameters from the message become a proposal and nothing is computed
     * (AD-24); a confirmed spec runs its whitelisted tool through the {@link ToolRegistry}. A tool missing a valid
     * Rule Parameter version marks the turn for {@code ESCALATE}. Before that, for every class but refusals and
     * traps, a subsidy question about a later year needs the Core's subsidy rules for that year (UJ-5); without them
     * the turn escalates too. A {@code RECOMMEND_CATEGORY} turn runs the Core's category selection and cost
     * calculation on the effective profile ({@link Recommendations}); on a protection topic the Core's
     * demands-and-needs statement instead ({@link NeedsStatements}).
     */
    TurnState toolPlan(RequestContext context, PersonaScope scope, TurnState turn) {
        TurnState.Tools planned = turn.tools();
        MessageClass messageClass = turn.messageClass();
        Optional<LocalDate> laterYear = messageClass == MessageClass.REFUSE_OUT_OF_SCOPE
                || messageClass == MessageClass.DETECT_TRAP ? Optional.empty()
                : ToolPlan.laterSubsidyYear(turn.nlu().extraction(), turn.userText(), context.asOf());
        if (laterYear.isPresent()) {
            try {
                tools.requireSubsidyRules(laterYear.get());
            } catch (NoValidRuleParameterException e) {
                return turn.withTools(TurnState.Tools.laterYearRuleMissing()).ran(TurnStage.TOOL_PLAN);
            }
        }
        if (messageClass == MessageClass.RECOMMEND_CATEGORY
                && CheckChain.protectionTopic(turn.nlu().extraction().topic())) {
            return turn.withTools(needsStatements.run(context, profiles.stored(scope),
                    turn.nlu().extraction().statedProfile())).ran(TurnStage.TOOL_PLAN);
        }
        if (messageClass == MessageClass.RECOMMEND_CATEGORY) {
            return turn.withTools(recommendations.run(context, profiles.stored(scope),
                    turn.nlu().extraction().statedProfile(), turn.nlu().extraction().topic()))
                    .ran(TurnStage.TOOL_PLAN);
        }
        if (turn.messageClass() != MessageClass.COMPUTE) {
            return turn.passedThrough(TurnStage.TOOL_PLAN);
        }
        if (planned.spec() == null) {
            // A what-if with a subsidy row needs this year's subsidy rules; without them the proposal would only
            // escalate at Calculate, so the turn escalates now.
            if (planned.planned().subsidyAsked()) {
                try {
                    tools.requireSubsidyRules(context.asOf());
                } catch (NoValidRuleParameterException e) {
                    return turn.withTools(new TurnState.Tools(null, null, null, false, List.of(), true))
                            .ran(TurnStage.TOOL_PLAN);
                }
            }
            // A what-if's subsidy eligibility is account data, read for the card like the 3.7 portfolio.
            SourcedNumber netIncome = planned.planned().subsidyAsked() ? tools.netIncome(context) : null;
            SimulationSpec spec = ToolPlan.spec(planned.planned(), planned.accountPortfolio(), netIncome);
            return turn.withTools(new TurnState.Tools(planned.planned(), planned.accountPortfolio(), spec, true,
                    List.of(), false)).ran(TurnStage.TOOL_PLAN);
        }
        try {
            ToolRegistry.Ran ran = tools.run(context, planned.spec());
            return turn.withTools(TurnState.Tools.calculated(planned.spec(), ran.results(), ran.range()))
                    .ran(TurnStage.TOOL_PLAN);
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
     * the class instruction. The Core results go in as {@code TOOL RESULTS}, the stored profile as profile context
     * (as {@code pipeline.py}); no passages exist yet. The
     * turn's {@link NumberLedger} is built from the same inputs plus the Rule Parameters valid at the as-of date, with
     * every Core number first (label: its {@code provId}).
     */
    TurnState exposureFilter(RequestContext context, PersonaScope scope, ChatMessage question, TurnState turn) {
        AgentConfiguration agent = context.agentConfig();
        List<PromptPayload.HistoryTurn> history =
                chats.releasedTurnsBefore(scope, question.id(), ExposureFilter.HISTORY_TURNS);
        PromptPayload payload = ExposureFilter.apply(level(agent.dataExposure()), turn.userText(),
                turn.nlu().extraction().depersonalizedQuestion(), history, profiles.stored(scope),
                ToolResults.format(turn.tools().results()), turn.passages());
        PromptFiles.Prompt rolePrompt = prompts.rolePrompt(agent.rolePrompt());
        PromptFiles.Prompt rules = prompts.complianceRules();
        PromptFiles.Prompt instruction = turn.tools().needsStatement() != null ? prompts.needsInstruction()
                : prompts.classInstruction(turn.messageClass());
        ChatMessage.Recommendation statement = turn.tools().statement();
        String classInstruction = ClassInstruction.fill(instruction.text(), turn.classification(),
                statement == null ? null : statement.category(),
                statement == null ? "" : String.join("; ", statement.conflicts()));
        PhrasingPrompt phrasing = PhrasingPrompt.build(rolePrompt.text(), rules.text(), classInstruction, payload,
                agent.decoding().numCtx(), agent.decoding().numPredict());
        Map<String, String> hashes = Map.of(rolePrompt.fileName(), rolePrompt.hash(), rules.fileName(), rules.hash(),
                instruction.fileName(), instruction.hash());
        Map<String, BigDecimal> parameters = new LinkedHashMap<>();
        ruleParameters.allValid(context.asOf()).forEach(p -> parameters.put(p.key(), p.value()));
        // Deviation from pipeline.py: the user's numbers are those the model saw (the depersonalised question at
        // none), and the class instruction's numbers (banded premises) are sourced too.
        NumberLedger ledger = NumberLedger.of(turn.tools().numbers(), payload.message(), payload,
                phrasing.droppedHistoryTurns(), passagesInPrompt(payload, phrasing), parameters)
                .with(NumberLedger.Source.EXPOSURE, "numbers in the class instruction",
                        NumberParser.numbersIn(classInstruction));
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
     * the fixed proposal text. An {@code ASK_PROFILE} draft starts with why Fine asks; a recommendation with strict
     * profile fields still unknown ends with them (AD-23). Both sentences are fixed and run through the checks; a blank
     * draft stays blank.
     */
    TurnState llmDraft(RequestContext context, TurnState turn) {
        if (turn.tools().proposal()) {
            return turn.withDraft(new TurnState.Draft(ResponseTemplates.PARAMETER_PROPOSAL, null, null, false))
                    .passedThrough(TurnStage.LLM_DRAFT);
        }
        if (turn.tools().range() != null) {
            return turn.withDraft(new TurnState.Draft(rangeText(turn.tools().results()), null, null, false))
                    .passedThrough(TurnStage.LLM_DRAFT);
        }
        PhrasingPrompt phrasing = turn.prompt().phrasing();
        LlmResult result = llm.generate(LlmRole.PHRASING,
                List.of(LlmMessage.system(phrasing.system()), LlmMessage.user(phrasing.user())),
                context.agentConfig().decoding(), null);
        String text = withFixedSentences(turn, result.text() == null ? "" : result.text());
        return turn.withDraft(new TurnState.Draft(text, result.modelTag(), result.digest(), result.truncated()))
                .ran(TurnStage.LLM_DRAFT);
    }

    /** The fixed answer of a calculated what-if, from the what-if projection's outputs (the last result). */
    static String rangeText(List<CoreResult> results) {
        CoreResult whatIf = results.getLast();
        return ResponseTemplates.range(whatIf.number(WhatIfProjection.OUTPUT_P10).value(),
                whatIf.number(WhatIfProjection.OUTPUT_P90).value(), whatIf.number(WhatIfProjection.OUTPUT_P50).value(),
                whatIf.number(WhatIfProjection.OUTPUT_YEARS).value().intValueExact());
    }

    /** The class's fixed sentences around a model text (first draft and regeneration alike); blank stays blank. */
    static String withFixedSentences(TurnState turn, String text) {
        if (text.isBlank()) {
            return text;
        }
        return switch (turn.messageClass()) {
            case ASK_PROFILE -> ResponseTemplates.withProfileReason(text);
            case RECOMMEND_CATEGORY -> {
                ChatMessage.Recommendation statement = turn.tools().statement();
                String led = turn.tools().needsStatement() != null
                        ? ResponseTemplates.withRecommendationLead(text, ResponseTemplates.NEEDS_LEAD)
                        : statement == null ? text : ResponseTemplates.withRecommendationLead(text,
                        ResponseTemplates.recommendationLead(statement.label(), statement.conflicts()));
                yield ResponseTemplates.withLimitations(led, turn.classification().limitations());
            }
            default -> text;
        };
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
     * runs again on the regenerated text. With retrieval off the regenerated text loses its citation markers like the
     * first draft; a blank regeneration keeps the first draft's results, so the turn is routed instead of failing.
     */
    TurnState ruleEngine(RequestContext context, TurnState turn) {
        TurnState.Checks checks = turn.checks();
        // A what-if's fixed range text (no model call) is never regenerated: block routing decides on it as it is.
        if (checks.flags().shadowMode() || turn.tools().ruleParameterError() || turn.tools().range() != null) {
            return turn.ran(TurnStage.RULE_ENGINE);
        }
        Optional<String> fix = RegenerationFix.of(checks.shadow().failed(), passagesInPrompt(turn),
                turn.prompt().payload().toolResults(), CheckChain.investTopic(turn.nlu().extraction().topic()),
                CheckChain.protectionTopic(turn.nlu().extraction().topic()));
        if (fix.isEmpty()) {
            return turn.ran(TurnStage.RULE_ENGINE);
        }
        PhrasingPrompt phrasing = turn.prompt().phrasing();
        LlmResult result = llm.generate(LlmRole.PHRASING,
                List.of(LlmMessage.system(phrasing.system()), LlmMessage.user(phrasing.user()),
                        LlmMessage.assistant(turn.draft().text()), LlmMessage.user(fix.get())),
                context.agentConfig().decoding(), null);
        boolean retrieval = context.agentConfig().retrieval();
        String raw = result.text() == null ? "" : result.text();
        String text = withFixedSentences(turn, retrieval ? raw : CitationMarkers.strip(raw).text());
        TurnState.Regeneration regeneration = new TurnState.Regeneration(fix.get(), text, result.truncated());
        ChainResult results = text.isBlank() ? checks.shadow() : CheckChain.run(text, facts(turn, retrieval));
        return turn.withChecks(checks.withRegeneration(regeneration, results)).ran(TurnStage.RULE_ENGINE);
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
     * the class and the draft is released, except that a Rule Parameter error escalates at every level: its draft has
     * no figures. A blank text that would be released fails the turn.
     */
    TurnState blockRouting(TurnState turn) {
        TurnState.Checks checks = turn.checks();
        if (!checks.flags().routing() && !turn.tools().ruleParameterError()) {
            ResponseOutcome outcome = ResponseOutcome.of(turn.messageClass());
            return turn.withOutcome(outcome, releasable(checks.draftText()), chips(outcome, turn), false)
                    .passedThrough(TurnStage.BLOCK_ROUTING);
        }
        BlockRouting.Route route = BlockRouting.route(checks.finalResults().failed(), turn.classification(),
                turn.tools().ruleParameterError(), turn.tools().ruleForLaterYear());
        TurnState routed = turn.withChecks(checks.withRoute(route));
        if (route.released()) {
            String text = checks.routedDraft();
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
     * What the Check Chain needs besides the text, with the turn's ledger for the Number Check and the Core's category
     * selection of a recommendation. {@code SOURCE_VALIDITY} applies only with retrieval on, as in the experiment.
     */
    private static CheckChain.Facts facts(TurnState turn, boolean retrievalOn) {
        return CheckChain.Facts.of(turn.classification(), turn.userText(), turn.tools().needs() != null
                ? NeedsStatements.facts(turn.tools().needs())
                : Recommendations.facts(turn.tools().statement(), turn.nlu().extraction().topic()), retrievalOn,
                passagesInPrompt(turn), turn.prompt().ledger());
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
     * state, an {@code ASK_PROFILE} answer with fields to ask the {@code ASSESSMENT} state with its card; a calculated
     * answer replies to its proposal and lists its figures, unless block routing released a template; a resumed
     * answer replies to its card.
     */
    ChatMessage auditAndRelease(RequestContext context, PersonaScope scope, ChatMessage question,
            Continuation continuation, String fixtureVersion, TurnState turn) {
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
