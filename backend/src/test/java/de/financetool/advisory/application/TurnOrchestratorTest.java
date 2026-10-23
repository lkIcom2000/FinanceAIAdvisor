package de.financetool.advisory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.financetool.advisory.adapter.ChatRepository;
import de.financetool.advisory.adapter.PromptFiles;
import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.EntityRegistry;
import de.financetool.advisory.domain.ExposureFilter;
import de.financetool.advisory.domain.MessageClass;
import de.financetool.advisory.domain.NluExtraction;
import de.financetool.advisory.domain.NoValidSource;
import de.financetool.advisory.domain.Premise;
import de.financetool.advisory.domain.ProfileCompleteness;
import de.financetool.advisory.domain.PromptPayload;
import de.financetool.advisory.domain.ResponseOutcome;
import de.financetool.advisory.domain.TurnStage;
import de.financetool.advisory.domain.TurnState;
import de.financetool.audit.AuditEntry;
import de.financetool.audit.AuditKind;
import de.financetool.audit.AuditWriter;
import de.financetool.audit.EvidenceStore;
import de.financetool.llm.LlmMessage;
import de.financetool.llm.LlmPort;
import de.financetool.llm.LlmResult;
import de.financetool.llm.LlmRole;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.config.AgentConfiguration;
import de.financetool.shared.config.DataExposureLevel;
import de.financetool.testing.TestContexts;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * AR6: the fixed stage order, the explicit pass-throughs and what the release writes (Story 3.1); the exposure
 * stage, the phrasing prompt and its budget (Story 3.2); NLU, classification, class instruction and the
 * no-valid-source rule (Story 3.3).
 */
class TurnOrchestratorTest {

    static final List<TurnStage> PASS_THROUGHS = List.of(TurnStage.DIALOG_STATE, TurnStage.TOOL_PLAN,
            TurnStage.KB_RETRIEVE, TurnStage.SHADOW_CHECKS, TurnStage.RULE_ENGINE, TurnStage.JUDGE);
    static final Map<String, String> NLU_HASHES = Map.of("nlu-few-shot.json", "f5", "nlu-system.txt", "s5");

    final ChatRepository chats = mock(ChatRepository.class);
    final PromptFiles prompts = mock(PromptFiles.class);
    final LlmPort llm = mock(LlmPort.class);
    final AuditWriter audit = mock(AuditWriter.class);
    final EvidenceStore evidence = mock(EvidenceStore.class);
    final NluExtractor nluExtractor = mock(NluExtractor.class);
    final EntityRegistry registry = new EntityRegistry(Map.of("laws_and_regulations", List.of("MiFID II")), List.of(),
            List.of());
    final TurnOrchestrator orchestrator = new TurnOrchestrator(chats, prompts, llm, audit, evidence, nluExtractor,
            registry, ProfileCompleteness.INCOMPLETE);

    final PersonaScope scope = new PersonaScope(TestContexts.LENA);
    final RequestContext context = TestContexts.context();
    final ChatMessage question =
            new ChatMessage(UUID.randomUUID(), ChatMessage.Author.USER, "What is an ETF?", null, null);
    final UUID conversation = UUID.randomUUID();

    ChatMessage run(String draft) {
        return run(draft, context, true);
    }

    ChatMessage run(String draft, RequestContext turnContext, boolean modelTruncated) {
        return run(draft, turnContext, modelTruncated, NluExtraction.defaults());
    }

    ChatMessage run(String draft, RequestContext turnContext, boolean modelTruncated, NluExtraction nlu) {
        return run(question, draft, turnContext, modelTruncated, nlu);
    }

    ChatMessage run(ChatMessage asked, String draft, RequestContext turnContext, boolean modelTruncated,
            NluExtraction nlu) {
        when(nluExtractor.extract(any(), eq(asked.text()))).thenReturn(new TurnState.Nlu(nlu, List.of("note"),
                "nlu-m", "sha256:n1", new java.util.TreeMap<>(NLU_HASHES), false, true));
        when(chats.dialogState(scope)).thenReturn(Optional.empty());
        when(chats.conversation(scope)).thenReturn(conversation);
        when(prompts.rolePrompt("advisor")).thenReturn(new PromptFiles.Prompt("role-advisor.txt", "ROLE", "abc123"));
        when(prompts.complianceRules()).thenReturn(new PromptFiles.Prompt("compliance-rules.txt", "RULES", "def456"));
        for (MessageClass c : MessageClass.values()) {
            when(prompts.classInstruction(c)).thenReturn(new PromptFiles.Prompt("class-" + c + ".txt",
                    "CLASS " + c + " {oos} {premises}", "h-" + c));
        }
        when(llm.generate(eq(LlmRole.PHRASING), anyList(), eq(TestContexts.DECODING), isNull()))
                .thenReturn(new LlmResult(draft, "m1", "sha256:d1", TestContexts.DECODING, 10, modelTruncated,
                        Duration.ZERO));
        return orchestrator.run(turnContext, scope, asked, "fv-1");
    }

