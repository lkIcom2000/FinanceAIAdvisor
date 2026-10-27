package de.financetool.actions.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Story 8.4: the customer texts of capped and refused Actions (FR-39, UX-DR25). */
class GateReasonsTest {

    static final List<String> ALL = List.of(AutonomyGate.NO_COVERING_MANDATE, AutonomyGate.AMOUNT_ABOVE_MANDATE_BOUNDS,
            AutonomyGate.NOT_REVERSIBLE, AutonomyGate.INVESTMENT_ABOVE_LIQUID_SHARE,
            AutonomyGate.SUITABILITY_INCOMPLETE, AutonomyGate.INSURANCE_CLASS, AutonomyGate.DATA_CONFIDENCE_LOW,
            AutonomyGate.LEGAL_CLASS, AutonomyGate.NOT_IN_L3_SET, GateReasons.RULE_PARAMETER_MISSING,
            GateReasons.SOURCE_NOT_COVERING, GateReasons.ACCOUNT_MISSING);

    static final Optional<MandateBounds> RULE = Optional.of(new MandateBounds(UUID.randomUUID(),
            RuleTemplate.SAFETY_CUSHION, new BigDecimal("300"), new BigDecimal("600"), new BigDecimal("2000"),
            BigDecimal.ZERO));

    @Test
    void theMonthAndTheCurrentAccountFloorAreNamedToo() {
        Optional<MandateBounds> used = Optional.of(new MandateBounds(UUID.randomUUID(), RuleTemplate.SAFETY_CUSHION,
                new BigDecimal("300"), new BigDecimal("600"), new BigDecimal("2000"), new BigDecimal("550")));
        Optional<MandateBounds> topUp = Optional.of(new MandateBounds(UUID.randomUUID(),
                RuleTemplate.EMERGENCY_FUND_TOP_UP, new BigDecimal("300"), new BigDecimal("600"),
                new BigDecimal("500"), BigDecimal.ZERO));

        assertThat(GateReasons.reason(List.of(AutonomyGate.AMOUNT_ABOVE_MANDATE_BOUNDS), new BigDecimal("90"), used))
                .isEqualTo("This needs your OK because 90.00 EUR is more than your rule allows this month (600.00"
                        + " EUR per month, 550.00 EUR already moved).");
        assertThat(GateReasons.reason(List.of(AutonomyGate.AMOUNT_ABOVE_MANDATE_BOUNDS), new BigDecimal("90"), topUp))
                .isEqualTo("This needs your OK because it would take your current account below the 500.00 EUR"
                        + " you set as its minimum.");
    }

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
