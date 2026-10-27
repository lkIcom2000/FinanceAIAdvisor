package de.financetool.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import de.financetool.audit.EvidenceItem;
import de.financetool.audit.EvidenceOrigin;
import de.financetool.audit.Provenance;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.fixtures.PersonaFixture;
import de.financetool.shared.fixtures.PersonaFixtureCatalog;
import de.financetool.testing.FixtureSnapshots;
import de.financetool.testing.TestContexts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Figure-key inputs of a released figure are frozen (Epic 2 retro A6): chat messages, notifications and actions store
 * {@code provId}s, so a changed key input, function name, output key or canonical form silently orphans them. This
 * test pins every key the gateway gives at the fixture date under the fixed test configuration. If it fails on
 * purpose, say so in the story spec (which figures re-key, which stored references are affected and how they are
 * handled), then rewrite the pinned files with {@code -Dfine.figureKeys.update=true}.
 */
class FigureKeyFreezeTest {

    static final PersonaFixtureCatalog CATALOG = new PersonaFixtureCatalog(Path.of("..", "data", "personas"));
    static final Path PINNED = Path.of("src", "test", "resources", "figure-keys");

    @ParameterizedTest
    @ValueSource(strings = { "p-lena", "p-jonas", "p-marco" })
    void releasedFigureKeysDoNotChange(String personaId) throws IOException {
        PersonaFixture fixture = CATALOG.fixture(new PersonaId(personaId));
        List<EvidenceItem> stored = new ArrayList<>();
        CoreGatewayService gateway = new CoreGatewayService(
                (persona, asOf) -> FixtureSnapshots.of(fixture, asOf), stored::addAll,
                CoreGatewayServiceTest.PARAMETERS, Optional::empty);
        RequestContext context = TestContexts.context(new PersonaId(personaId), fixture.simulatedDate());

        gateway.liquidity(context, EvidenceOrigin.OVERVIEW);
        gateway.wealth(context, EvidenceOrigin.OVERVIEW);
        gateway.insurance(context, EvidenceOrigin.OVERVIEW);
        gateway.emergencyFundTopUp(context, EvidenceOrigin.MONITORING);

        List<String> keys = stored.stream().map(i -> (Provenance) i.content())
                .map(p -> p.function() + " | " + p.label() + " | " + p.provId())
                .distinct().sorted().toList();
        Path pinned = PINNED.resolve(personaId + ".txt");
        if (Boolean.getBoolean("fine.figureKeys.update")) {
            Files.createDirectories(PINNED);
            Files.write(pinned, keys);
        }
        assertThat(keys)
                .as("figure keys of %s are frozen; see FigureKeyFreezeTest before updating %s", personaId, pinned)
                .containsExactlyElementsOf(Files.readAllLines(pinned));
    }
}
