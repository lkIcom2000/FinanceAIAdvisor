package de.financetool.shared.fixtures;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import de.financetool.shared.AppHome;
import de.financetool.shared.domain.PersonaId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Synthetic Persona fixtures below {@code APP_HOME/data/personas} (FR-51), loaded once at startup. Every
 * {@code p-*.json} must validate against {@code schema.json} and carry its file stem as {@code personaId}; otherwise
 * startup fails naming the file and the violation. Whether every persona has a fixture is checked by the seeding use
 * case, which knows the personas.
 *
 * <p>{@link #version} is the fixture's content hash: lowercase hex SHA-256 of the file bytes after CRLF to LF
 * normalisation, so a Windows checkout hashes like the image.
 */
@Component
public final class PersonaFixtureCatalog {

    public static final String DIRECTORY = "personas";
    public static final String SCHEMA_FILE = "schema.json";

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private final Map<String, PersonaFixture> fixtures;
    private final Map<String, String> versions;

    @Autowired
    public PersonaFixtureCatalog(AppHome appHome) {
        this(appHome.data().resolve(DIRECTORY));
    }

    /** Loads and validates every fixture in {@code directory}; throws {@link IllegalStateException} on any error. */
    public PersonaFixtureCatalog(Path directory) {
        Path schemaFile = directory.resolve(SCHEMA_FILE);
        if (!Files.isRegularFile(schemaFile)) {
            throw new IllegalStateException("Persona fixture schema missing: " + schemaFile);
        }
        Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(readString(schemaFile));
        Map<String, PersonaFixture> loaded = new TreeMap<>();
        Map<String, String> hashes = new TreeMap<>();
        for (Path file : fixtureFiles(directory)) {
            String stem = file.getFileName().toString().replaceFirst("\\.json$", "");
            byte[] bytes = normalised(readBytes(file));
            PersonaFixture fixture = parse(file, schema, new String(bytes, StandardCharsets.UTF_8));
            if (!fixture.personaId().equals(stem)) {
                throw new IllegalStateException("Persona fixture " + file + ": personaId " + fixture.personaId()
                        + " differs from the file name " + stem);
            }
            loaded.put(stem, fixture);
            hashes.put(stem, sha256(bytes));
        }
        if (loaded.isEmpty()) {
            throw new IllegalStateException("No persona fixtures (p-*.json) in " + directory);
        }
        this.fixtures = Map.copyOf(loaded);
        this.versions = Map.copyOf(hashes);
    }

    /** The persona ids that have a fixture, sorted. */
    public List<PersonaId> personaIds() {
        return fixtures.keySet().stream().sorted().map(PersonaId::new).toList();
    }

    public PersonaFixture fixture(PersonaId personaId) {
        PersonaFixture fixture = fixtures.get(personaId.value());
        if (fixture == null) {
            throw new NoSuchElementException("No persona fixture for " + personaId);
        }
        return fixture;
    }

    /** The fixture version stored in {@code persona_state.fixture_version} (64 hex characters). */
    public String version(PersonaId personaId) {
        fixture(personaId);
        return versions.get(personaId.value());
    }

    /** Hex SHA-256 of {@code bytes} after CRLF to LF normalisation: the fixture version of a fixture file. */
    public static String contentHash(byte[] bytes) {
        return sha256(normalised(bytes));
    }

    private static PersonaFixture parse(Path file, Schema schema, String json) {
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new IllegalStateException("Persona fixture " + file + " is not valid JSON: " + e.getOriginalMessage(),
                    e);
        }
        List<Error> errors = schema.validate(node);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Persona fixture " + file + " violates " + SCHEMA_FILE + ": "
                    + errors.stream().map(Error::toString).collect(Collectors.joining("; ")));
        }
        try {
            return MAPPER.treeToValue(node, PersonaFixture.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Persona fixture " + file + " cannot be read: " + e.getOriginalMessage(),
                    e);
        }
    }

    private static List<Path> fixtureFiles(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().matches("p-.*\\.json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list persona fixtures in " + directory, e);
        }
    }

    private static byte[] normalised(byte[] raw) {
        byte[] out = new byte[raw.length];
        int n = 0;
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == '\r' && i + 1 < raw.length && raw[i + 1] == '\n') {
                continue;
            }
            out[n++] = raw[i];
        }
        return java.util.Arrays.copyOf(out, n);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] readBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readString(Path file) {
        return new String(normalised(readBytes(file)), StandardCharsets.UTF_8);
    }
}
