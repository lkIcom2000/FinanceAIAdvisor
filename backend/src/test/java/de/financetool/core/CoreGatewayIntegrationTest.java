package de.financetool.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.PostgresIntegrationTest;
import de.financetool.audit.EvidenceOrigin;
import de.financetool.core.domain.CoreResult;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.RequestContextFactory;
import de.financetool.shared.config.ConfigFingerprint;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** AD-2: the gateway keys every figure deterministically and stores its provenance once. */
class CoreGatewayIntegrationTest extends PostgresIntegrationTest {

    static final LocalDate START = LocalDate.of(2026, 10, 15);

    @Autowired
    CoreGateway core;

    @Autowired
    RequestContextFactory contexts;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    JdbcTemplate jdbc;

    RequestContext context(String persona, LocalDate asOf) {
        RequestContext base = contexts.forPersona(new PersonaScope(new PersonaId(persona)), null);
        return new RequestContext(base.agentConfig(), base.configFingerprint(), base.thresholds(),
                base.kbSnapshot(), base.seeds(), asOf, base.personaId(), base.runId());
    }

    SourcedNumber balance(RequestContext context) {
        CoreResult result = transaction.execute(tx -> core.currentAccountBalance(context, EvidenceOrigin.OVERVIEW));
        assertThat(result.numbers()).hasSize(1);
        return result.number("balance");
    }

    @Test
    void theSameCallTwiceGivesTheSameKeyAndOneEvidenceRow() {
        SourcedNumber first = balance(context("p-lena", START));
        SourcedNumber second = balance(context("p-lena", START));

        assertThat(first).isEqualTo(second);
        assertThat(first.provId()).matches("[0-9a-f]{16}");
        assertThat(first.value()).isEqualTo(new java.math.BigDecimal("1974.00"));
        assertThat(first.unit()).isEqualTo(Unit.EUR);
        assertThat(jdbc.queryForList("SELECT persona_id, origin, content->>'function' AS function "
                + "FROM evidence_items WHERE prov_id = ?", first.provId()))
                .containsExactly(Map.of("persona_id", "p-lena", "origin", "overview", "function", "accountBalance"));
    }

    @Test
    void anotherDateOrPersonaGivesAnotherKey() {
        SourcedNumber lena = balance(context("p-lena", START));
        SourcedNumber earlier = balance(context("p-lena", START.minusDays(14)));
        SourcedNumber marco = balance(context("p-marco", START));

        assertThat(earlier.provId()).isNotEqualTo(lena.provId());
        assertThat(earlier.value()).isNotEqualByComparingTo(lena.value());
        assertThat(marco.provId()).isNotIn(lena.provId(), earlier.provId());
    }

    @Test
    void anotherConfigurationFingerprintGivesAnotherKeyAndRow() {
        RequestContext base = context("p-lena", START);
        RequestContext otherConfig = new RequestContext(base.agentConfig(),
                new ConfigFingerprint("other", base.configFingerprint().fileHashes()), base.thresholds(),
                base.kbSnapshot(), base.seeds(), base.asOf(), base.personaId(), base.runId());

        SourcedNumber reference = balance(base);
        SourcedNumber other = balance(otherConfig);

        assertThat(other.provId()).isNotEqualTo(reference.provId());
        assertThat(other.value()).isEqualTo(reference.value());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evidence_items WHERE prov_id IN (?, ?)", Long.class,
                reference.provId(), other.provId())).isEqualTo(2L);
    }

    @Test
    void theGatewayRunsOnlyInsideTheUseCaseTransaction() {
        RequestContext context = context("p-lena", START);
        assertThatThrownBy(() -> core.currentAccountBalance(context, EvidenceOrigin.OVERVIEW))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
