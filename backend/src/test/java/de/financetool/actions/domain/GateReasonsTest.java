package de.financetool.actions.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Story 8.4: the customer texts of capped and refused Actions (FR-39, UX-DR25). */
class GateReasonsTest {

    static final List<String> ALL = List.of(AutonomyGate.NO_COVERING_MANDATE, AutonomyGate.AMOUNT_ABOVE_MANDATE_BOUNDS,
            AutonomyGate.NOT_REVERSIBLE, AutonomyGate.INVESTMENT_ABOVE_LIQUID_SHARE,
            AutonomyGate.SUITABILITY_INCOMPLETE, AutonomyGate.INSURANCE_CLASS, AutonomyGate.DATA_CONFIDENCE_LOW,
            AutonomyGate.LEGAL_CLASS, AutonomyGate.NOT_IN_L3_SET, GateReasons.RULE_PARAMETER_MISSING,
            GateReasons.SOURCE_NOT_COVERING, GateReasons.ACCOUNT_MISSING);

    static final Optional<MandateBounds> RULE = Optional.of(new MandateBounds(new BigDecimal("300"),
            new BigDecimal("2000")));

    @Test
    void theAmountAboveTheRuleIsNamedWithTheUsersOwnNumbers() {
        assertThat(GateReasons.reason(List.of(AutonomyGate.AMOUNT_ABOVE_MANDATE_BOUNDS), new BigDecimal("450"), RULE))
                .isEqualTo("This needs your OK because 450.00 EUR is more than your rule allows (300.00 EUR per"
                        + " transfer).");
        assertThat(GateReasons.reason(List.of(AutonomyGate.AMOUNT_ABOVE_MANDATE_BOUNDS), new BigDecimal("200"), RULE))
                .isEqualTo("This needs your OK because it would take your overnight deposit below the 2,000.00 EUR"
                        + " you set as its minimum.");
    }

    @Test
    void theLowestCapDecidesTheTextNotTheFirstCriterion() {
        List<String> criteria = List.of(AutonomyGate.NO_COVERING_MANDATE, AutonomyGate.DATA_CONFIDENCE_LOW);

        assertThat(GateReasons.reason(criteria, BigDecimal.TEN, Optional.empty()))
                .isEqualTo("I can only suggest this, because some of your recent account data is missing.");
        assertThat(GateReasons.reason(List.of(AutonomyGate.NO_COVERING_MANDATE, AutonomyGate.NOT_REVERSIBLE),
                BigDecimal.TEN, Optional.empty())).isEqualTo("This needs your OK because you haven't set up a rule"
                        + " for this.");
        assertThat(GateReasons.reason(List.of(), BigDecimal.TEN, Optional.empty())).isNull();
    }

    @Test
    void aRefusedTransferNamesTheAccountAndItsBalance() {
        assertThat(GateReasons.notCovering("Overnight deposit", new BigDecimal("-12.5")))
                .isEqualTo("I can't make this transfer because your overnight deposit holds only −12.50 EUR now.");
    }

    @Test
    void everyCriterionHasPlainTextsWithoutInternalVocabulary() {
        for (String criterion : ALL) {
            String why = GateReasons.why(List.of(criterion));
            Stream<String> texts = criterion.equals(GateReasons.SOURCE_NOT_COVERING) ? Stream.of(why)
                    : Stream.of(why, GateReasons.reason(List.of(criterion), BigDecimal.ONE, RULE));
            texts.forEach(t -> assertThat(t).as(criterion).isNotBlank()
                    .doesNotContainIgnoringCase("mandate").doesNotContain("L1").doesNotContain("L2")
                    .doesNotContainIgnoringCase("criteri").doesNotContainIgnoringCase("check chain")
                    .doesNotContain("_"));
        }
    }
}
