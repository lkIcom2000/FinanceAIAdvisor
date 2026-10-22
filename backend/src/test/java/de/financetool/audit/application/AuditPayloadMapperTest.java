package de.financetool.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import de.financetool.audit.Provenance;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.Unit;
import de.financetool.testing.TestContexts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The payload format is fixed (AD-13): sorted keys, ISO dates, nulls kept, independent of field order. */
class AuditPayloadMapperTest {

    static final RequestContext CONTEXT = TestContexts.context();

    record Body(String zeta, BigDecimal amount, String alpha) {
    }

    final AuditPayloadMapper mapper = new AuditPayloadMapper();

    @Test
    void thePayloadHasAFixedSortedFormat() {
        assertThat(mapper.payload(CONTEXT, new Body("z", new BigDecimal("12.50"), null))).isEqualTo(
                "{\"asOf\":\"2026-10-20\","
                        + "\"body\":{\"alpha\":null,\"amount\":12.50,\"zeta\":\"z\"},"
                        + "\"config\":{\"files\":{\"config/a.yaml\":\"aa\",\"config/b.yaml\":\"bb\"},"
                        + "\"fingerprint\":\"f00d\",\"id\":\"reference\"},"
                        + "\"kbSnapshot\":\"none\","
                        + "\"seeds\":{\"core\":20261020,\"llm\":42}}");
    }

    @Test
    void storedJsonIsReSerialisedInTheSameFormat() {
        String json = mapper.payload(CONTEXT, Map.of("b", 1, "a", new BigDecimal("12.50")));
        assertThat(mapper.toJson(mapper.fromJson(json))).isEqualTo(json);
    }

    @Test
    void aProvenanceReadsBackEqualWithItsScale() {
        Provenance provenance = new Provenance("0123456789abcdef", "accountBalance",
                new Provenance.Source(Provenance.SourceKind.CORE_FUNCTION, "Calculated."), "Balance",
                new BigDecimal("1974.00"), Unit.EUR, LocalDate.of(2026, 10, 15),
                List.of(new Provenance.Input("Opening", new BigDecimal("-12.50"), Unit.EUR, null, null),
                        new Provenance.Input("Note", null, null, "text", "fedcba9876543210")),
                List.of("Includes every booking."), List.of(new Provenance.RuleParameter("rp", "v1")),
                List.of(new Provenance.Check("NUMBER_MATCH", true, "Every number matches")));

        Provenance back = mapper.fromJson(mapper.toJson(provenance), Provenance.class);

        assertThat(back).isEqualTo(provenance);
        assertThat(back.value().toPlainString()).isEqualTo("1974.00");
    }
}
