package de.financetool.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.shared.domain.PersonaId;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** AD-2: identical calls give identical figure keys; every component of the key changes it. */
class FigureKeyTest {

    static final PersonaId LENA = new PersonaId("p-lena");
    static final LocalDate AS_OF = LocalDate.of(2026, 10, 15);
    static final Map<String, Object> INPUTS = Map.of("openingBalance", new BigDecimal("1000.00"),
            "openingDate", LocalDate.of(2025, 10, 1), "bookingsCount", 3);

    static String key(PersonaId persona, String function, Map<String, ?> inputs, LocalDate asOf,
            String fingerprint, String output) {
        return FigureKey.of(persona, function, inputs, asOf, fingerprint, output);
    }

    static String reference() {
        return key(LENA, "accountBalance", INPUTS, AS_OF, "f00d", "balance");
    }

    @Test
    void theKeyIsSixteenLowerCaseHexCharactersOfSha1OverTheNewlineJoinedComponents() throws Exception {
        String text = "p-lena\naccountBalance\n"
                + "{\"bookingsCount\":3,\"openingBalance\":1000.00,\"openingDate\":2025-10-01}\n"
                + "2026-10-15\nf00d\nbalance";
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                .digest(text.getBytes(StandardCharsets.UTF_8))).substring(0, 16);

        assertThat(reference()).matches("[0-9a-f]{16}").isEqualTo(expected);
    }

    @Test
    void theKeyIsStableAndIndependentOfInputOrder() {
        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("openingDate", LocalDate.of(2025, 10, 1));
        reordered.put("bookingsCount", 3);
        reordered.put("openingBalance", new BigDecimal("1000.00"));

        assertThat(reference()).isEqualTo(reference()).isEqualTo(
                key(LENA, "accountBalance", reordered, AS_OF, "f00d", "balance"));
    }

    @Test
    void everyComponentChangesTheKey() {
        Map<String, Object> otherInputs = new LinkedHashMap<>(INPUTS);
        otherInputs.put("bookingsCount", 4);

        assertThat(List.of(
                key(new PersonaId("p-marco"), "accountBalance", INPUTS, AS_OF, "f00d", "balance"),
                key(LENA, "otherFunction", INPUTS, AS_OF, "f00d", "balance"),
                key(LENA, "accountBalance", otherInputs, AS_OF, "f00d", "balance"),
                key(LENA, "accountBalance", INPUTS, AS_OF.plusDays(1), "f00d", "balance"),
                key(LENA, "accountBalance", INPUTS, AS_OF, "beef", "balance"),
                key(LENA, "accountBalance", INPUTS, AS_OF, "f00d", "other")))
                .doesNotContain(reference())
                .doesNotHaveDuplicates();
    }

    @Test
    void canonicalInputsKeepDecimalScaleAndListOrderAndRejectDoubles() {
        assertThat(CanonicalForm.of(Map.of("a", new BigDecimal("1.50"))))
                .isNotEqualTo(CanonicalForm.of(Map.of("a", new BigDecimal("1.5"))));
        assertThat(CanonicalForm.of(Map.of("l", List.of(1, 2))))
                .isNotEqualTo(CanonicalForm.of(Map.of("l", List.of(2, 1))));
        assertThat(CanonicalForm.of(Map.of("s", "a\"b", "n", Map.of("y", 1, "x", "2"))))
                .isEqualTo("{\"n\":{\"x\":\"2\",\"y\":1},\"s\":\"a\\\"b\"}");
        assertThatThrownBy(() -> CanonicalForm.of(Map.of("d", 1.5))).isInstanceOf(IllegalArgumentException.class);
    }
}
