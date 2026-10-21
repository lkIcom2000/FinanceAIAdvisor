package de.financetool.research.application;

import de.financetool.personas.PersonaClock;
import de.financetool.shared.PersonaResettable;
import de.financetool.shared.domain.PersonaId;
import java.util.Comparator;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resets a persona to its fixtures (FR-3, AD-12): every owner of persona state, in reverse dependency order, in one
 * transaction. Audit is never reset.
 */
@Service
@Profile("research")
public class ResetPersonaUseCase {

    /** Owners of persona state: each module before the modules it depends on (checked against the AD-12 graph). */
    public static final List<String> RESET_ORDER = List.of(
            "monitoring", "advisory", "actions", "escalation", "profile", "core", "bank", "documents", "personas");

    private final PersonaClock personas;
    private final List<PersonaResettable> owners;

    ResetPersonaUseCase(PersonaClock personas, List<PersonaResettable> owners) {
        for (PersonaResettable owner : owners) {
            if (!RESET_ORDER.contains(owner.module())) {
                throw new IllegalStateException("PersonaResettable " + owner.getClass().getName() + " of module "
                        + owner.module() + " is not in ResetPersonaUseCase.RESET_ORDER");
            }
        }
        this.personas = personas;
        this.owners = owners.stream().sorted(Comparator.comparingInt(o -> RESET_ORDER.indexOf(o.module()))).toList();
    }

    /** @return {@code false} if the persona does not exist */
    @Transactional
    public boolean reset(PersonaId personaId) {
        if (!personas.exists(personaId)) {
            return false;
        }
        for (PersonaResettable owner : owners) {
            owner.reset(personaId);
        }
        return true;
    }

    List<String> order() {
        return owners.stream().map(PersonaResettable::module).toList();
    }
}
