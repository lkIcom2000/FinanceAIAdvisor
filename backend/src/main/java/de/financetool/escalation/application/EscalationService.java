package de.financetool.escalation.application;

import de.financetool.escalation.EscalationCases;
import de.financetool.escalation.adapter.EscalationCaseRepository;
import de.financetool.shared.PersonaScope;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class EscalationService implements EscalationCases {

    private final EscalationCaseRepository cases;

    EscalationService(EscalationCaseRepository cases) {
        this.cases = cases;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID openForTurn(PersonaScope scope, TurnSnapshot snapshot) {
        UUID id = UUID.randomUUID();
        cases.insertTurnCase(scope, id, snapshot);
        return id;
    }

    @Override
    public Map<UUID, TurnCase> turnCases(PersonaScope scope) {
        return cases.turnCases(scope);
    }
}
