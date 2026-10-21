package de.financetool.fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.bank.AccountType;
import de.financetool.bank.AssetClass;
import de.financetool.bank.Coverage;
import de.financetool.bank.InsuredRole;
import de.financetool.bank.PaymentInterval;
import de.financetool.bank.TransactionCategory;
import de.financetool.shared.AppHome;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.fixtures.PersonaFixture;
import de.financetool.shared.fixtures.PersonaFixture.Transaction;
import de.financetool.shared.fixtures.PersonaFixtureCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The committed fixtures load, validate against {@code schema.json} and meet the persona data rules (FR-51). */
class PersonaFixtureCatalogTest {

    static final List<String> V2_PERSONAS = List.of("p-harness", "p-jonas", "p-lena", "p-marco", "p-petra",
            "p-shortfall", "p-tim");

    final PersonaFixtureCatalog catalog = new PersonaFixtureCatalog(new AppHome(".."));

    @TempDir
    Path temp;

    @Test
    void everyPersonaOfV2HasExactlyOneValidFixture() {
        assertThat(catalog.personaIds()).extracting(PersonaId::value).containsExactlyElementsOf(V2_PERSONAS);
        for (PersonaId id : catalog.personaIds()) {
            assertThat(catalog.fixture(id).personaId()).isEqualTo(id.value());
            assertThat(catalog.fixture(id).schemaVersion()).isEqualTo(1);
        }
    }

    @Test
    void theVersionIsTheSha256OfTheFileBytes() throws Exception {
        for (PersonaId id : catalog.personaIds()) {
            byte[] bytes = Files.readAllBytes(PersonaFixtureGenerator.DIRECTORY.resolve(id.value() + ".json"));
            String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            assertThat(catalog.version(id)).isEqualTo(expected).hasSize(64).matches("[0-9a-f]{64}");
        }
    }

    @Test
    void crlfLineEndingsDoNotChangeTheVersion() throws IOException {
        Path dir = copyFixtures();
        Path lena = dir.resolve("p-lena.json");
        Files.writeString(lena, Files.readString(lena).replace("\n", "\r\n"));

        assertThat(new PersonaFixtureCatalog(dir).version(new PersonaId("p-lena")))
                .isEqualTo(catalog.version(new PersonaId("p-lena")));
    }

    @Test
    void everyTransactionIsCategorisedAndDataIsComplete() {
        for (PersonaId id : catalog.personaIds()) {
            PersonaFixture fixture = catalog.fixture(id);
            List<Transaction> transactions = fixture.transactions();
            assertThat(transactions).as("%s transactions", id).isNotEmpty()
                    .allSatisfy(t -> assertThat(t.category()).isIn(names(TransactionCategory.values())))
                    .allSatisfy(t -> assertThat(t.bookingDate()).isBeforeOrEqualTo(fixture.simulatedDate()))
                    .allSatisfy(t -> assertThat(t.amount().scale()).isEqualTo(2));
            LocalDate first = transactions.stream().map(Transaction::bookingDate).min(Comparator.naturalOrder())
                    .orElseThrow();
            assertThat(ChronoUnit.MONTHS.between(first, fixture.simulatedDate())).as("%s months of history", id)
                    .isBetween(12L, 24L);
            UUID current = fixture.accounts().stream().filter(a -> a.type().equals("CURRENT")).findFirst()
                    .orElseThrow().id();
            List<LocalDate> days = transactions.stream().filter(t -> t.accountId().equals(current))
                    .map(Transaction::bookingDate).distinct().sorted().toList();
            for (int i = 1; i < days.size(); i++) {
                assertThat(ChronoUnit.DAYS.between(days.get(i - 1), days.get(i))).as("%s gap after %s", id,
                        days.get(i - 1)).isLessThanOrEqualTo(4);
            }
            assertThat(ChronoUnit.DAYS.between(days.getLast(), fixture.simulatedDate())).isLessThanOrEqualTo(4);
            assertThat(fixture.accounts()).allSatisfy(a -> {
                assertThat(a.iban()).startsWith("DE00");
                assertThat(a.openingBalance().signum()).isNotNegative();
            });
            assertThat(transactions).extracting(Transaction::id).doesNotHaveDuplicates();
        }
    }

