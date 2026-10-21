package de.financetool.research.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.financetool.personas.PersonaClock;
import de.financetool.shared.PersonaResettable;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.fixtures.PersonaFixtureCatalog;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Startup seeding loads only changed personas and stops when personas and fixtures do not match one to one. */
class SeedPersonaFixturesUseCaseTest {

    static final PersonaFixtureCatalog CATALOG = new PersonaFixtureCatalog(Path.of("..", "data", "personas"));

    final List<String> calls = new ArrayList<>();
    final PersonaClock personas = mock(PersonaClock.class);
    final SeedPersonaFixturesUseCase seed = new SeedPersonaFixturesUseCase(personas, CATALOG,
            new PersonaFixtureLoader(List.of(owner("bank"), owner("personas"))));

    PersonaResettable owner(String module) {
        return new PersonaResettable() {
            @Override
            public String module() {
                return module;
            }

            @Override
            public void reset(PersonaId personaId) {
                calls.add(module + ":" + personaId);
            }
        };
    }

    Map<PersonaId, String> storedVersions() {
        Map<PersonaId, String> versions = new LinkedHashMap<>();
        CATALOG.personaIds().forEach(id -> versions.put(id, CATALOG.version(id)));
        return versions;
    }

    @Test
    void onlyPersonasWithAnotherVersionAreSeeded() {
        Map<PersonaId, String> stored = storedVersions();
        stored.put(new PersonaId("p-lena"), "pre-fixtures");
        when(personas.fixtureVersions()).thenReturn(stored);

        assertThat(seed.seedChanged()).containsExactly(new PersonaId("p-lena"));
        assertThat(calls).containsExactly("bank:p-lena", "personas:p-lena");
    }

    @Test
    void matchingVersionsSeedNothing() {
        when(personas.fixtureVersions()).thenReturn(storedVersions());

        assertThat(seed.seedChanged()).isEmpty();
        assertThat(calls).isEmpty();
    }

    @Test
    void aPersonaWithoutFixtureStopsStartup() {
        Map<PersonaId, String> stored = storedVersions();
        stored.put(new PersonaId("p-nofixture"), "pre-fixtures");
        when(personas.fixtureVersions()).thenReturn(stored);

        assertThatThrownBy(seed::seedChanged).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("p-nofixture");
        assertThat(calls).isEmpty();
    }

    @Test
    void aFixtureWithoutPersonaStopsStartup() {
        Map<PersonaId, String> stored = storedVersions();
        stored.remove(new PersonaId("p-tim"));
        when(personas.fixtureVersions()).thenReturn(stored);

        assertThatThrownBy(seed::seedChanged).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("p-tim");
    }
}