    @SuppressWarnings("unchecked")
    List<LlmMessage> sentMessages() {
        ArgumentCaptor<List<LlmMessage>> messages = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(llm).generate(any(), messages.capture(), any(), any());
        return messages.getValue();
    }

    ResponseAuditBody auditBody() {
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        org.mockito.Mockito.verify(audit).write(any(), entry.capture());
        return (ResponseAuditBody) entry.getValue().body();
    }

    @Test
    void theStagesAreDeclaredInTheFixedPipelineOrder() {
        assertThat(TurnStage.values()).containsExactly(TurnStage.DIALOG_STATE, TurnStage.NLU, TurnStage.CLASSIFY,
                TurnStage.TOOL_PLAN, TurnStage.KB_RETRIEVE, TurnStage.EXPOSURE_FILTER, TurnStage.LLM_DRAFT,
                TurnStage.SHADOW_CHECKS, TurnStage.RULE_ENGINE, TurnStage.JUDGE, TurnStage.BLOCK_ROUTING,
                TurnStage.AUDIT_RELEASE);
    }

    @Test
    void everyStageIsAMethodOfTheOrchestrator() {
        List<String> methods = Arrays.stream(TurnOrchestrator.class.getDeclaredMethods()).map(Method::getName).toList();
        for (TurnStage stage : TurnStage.values()) {
            String name = switch (stage) {
                case AUDIT_RELEASE -> "auditAndRelease";
                case LLM_DRAFT -> "llmDraft";
                default -> camel(stage.name());
            };
            assertThat(methods).as(stage.name()).contains(name);
        }
    }

    @Test
    void dialogStateFirstThenDraftThenReleaseThenAudit() {
        ChatMessage answer = run("  An ETF is a fund.  ");

        InOrder order = inOrder(chats, llm, evidence, audit);
        order.verify(chats).dialogState(scope);
        order.verify(llm).generate(eq(LlmRole.PHRASING), anyList(), any(), isNull());
        order.verify(chats).insert(eq(scope), eq(conversation), eq(answer));
        order.verify(evidence).putIfAbsent(List.of());
        order.verify(audit).write(eq(context), any(AuditEntry.class));

        assertThat(answer.author()).isEqualTo(ChatMessage.Author.AGENT);
        assertThat(answer.outcome()).isEqualTo(ResponseOutcome.INFORM);
        assertThat(answer.text()).isEqualTo(NoValidSource.TEXT);
        assertThat(answer.suggestions()).containsExactly(NoValidSource.DEFAULT_SUGGESTION);
        assertThat(answer.replyTo()).isEqualTo(question.id());
    }

    static NluExtraction nlu(String requestType, String topic, List<String> categories, List<Premise> premises) {
        return new NluExtraction(requestType, "self", topic, categories, premises, false, false, Map.of(), Map.of(),
                "");
    }

    @Test
    void aClassOtherThanInformReleasesTheStrippedDraftAsItsOutcome() {
        ChatMessage answer = run("  Knock-outs are outside the service.  ", context, false,
                nlu("product_selection", "other", List.of("derivative_or_certificate"), List.of()));

        assertThat(answer.outcome()).isEqualTo(ResponseOutcome.REFUSE_OUT_OF_SCOPE);
        assertThat(answer.text()).isEqualTo("Knock-outs are outside the service.");
        assertThat(answer.suggestions()).isEmpty();
        ResponseAuditBody body = auditBody();
        assertThat(body.messageClass()).isEqualTo(MessageClass.REFUSE_OUT_OF_SCOPE);
        assertThat(body.noValidSource()).isFalse();
        assertThat(body.outOfScope()).containsExactly("derivative_or_certificate");
        assertThat(body.passThroughStages()).contains(TurnStage.BLOCK_ROUTING);
        assertThat(sentMessages().get(0).content())
                .isEqualTo("ROLE\n\nRULES\n\nCLASS REFUSE_OUT_OF_SCOPE derivative or certificate");
        assertThat(body.promptHashes()).containsEntry("class-REFUSE_OUT_OF_SCOPE.txt", "h-REFUSE_OUT_OF_SCOPE");
    }