    @Test
    void theBankEnumsEqualTheSchemaEnums() throws IOException {
        JsonNode schema = JsonMapper.builder().build()
                .readTree(Files.readString(PersonaFixtureGenerator.DIRECTORY.resolve("schema.json")));
        JsonNode defs = schema.get("$defs");
        JsonNode props = schema.get("properties");
        assertThat(enumValues(defs.get("category"))).containsExactlyElementsOf(names(TransactionCategory.values()));
        assertThat(enumValues(defs.get("assetClass"))).containsExactlyElementsOf(names(AssetClass.values()));
        JsonNode account = props.get("accounts").get("items").get("properties");
        assertThat(enumValues(account.get("type"))).containsExactlyElementsOf(names(AccountType.values()));
        JsonNode contract = props.get("contracts").get("items").get("properties");
        assertThat(enumValues(contract.get("coverage"))).containsExactlyElementsOf(names(Coverage.values()));
        assertThat(enumValues(contract.get("insuredRole"))).containsExactlyElementsOf(names(InsuredRole.values()));
        assertThat(enumValues(contract.get("paymentInterval")))
                .containsExactlyElementsOf(names(PaymentInterval.values()));
    }

    @Test
    void aSchemaViolationStopsLoadingNamingTheFile() throws IOException {
        Path dir = copyFixtures();
        replaceOnce(dir.resolve("p-marco.json"), "\"category\": \"RENT\", ", "");

        assertThatThrownBy(() -> new PersonaFixtureCatalog(dir)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("p-marco.json").hasMessageContaining("category");
    }

    @Test
    void anUnknownPropertyStopsLoading() throws IOException {
        Path dir = copyFixtures();
        replaceOnce(dir.resolve("p-tim.json"), "\"schemaVersion\": 1,", "\"schemaVersion\": 1, \"bonus\": \"1.00\",");

        assertThatThrownBy(() -> new PersonaFixtureCatalog(dir)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("p-tim.json").hasMessageContaining("bonus");
    }

    @Test
    void anUnknownCategoryStopsLoading() throws IOException {
        Path dir = copyFixtures();
        replaceOnce(dir.resolve("p-tim.json"), "\"category\": \"RENT\"", "\"category\": \"MISC\"");

        assertThatThrownBy(() -> new PersonaFixtureCatalog(dir)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("p-tim.json");
    }

    @Test
    void aFileNameThatDiffersFromThePersonaIdStopsLoading() throws IOException {
        Path dir = copyFixtures();
        Files.copy(dir.resolve("p-lena.json"), dir.resolve("p-lena2.json"));

        assertThatThrownBy(() -> new PersonaFixtureCatalog(dir)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("p-lena2.json").hasMessageContaining("p-lena");
    }

    @Test
    void invalidJsonStopsLoading() throws IOException {
        Path dir = copyFixtures();
        Files.writeString(dir.resolve("p-tim.json"), "{ not json");

        assertThatThrownBy(() -> new PersonaFixtureCatalog(dir)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("p-tim.json");
    }

    private Path copyFixtures() throws IOException {
        Path dir = Files.createDirectories(temp.resolve("personas"));
        try (var files = Files.list(PersonaFixtureGenerator.DIRECTORY)) {
            for (Path file : files.toList()) {
                Files.copy(file, dir.resolve(file.getFileName()));
            }
        }
        return dir;
    }

    private static void replaceOnce(Path file, String from, String to) throws IOException {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(json).contains(from);
        Files.writeString(file, json.replaceFirst(java.util.regex.Pattern.quote(from),
                java.util.regex.Matcher.quoteReplacement(to)), StandardCharsets.UTF_8);
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private static List<String> enumValues(JsonNode node) {
        return StreamSupport.stream(node.get("enum").spliterator(), false).map(JsonNode::asString).toList();
    }
}
