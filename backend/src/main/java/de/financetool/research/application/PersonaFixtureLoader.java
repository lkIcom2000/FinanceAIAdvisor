package de.financetool.research.application;

import de.financetool.shared.PersonaResettable;
import de.financetool.shared.domain.PersonaId;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a persona's fixture into every module that owns persona state: each registered {@link PersonaResettable}
 * resets the persona, in {@link ResetPersonaUseCase#RESET_ORDER}, inside the caller's transaction. The one path
 * shared by startup seeding and persona reset, so a fresh install and a reset give the same state.
 */
@Component
public class PersonaFixtureLoader {

    private final List<PersonaResettable> owners;

    public PersonaFixtureLoader(List<PersonaResettable> owners) {
        ResetPersonaUseCase.requireRegistered(owners);
        this.owners = owners.stream()
                .sorted(Comparator.comparingInt(o -> ResetPersonaUseCase.RESET_ORDER.indexOf(o.module())))
                .toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void load(PersonaId personaId) {
        for (PersonaResettable owner : owners) {
            owner.reset(personaId);
        }
    }

    List<String> order() {
        return owners.stream().map(PersonaResettable::module).toList();
    }
}