    @Test
    void aTrapRecordsItsUnverifiedPremiseAndTheClaimGoesIntoTheInstruction() {
        ChatMessage trapQuestion = new ChatMessage(UUID.randomUUID(), ChatMessage.Author.USER,
                "Does the Savings Bonus Act 2026 apply to me?", null, null);
        Premise premise = new Premise("The Savings Bonus Act 2026 exists", "law_or_regulation",
                "Savings Bonus Act 2026");

        ChatMessage answer = run(trapQuestion, "It does not exist.", context, false,
                nlu("general_explanation", "regulation_or_concept", List.of(), List.of(premise)));

        assertThat(answer.outcome()).isEqualTo(ResponseOutcome.DETECT_TRAP);
        ResponseAuditBody body = auditBody();
        assertThat(body.unverifiedPremises()).singleElement().satisfies(p -> assertThat(p)
                .containsEntry("named_entity", "Savings Bonus Act 2026").containsEntry("kind", "law_or_regulation"));
        assertThat(body.classReasons()).containsExactly("question presupposes unverifiable premise(s)");
        assertThat(sentMessages().get(0).content()).endsWith("CLASS DETECT_TRAP this product "
                + "The Savings Bonus Act 2026 exists");
    }

    @Test
    void theAuditRecordNamesThePassThroughsAndSharesTheTurnId() {
        ChatMessage answer = run("An ETF is a fund.");

        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        org.mockito.Mockito.verify(audit).write(eq(context), entry.capture());
        assertThat(entry.getValue().kind()).isEqualTo(AuditKind.RESPONSE);
        assertThat(entry.getValue().turnId()).isEqualTo(answer.id());
        assertThat(entry.getValue().fixtureVersion()).isEqualTo("fv-1");
        ResponseAuditBody body = (ResponseAuditBody) entry.getValue().body();
        assertThat(body.passThroughStages()).containsExactlyElementsOf(PASS_THROUGHS);
        assertThat(body.messageClass()).isEqualTo(MessageClass.INFORM);
        assertThat(body.outcome()).isEqualTo(ResponseOutcome.INFORM);
        assertThat(body.classReasons()).containsExactly("default: general information");
        assertThat(body.missingFields()).isEmpty();
        assertThat(body.unverifiedPremises()).isEmpty();
        assertThat(body.outOfScope()).isEmpty();
        assertThat(body.nlu()).isEqualTo(NluExtraction.defaults().toMap());
        assertThat(body.nluGrounding()).containsExactly("note");
        assertThat(body.nluModelTag()).isEqualTo("nlu-m");
        assertThat(body.nluModelDigest()).isEqualTo("sha256:n1");
        assertThat(body.nluPromptHashes()).isEqualTo(NLU_HASHES);
        assertThat(body.nluCacheHit()).isFalse();
        assertThat(body.nluParseOk()).isTrue();
        assertThat(body.instructionOverrideAttempt()).isFalse();
        assertThat(body.noValidSource()).isTrue();
        assertThat(body.evidence()).isEmpty();
        assertThat(body.checks()).isEmpty();
        assertThat(body.modelTag()).isEqualTo("m1");
        assertThat(body.modelDigest()).isEqualTo("sha256:d1");
        assertThat(body.promptHashes()).isEqualTo(Map.of("compliance-rules.txt", "def456", "role-advisor.txt",
                "abc123", "class-INFORM.txt", "h-INFORM"));
        assertThat(body.truncated()).as("the model's own truncated flag").isTrue();
        assertThat(body.droppedHistoryTurns()).isZero();
        assertThat(body.droppedPassages()).isZero();
        assertThat(body.dataExposure()).isEqualTo(DataExposureLevel.AGGREGATED);
        assertThat(body.messageWithheld()).isFalse();
        assertThat(body.firstDraft()).as("the draft is still recorded").isEqualTo("An ETF is a fund.");
        assertThat(body.finalText()).isEqualTo(NoValidSource.TEXT);
        assertThat(body.regenerated()).isFalse();
        assertThat(body.complianceLevel()).isEqualTo("A+B+C");
    }

    @Test
    void theSystemMessageHoldsTheRolePromptTheRulesAndTheClassInstructionAndTheUserTextOnlyTheSlot() {
        run("An ETF is a fund.");

        List<LlmMessage> messages = sentMessages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isEqualTo(LlmMessage.system("ROLE\n\nRULES\n\nCLASS INFORM this product"));
        assertThat(messages.get(1).author()).isEqualTo(LlmMessage.Author.USER);
        assertThat(messages.get(1).content()).isEqualTo("<user_message>\nWhat is an ETF?\n</user_message>");
    }

    @Test
    void theLastReleasedTurnsGoFilteredIntoTheEarlierConversation() {
        when(chats.releasedTurnsBefore(scope, question.id(), 3)).thenReturn(List.of(
                new PromptPayload.HistoryTurn("I'm 34 and have 12,400 EUR.", "Noted."),
                new PromptPayload.HistoryTurn("What is a fund?", "A pool of money.")));
        run("An ETF is a fund.");

        List<LlmMessage> messages = sentMessages();
        assertThat(messages.get(1).content()).isEqualTo("EARLIER CONVERSATION (oldest first):\n"
                + "Customer: I'm 30s and have 10,000\u201315,000 EUR.\nAssistant: Noted.\n"
                + "Customer: What is a fund?\nAssistant: A pool of money.\n\n"
                + "<user_message>\nWhat is an ETF?\n</user_message>");
        assertThat(messages.get(0).content()).startsWith("ROLE\n\nRULES\n\nCLASS INFORM");
    }

