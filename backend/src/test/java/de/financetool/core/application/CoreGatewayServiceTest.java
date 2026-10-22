package de.financetool.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.audit.EvidenceItem;
import de.financetool.audit.EvidenceOrigin;
import de.financetool.audit.Provenance;
import de.financetool.bank.AccountType;
import de.financetool.bank.BankSnapshot;
import de.financetool.knowledge.RuleParamPort;
import de.financetool.testing.TestContexts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The gateway refuses to compute with a Rule Parameter that has no version valid at {@code asOf}. */
class CoreGatewayServiceTest {

    @Test
    void aRuleParameterNotValidAtAsOfIsRefusedAndNothingIsStored() {
        UUID current = UUID.randomUUID();
        BankSnapshot snapshot = new BankSnapshot(TestContexts.LENA, TestContexts.AS_OF,
                List.of(new BankSnapshot.Account(current, AccountType.CURRENT, "Current account", "DE00",
                        LocalDate.of(2025, 7, 1), new BigDecimal("100.00"), new BigDecimal("100.00"))),
                List.of(), List.of(), List.of(), List.of());
        List<EvidenceItem> stored = new ArrayList<>();
        RuleParamPort refusing = (key, asOf) -> {
            throw new IllegalStateException("No version of Rule Parameter " + key + " is valid on " + asOf);
        };
        CoreGatewayService gateway = new CoreGatewayService((persona, asOf) -> snapshot, stored::addAll, refusing);

        assertThatThrownBy(() -> gateway.liquidity(TestContexts.context(), EvidenceOrigin.OVERVIEW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is valid on " + TestContexts.AS_OF);
        assertThat(stored).extracting(i -> ((Provenance) i.content()).function())
                .doesNotContain("cashFlowForecast", "emergencyFundTarget");
    }
}
