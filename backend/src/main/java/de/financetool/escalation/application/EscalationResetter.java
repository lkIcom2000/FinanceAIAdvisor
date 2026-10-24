package de.financetool.escalation.application;

import de.financetool.escalation.adapter.EscalationCaseRepository;
import de.financetool.shared.PersonaResettable;
import de.financetool.shared.domain.PersonaId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persona reset of the escalation module: the fixtures hold no cases, so the persona's cases are deleted. */
@Service
class EscalationResetter implements PersonaResettable {

    private final EscalationCaseRepository cases;

    EscalationResetter(EscalationCaseRepository cases) {
        this.cases = cases;
    }

    @Override
    public String module() {
        return "escalation";
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reset(PersonaId personaId) {
        cases.deletePersonaRows(personaId);
    }
}