    @Test
    void atLevelNoneThePromptHoldsNoHistoryNoProfileAndNoPartOfTheMessage() {
        when(chats.releasedTurnsBefore(scope, question.id(), 3)).thenReturn(List.of(
                new PromptPayload.HistoryTurn("I'm 34 and have 12,400 EUR.", "Noted.")));
        AgentConfiguration a = TestContexts.AGENT;
        AgentConfiguration none = new AgentConfiguration(a.id(), a.derivedFrom(), a.llm(), a.retrieval(),
                a.rolePrompt(), a.compliance(), DataExposureLevel.NONE, a.decoding(), a.seeds());
        RequestContext noneContext = new RequestContext(none, TestContexts.FINGERPRINT, TestContexts.THRESHOLDS,
                RequestContext.NO_KB_SNAPSHOT, TestContexts.SEEDS, TestContexts.AS_OF, TestContexts.LENA, null);
        run("An ETF is a fund.", noneContext, false);

        List<LlmMessage> messages = sentMessages();
        assertThat(messages.get(0).content()).isEqualTo("ROLE\n\nRULES\n\nCLASS INFORM this product");
        assertThat(messages.get(1).content())
                .isEqualTo("<user_message>\n" + ExposureFilter.WITHHELD + "\n</user_message>");
        ResponseAuditBody body = auditBody();
        assertThat(body.dataExposure()).isEqualTo(DataExposureLevel.NONE);
        assertThat(body.messageWithheld()).isTrue();
    }

    @Test
    void atLevelNoneTheNlusDepersonalisedQuestionReplacesTheMessage() {
        AgentConfiguration a = TestContexts.AGENT;
        AgentConfiguration none = new AgentConfiguration(a.id(), a.derivedFrom(), a.llm(), a.retrieval(),
                a.rolePrompt(), a.compliance(), DataExposureLevel.NONE, a.decoding(), a.seeds());
        RequestContext noneContext = new RequestContext(none, TestContexts.FINGERPRINT, TestContexts.THRESHOLDS,
                RequestContext.NO_KB_SNAPSHOT, TestContexts.SEEDS, TestContexts.AS_OF, TestContexts.LENA, null);
        NluExtraction withQuestion = new NluExtraction("general_explanation", "generic", "other", List.of(),
                List.of(), false, false, Map.of(), Map.of(), "What is an exchange-traded fund?");
        run("An ETF is a fund.", noneContext, false, withQuestion);

        assertThat(sentMessages().get(1).content())
                .isEqualTo("<user_message>\nWhat is an exchange-traded fund?\n</user_message>");
        assertThat(auditBody().messageWithheld()).isFalse();
    }

    @Test
    void everyConfiguredExposureLevelMapsToTheSameDomainLevel() {
        for (DataExposureLevel configured : DataExposureLevel.values()) {
            assertThat(TurnOrchestrator.level(configured).name()).isEqualTo(configured.name());
        }
        assertThat(ExposureFilter.Level.values()).hasSameSizeAs(DataExposureLevel.values());
    }

    @Test
    void aPromptOverBudgetDropsHistoryOldestFirstAndMarksTheRecordTruncated() {
        String longText = "x".repeat(6000);
        when(chats.releasedTurnsBefore(scope, question.id(), 3)).thenReturn(List.of(
                new PromptPayload.HistoryTurn(longText, "a1"), new PromptPayload.HistoryTurn(longText, "a2"),
                new PromptPayload.HistoryTurn("short", "a3")));
        run("An ETF is a fund.", context, false);

        assertThat(sentMessages().get(1).content()).contains("Assistant: a2", "Customer: short")
                .doesNotContain("a1")
                .endsWith("<user_message>\nWhat is an ETF?\n</user_message>");
        ResponseAuditBody body = auditBody();
        assertThat(body.truncated()).isTrue();
        assertThat(body.droppedHistoryTurns()).isEqualTo(1);
        assertThat(body.droppedPassages()).isZero();
    }

    @Test
    void anEmptyDraftFailsTheTurnBeforeAnythingIsWritten() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> run("  "))
                .isInstanceOf(IllegalStateException.class);
        org.mockito.Mockito.verify(chats, org.mockito.Mockito.never()).insert(any(), any(), any());
        org.mockito.Mockito.verifyNoInteractions(audit);
    }

    private static String camel(String constant) {
        StringBuilder out = new StringBuilder();
        boolean upper = false;
        for (char c : constant.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                out.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return out.toString();
    }
}
