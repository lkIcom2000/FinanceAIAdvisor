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
import de.financetool.advisory.domain.ExposureFilter;
import de.financetool.advisory.domain.MessageClass;
import de.financetool.advisory.domain.PromptPayload;
import de.financetool.advisory.domain.ResponseOutcome;
import de.financetool.advisory.domain.TurnStage;
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
 * stage, the phrasing prompt and its budget (Story 3.2).
 */
class TurnOrchestratorTest {

    static final List<TurnStage> PASS_THROUGHS = List.of(TurnStage.DIALOG_STATE, TurnStage.NLU, TurnStage.CLASSIFY,
            TurnStage.TOOL_PLAN, TurnStage.KB_RETRIEVE, TurnStage.SHADOW_CHECKS, TurnStage.RULE_ENGINE,
            TurnStage.JUDGE, TurnStage.BLOCK_ROUTING);

    final ChatRepository chats = mock(ChatRepository.class);
    final PromptFiles prompts = mock(PromptFiles.class);
    final LlmPort llm = mock(LlmPort.class);
    final AuditWriter audit = mock(AuditWriter.class);
    final EvidenceStore evidence = mock(EvidenceStore.class);
    final TurnOrchestrator orchestrator = new TurnOrchestrator(chats, prompts, llm, audit, evidence);

    final PersonaScope scope = new PersonaScope(TestContexts.LENA);
    final RequestContext context = TestContexts.context();
    final ChatMessage question =
            new ChatMessage(UUID.randomUUID(), ChatMessage.Author.USER, "What is an ETF?", null, null);
    final UUID conversation = UUID.randomUUID();

    ChatMessage run(String draft) {
        return run(draft, context, true);
    }

    ChatMessage run(String draft, RequestContext turnContext, boolean modelTruncated) {
        when(chats.dialogState(scope)).thenReturn(Optional.empty());
        when(chats.conversation(scope)).thenReturn(conversation);
        when(prompts.rolePrompt("advisor")).thenReturn(new PromptFiles.Prompt("role-advisor", "ROLE", "abc123"));
        when(prompts.complianceRules()).thenReturn(new PromptFiles.Prompt("compliance-rules", "RULES", "def456"));
        when(llm.generate(eq(LlmRole.PHRASING), anyList(), eq(TestContexts.DECODING), isNull()))
                .thenReturn(new LlmResult(draft, "m1", "sha256:d1", TestContexts.DECODING, 10, modelTruncated,
                        Duration.ZERO));
        return orchestrator.run(turnContext, scope, question, "fv-1");
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
        assertThat(answer.text()).isEqualTo("An ETF is a fund.");
        assertThat(answer.replyTo()).isEqualTo(question.id());
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
        assertThat(body.evidence()).isEmpty();
        assertThat(body.checks()).isEmpty();
        assertThat(body.modelTag()).isEqualTo("m1");
        assertThat(body.modelDigest()).isEqualTo("sha256:d1");
        assertThat(body.promptHashes())
                .isEqualTo(Map.of("compliance-rules.txt", "def456", "role-advisor.txt", "abc123"));
        assertThat(body.truncated()).as("the model's own truncated flag").isTrue();
        assertThat(body.droppedHistoryTurns()).isZero();
        assertThat(body.droppedPassages()).isZero();
        assertThat(body.firstDraft()).isEqualTo("An ETF is a fund.");
        assertThat(body.finalText()).isEqualTo("An ETF is a fund.");
        assertThat(body.regenerated()).isFalse();
        assertThat(body.complianceLevel()).isEqualTo("A+B+C");
    }

    @Test
    void theSystemMessageHoldsTheRolePromptAndTheRulesAndTheUserTextOnlyTheSlot() {
        run("An ETF is a fund.");

        List<LlmMessage> messages = sentMessages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isEqualTo(LlmMessage.system("ROLE\n\nRULES"));
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
        assertThat(messages.get(0).content()).isEqualTo("ROLE\n\nRULES");
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
        assertThat(messages.get(0).content()).isEqualTo("ROLE\n\nRULES");
        assertThat(messages.get(1).content())
                .isEqualTo("<user_message>\n" + ExposureFilter.WITHHELD + "\n</user_message>");
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
