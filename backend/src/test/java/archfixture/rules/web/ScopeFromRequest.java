package archfixture.rules.web;

import de.financetool.personas.adapter.PersonaEntity;
import de.financetool.personas.adapter.PersonaRepository;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;
import java.util.Optional;
import org.springframework.web.bind.annotation.RequestBody;

/** Deliberately broken: takes the persona from client-supplied data in every way AD-11 forbids. */
public class ScopeFromRequest {

    PersonaScope bound;

    public String fromBody(@RequestBody PersonaScope scope) {
        return scope.id();
    }

    public Optional<PersonaEntity> rawRead(PersonaRepository personas, String personaIdFromPath) {
        return personas.findById(personaIdFromPath);
    }

    public PersonaScope fromPath(String personaIdFromPath) {
        return new PersonaScope(new PersonaId(personaIdFromPath));
    }
}
