package de.financetool.advisory.application;

import de.financetool.advisory.adapter.ChatRepository;
import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.TurnStage;
import de.financetool.advisory.domain.TurnState;
import de.financetool.shared.PersonaScope;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Stage 1 of a turn (AD-24): the persona's dialog state is consulted first. An open parameter proposal or
 * assessment card ends here: Calculate marks its proposal calculated, Save its card saved, a new question cancels
 * whichever is open; in each case the dialog state returns to {@code NONE}. Without an open card the state passes on.
 */
@Component
class DialogStates {

    private final ChatRepository chats;

    DialogStates(ChatRepository chats) {
        this.chats = chats;
    }

    /**
     * @param continuation Calculate's confirmed proposal or Save's card; {@code null} for a new question
     * @throws CalculateUseCase.ProposalClosedException if Calculate's proposal is no longer the open one
     * @throws SaveAssessmentUseCase.AssessmentClosedException if Save's card is no longer the open one
     */
    TurnState consult(PersonaScope scope, TurnState turn, TurnOrchestrator.Continuation continuation) {
        TurnState consulted = turn.withDialogState(chats.dialogState(scope).orElse(TurnState.NO_DIALOG_STATE));
        Optional<UUID> proposal = chats.openProposal(scope);
        Optional<UUID> assessment = chats.openAssessment(scope);
        switch (continuation) {
            case TurnOrchestrator.Confirmation confirmation -> {
                UUID id = confirmation.proposal().id();
                if (!proposal.equals(Optional.of(id)) || !chats.updateProposalStatus(scope, id,
                        ChatMessage.Proposal.Status.OPEN, ChatMessage.Proposal.Status.CALCULATED)) {
                    throw new CalculateUseCase.ProposalClosedException();
                }
            }
            case TurnOrchestrator.SavedAssessment saved -> {
                UUID id = saved.card().id();
                if (!assessment.equals(Optional.of(id)) || !chats.updateAssessmentStatus(scope, id,
                        ChatMessage.Assessment.Status.OPEN, ChatMessage.Assessment.Status.SAVED)) {
                    throw new SaveAssessmentUseCase.AssessmentClosedException();
                }
            }
            case null -> {
                if (proposal.isEmpty() && assessment.isEmpty()) {
                    return consulted.passedThrough(TurnStage.DIALOG_STATE);
                }
                proposal.ifPresent(id -> chats.updateProposalStatus(scope, id, ChatMessage.Proposal.Status.OPEN,
                        ChatMessage.Proposal.Status.CANCELLED));
                assessment.ifPresent(id -> chats.updateAssessmentStatus(scope, id,
                        ChatMessage.Assessment.Status.OPEN, ChatMessage.Assessment.Status.CANCELLED));
            }
        }
        chats.saveDialogState(scope, chats.conversation(scope), null);
        return consulted.ran(TurnStage.DIALOG_STATE);
    }
}
