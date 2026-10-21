package de.financetool.research.application;

import de.financetool.personas.PersonaClock;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.fixtures.PersonaFixtureCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds persona fixtures (FR-51) in every profile: each persona whose stored fixture version differs from its fixture's
 * content hash is loaded through {@link PersonaFixtureLoader}, the same path as persona reset. A fresh install (V3
 * placeholder {@code pre-fixtures}) seeds every persona; an unchanged restart seeds none, so researcher-set dates and
 * bookings survive; a changed fixture re-seeds only that persona.
 */
@Service
public class SeedPersonaFixturesUseCase {

    private static final Logger log = LoggerFactory.getLogger(SeedPersonaFixturesUseCase.class);

    private final PersonaClock personas;
    private final PersonaFixtureCatalog fixtures;
    private final PersonaFixtureLoader loader;

    SeedPersonaFixturesUseCase(PersonaClock personas, PersonaFixtureCatalog fixtures, PersonaFixtureLoader loader) {
        this.personas = personas;
        this.fixtures = fixtures;
        this.loader = loader;
    }

    /**
     * @return the personas that were (re-)seeded, sorted
     * @throws IllegalStateException if a persona has no fixture or a fixture has no persona
     */
    @Transactional
    public List<PersonaId> seedChanged() {
        Map<PersonaId, String> stored = personas.fixtureVersions();
        requireOneFixturePerPersona(stored.keySet());
        List<PersonaId> seeded = new ArrayList<>();
        for (Map.Entry<PersonaId, String> entry : stored.entrySet()) {
            PersonaId personaId = entry.getKey();
            String version = fixtures.version(personaId);
            if (version.equals(entry.getValue())) {
                continue;
            }
            loader.load(personaId);
            seeded.add(personaId);
            log.atInfo().setMessage("Persona fixture seeded").addKeyValue("personaId", personaId.value())
                    .addKeyValue("oldVersion", entry.getValue()).addKeyValue("newVersion", version).log();
        }
        return seeded;
    }

    private void requireOneFixturePerPersona(Set<PersonaId> stored) {
        Set<String> personaIds = new TreeSet<>(stored.stream().map(PersonaId::value).toList());
        Set<String> fixtureIds = new TreeSet<>(fixtures.personaIds().stream().map(PersonaId::value).toList());
        Set<String> withoutFixture = new TreeSet<>(personaIds);
        withoutFixture.removeAll(fixtureIds);
        Set<String> withoutPersona = new TreeSet<>(fixtureIds);
        withoutPersona.removeAll(personaIds);
        if (!withoutFixture.isEmpty() || !withoutPersona.isEmpty()) {
            throw new IllegalStateException("Persona fixtures in data/personas do not match the personas: "
                    + "missing fixture for " + withoutFixture + ", fixture without persona " + withoutPersona);
        }
    }
}
