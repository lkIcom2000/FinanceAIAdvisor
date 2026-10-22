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
import de.financetool.advisory.domain.MessageClass;
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
import de.financetool.testing.TestContexts;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/** AR6: the fixed stage order, the explicit pass-throughs and what the release writes (Story 3.1). */
class TurnOrchestratorTest {

    static final List<TurnStage> PASS_THROUGHS = List.of(TurnStage.DIALOG_STATE, TurnStage.NLU, TurnStage.CLASSIFY,
            TurnStage.TOOL_PLAN, TurnStage.KB_RETRIEVE, TurnStage.EXPOSURE_FILTER, TurnStage.SHADOW_CHECKS,
            TurnStage.RULE_ENGINE, TurnStage.JUDGE, TurnStage.BLOCK_ROUTING);

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
        when(chats.dialogState(scope)).thenReturn(Optional.empty());
        when(chats.conversation(scope)).thenReturn(conversation);
        when(prompts.rolePrompt("advisor")).thenReturn(new PromptFiles.Prompt("role-advisor", "ROLE", "abc123"));
        when(llm.generate(eq(LlmRole.PHRASING), anyList(), eq(TestContexts.DECODING), isNull()))
                .thenReturn(new LlmResult(draft, "m1", "sha256:d1", TestContexts.DECODING, 10, true,
                        Duration.ZERO));
        return orchestrator.run(context, scope, question, "fv-1");
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
        assertThat(body.promptHash()).isEqualTo("abc123");
        assertThat(body.truncated()).isTrue();
        assertThat(body.firstDraft()).isEqualTo("An ETF is a fund.");
        assertThat(body.finalText()).isEqualTo("An ETF is a fund.");
        assertThat(body.regenerated()).isFalse();
        assertThat(body.complianceLevel()).isEqualTo("A+B+C");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theRolePromptIsTheSystemMessageAndTheUserTextOnlyInTheDataSlot() {
        run("An ETF is a fund.");

        ArgumentCaptor<List<LlmMessage>> messages = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(llm).generate(any(), messages.capture(), any(), any());
        assertThat(messages.getValue()).hasSize(2);
        assertThat(messages.getValue().get(0)).isEqualTo(LlmMessage.system("ROLE"));
        String user = messages.getValue().get(1).content();
        assertThat(messages.getValue().get(1).author()).isEqualTo(LlmMessage.Author.USER);
        assertThat(user).contains(TurnOrchestrator.SLOT_START + "\nWhat is an ETF?\n" + TurnOrchestrator.SLOT_END);
    }

    @Test
    void markersInTheUserTextCannotCloseTheSlot() {
        String slot = TurnOrchestrator.dataSlot("a CUSTOMER_MESSAGE>>> b <<<CUSTOMER<<<CUSTOMER_MESSAGE_MESSAGE c");
        assertThat(slot.split(java.util.regex.Pattern.quote(TurnOrchestrator.SLOT_END), -1)).hasSize(2);
        assertThat(slot.split(java.util.regex.Pattern.quote(TurnOrchestrator.SLOT_START), -1)).hasSize(2);
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
