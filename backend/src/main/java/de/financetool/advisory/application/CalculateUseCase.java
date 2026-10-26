package de.financetool.advisory.application;

import de.financetool.advisory.adapter.ChatRepository;
import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.ToolPlan;
import de.financetool.personas.PersonaDirectory;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.RequestContextFactory;
import de.financetool.shared.domain.SimulationSpec;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calculate on a parameter proposal (AD-24): applies the values the persona changed, then runs the turn with the
 * confirmed spec and releases the calculated answer, all in this one transaction. A proposal that was already
 * calculated returns its stored answer without a second turn.
 */
@Service
public class CalculateUseCase {

    /** The proposal was cancelled by a later question; nothing is calculated. */
    public static class ProposalClosedException extends RuntimeException {
        ProposalClosedException() {
            super("The proposal is no longer open");
        }
    }

    /** A changed value is unknown or out of bounds; the problems are plain sentences for the card. */
    public static class InvalidValuesException extends RuntimeException {
        private final List<String> problems;

        InvalidValuesException(List<String> problems) {
            super(String.join(" ", problems));
            this.problems = List.copyOf(problems);
        }

        public List<String> problems() {
            return problems;
        }
    }

    private final RequestContextFactory contexts;
    private final PersonaDirectory personas;
    private final ChatRepository chats;
    private final TurnOrchestrator orchestrator;
    private final Handovers handovers;

    CalculateUseCase(RequestContextFactory contexts, PersonaDirectory personas, ChatRepository chats,
            TurnOrchestrator orchestrator, Handovers handovers) {
        this.contexts = contexts;
        this.personas = personas;
        this.chats = chats;
        this.orchestrator = orchestrator;
        this.handovers = handovers;
    }

    /**
     * @param changed the values the persona changed, by parameter key; empty to calculate as proposed
     * @return empty if the scope's persona has no proposal message with this id
     * @throws ProposalClosedException if the proposal was cancelled
     * @throws InvalidValuesException if a changed value is unknown or out of bounds
     * @throws AnswerMessageUseCase.TurnFailedException if the model, a write or the audit fails
     */
    @Transactional
    public Optional<ChatMessage> calculate(PersonaScope scope, UUID proposalId, Map<String, BigDecimal> changed) {
        Optional<ChatMessage> proposal = chats.find(scope, proposalId).filter(m -> m.proposal() != null);
        if (proposal.isEmpty()) {
            return Optional.empty();
        }
        Optional<ChatMessage> stored = chats.findReply(scope, proposalId);
        if (stored.isPresent()) {
            return Optional.of(handovers.attach(scope, stored.get()));
        }
        if (proposal.get().proposal().status() != ChatMessage.Proposal.Status.OPEN) {
            throw new ProposalClosedException();
        }
        SimulationSpec spec = changedSpec(proposal.get().proposal().spec(), changed);
        RequestContext context = contexts.forPersona(scope, null);
        String fixtureVersion = personas.fixtureVersion(scope);
        try {
            ChatMessage question = chats.find(scope, proposal.get().replyTo()).orElseThrow();
            return Optional.of(orchestrator.calculate(context, scope, question,
                    new TurnOrchestrator.Confirmation(proposal.get(), spec), fixtureVersion));
        } catch (ProposalClosedException e) {
            // A concurrent question or Calculate closed it after the check above.
            throw e;
        } catch (RuntimeException e) {
            throw new AnswerMessageUseCase.TurnFailedException(e);
        }
    }

    /** The spec with the changed values: amounts in cents, rates as written; unknown keys and bounds are refused. */
    static SimulationSpec changedSpec(SimulationSpec spec, Map<String, BigDecimal> changed) {
        List<String> unknown = changed.keySet().stream()
                .filter(key -> spec.parameters().stream().noneMatch(p -> p.key().equals(key)))
                .toList();
        if (!unknown.isEmpty()) {
            throw new InvalidValuesException(List.of("This calculation has no value called " + unknown.getFirst()
                    + "."));
        }
        List<SimulationSpec.Parameter> parameters = new ArrayList<>();
        for (SimulationSpec.Parameter parameter : spec.parameters()) {
            BigDecimal value = changed.get(parameter.key());
            parameters.add(value == null ? parameter : parameter.changedTo(parameter.unit() == Unit.EUR
                    ? value.setScale(2, RoundingMode.HALF_UP) : plain(value)));
        }
        SimulationSpec result = new SimulationSpec(spec.function(), parameters);
        List<String> problems = ToolPlan.problems(result);
        if (!problems.isEmpty()) {
            throw new InvalidValuesException(problems);
        }
        return result;
    }

    private static BigDecimal plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
