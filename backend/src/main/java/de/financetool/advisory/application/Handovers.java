package de.financetool.advisory.application;

import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.HandoverReason;
import de.financetool.escalation.EscalationCases;
import de.financetool.shared.PersonaScope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Attaches to each {@code ESCALATE} answer what the card shows of its case: the plain reason class and status. */
@Component
class Handovers {

    private final EscalationCases cases;

    Handovers(EscalationCases cases) {
        this.cases = cases;
    }

    List<ChatMessage> attach(PersonaScope scope, List<ChatMessage> messages) {
        Map<UUID, EscalationCases.TurnCase> byTurn = cases.turnCases(scope);
        return messages.stream().map(message -> attach(message, byTurn.get(message.id()))).toList();
    }

    ChatMessage attach(PersonaScope scope, ChatMessage message) {
        return attach(message, cases.turnCases(scope).get(message.id()));
    }

    static ChatMessage attach(ChatMessage message, EscalationCases.TurnCase turnCase) {
        if (turnCase == null) {
            return message;
        }
        return message.withHandover(new ChatMessage.Handover(HandoverReason.textOf(turnCase.reasonClass()),
                ChatMessage.Handover.Status.valueOf(turnCase.status().name())));
    }
}
