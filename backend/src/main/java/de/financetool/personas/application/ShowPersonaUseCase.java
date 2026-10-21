package de.financetool.personas.application;

import de.financetool.personas.PersonaDirectory;
import de.financetool.personas.PersonaSummary;
import de.financetool.personas.RequestContextFactory;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The persona of the session and its simulated "today". */
@Service
public class ShowPersonaUseCase {

    public record PersonaView(PersonaSummary persona, LocalDate asOf) {
    }

    private final RequestContextFactory contexts;
    private final PersonaDirectory directory;

    ShowPersonaUseCase(RequestContextFactory contexts, PersonaDirectory directory) {
        this.contexts = contexts;
        this.directory = directory;
    }

    @Transactional(readOnly = true)
    public PersonaView show(PersonaScope scope) {
        RequestContext context = contexts.forPersona(scope, null);
        return new PersonaView(directory.find(scope), context.asOf());
    }
}
