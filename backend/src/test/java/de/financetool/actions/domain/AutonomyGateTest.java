package de.financetool.actions.domain;

import static de.financetool.actions.domain.AutonomyGate.AMOUNT_ABOVE_MANDATE_BOUNDS;
import static de.financetool.actions.domain.AutonomyGate.DATA_CONFIDENCE_LOW;
import static de.financetool.actions.domain.AutonomyGate.INSURANCE_CLASS;
import static de.financetool.actions.domain.AutonomyGate.INVESTMENT_ABOVE_LIQUID_SHARE;
import static de.financetool.actions.domain.AutonomyGate.LEGAL_CLASS;
import static de.financetool.actions.domain.AutonomyGate.NOT_REVERSIBLE;
import static de.financetool.actions.domain.AutonomyGate.NO_COVERING_MANDATE;
import static de.financetool.actions.domain.AutonomyGate.SUITABILITY_INCOMPLETE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * FR-35 table of the Autonomy Gate. The {@code investment} and irreversibility rows are covered only here: no such
 * Action exists in the MVP.
 */
class AutonomyGateTest {

    static final GateThresholds THRESHOLDS = new GateThresholds(new BigDecimal("0.05"));
    static final BigDecimal LIQUID = new BigDecimal("10000.00");
    static final MandateBounds BOUNDS = new MandateBounds(new BigDecimal("500.00"), new BigDecimal("3000.00"));

    static GateAction action(RegulatoryClass regulatoryClass, String amount, boolean reversible) {
        return new GateAction(ActionType.SHORTFALL_TRANSFER, regulatoryClass,
                amount == null ? null : new BigDecimal(amount), reversible);
    }

    static GateContext context(boolean confidenceHigh, boolean suitabilityComplete, MandateBounds mandate,
            String fundAfter) {
        return new GateContext(LIQUID, confidenceHigh, suitabilityComplete, Optional.ofNullable(mandate),
                new BigDecimal(fundAfter));
    }

    /** Everything met but the row under test. */
    static GateContext covered() {
        return context(true, true, BOUNDS, "5000.00");
    }

    static Stream<Arguments> table() {
        return Stream.of(
                Arguments.of("all criteria met, L3 set", action(RegulatoryClass.PAYMENT, "200.00", true), covered(),
                        AutonomyLevel.L3, List.of()),
                Arguments.of("no covering mandate", action(RegulatoryClass.PAYMENT, "200.00", true),
                        context(true, true, null, "5000.00"), AutonomyLevel.L2, List.of(NO_COVERING_MANDATE)),
                Arguments.of("amount above mandate max", action(RegulatoryClass.PAYMENT, "500.01", true), covered(),
                        AutonomyLevel.L2, List.of(AMOUNT_ABOVE_MANDATE_BOUNDS)),
                Arguments.of("amount equal to mandate max", action(RegulatoryClass.PAYMENT, "500.00", true),
                        covered(), AutonomyLevel.L3, List.of()),
                Arguments.of("emergency fund below floor after", action(RegulatoryClass.PAYMENT, "200.00", true),
                        context(true, true, BOUNDS, "2999.99"), AutonomyLevel.L2,
                        List.of(AMOUNT_ABOVE_MANDATE_BOUNDS)),
                Arguments.of("emergency fund at the floor after", action(RegulatoryClass.PAYMENT, "200.00", true),
                        context(true, true, BOUNDS, "3000.00"), AutonomyLevel.L3, List.of()),
                Arguments.of("not reversible", action(RegulatoryClass.PAYMENT, "200.00", false), covered(),
                        AutonomyLevel.L2, List.of(NOT_REVERSIBLE)),
                Arguments.of("investment above share of liquid assets",
                        action(RegulatoryClass.INVESTMENT, "500.01", true),
                        context(true, true, new MandateBounds(new BigDecimal("1000.00"), BigDecimal.ZERO), "5000.00"),
                        AutonomyLevel.L2, List.of(INVESTMENT_ABOVE_LIQUID_SHARE)),
                Arguments.of("investment at the share of liquid assets",
                        action(RegulatoryClass.INVESTMENT, "500.00", true), covered(), AutonomyLevel.L3, List.of()),
                Arguments.of("investment without complete suitability",
                        action(RegulatoryClass.INVESTMENT, "100.00", true), context(true, false, BOUNDS, "5000.00"),
                        AutonomyLevel.L1, List.of(SUITABILITY_INCOMPLETE)),
                Arguments.of("payment without complete suitability", action(RegulatoryClass.PAYMENT, "100.00", true),
                        context(true, false, BOUNDS, "5000.00"), AutonomyLevel.L3, List.of()),
                Arguments.of("insurance", action(RegulatoryClass.INSURANCE, "100.00", true), covered(),
                        AutonomyLevel.L1, List.of(INSURANCE_CLASS)),
                Arguments.of("data confidence low", action(RegulatoryClass.PAYMENT, "100.00", true),
                        context(false, true, BOUNDS, "5000.00"), AutonomyLevel.L1, List.of(DATA_CONFIDENCE_LOW)),
                Arguments.of("legal", action(RegulatoryClass.LEGAL, "100.00", true), covered(), AutonomyLevel.L1,
                        List.of(LEGAL_CLASS)),
                Arguments.of("null amount counts as 0", action(RegulatoryClass.INVESTMENT, null, true), covered(),
                        AutonomyLevel.L3, List.of()),
                // combinations: every failed criterion is listed in table order, the lowest cap wins
                Arguments.of("no mandate and insurance", action(RegulatoryClass.INSURANCE, "100.00", true),
                        context(true, true, null, "5000.00"), AutonomyLevel.L1,
                        List.of(NO_COVERING_MANDATE, INSURANCE_CLASS)),
                Arguments.of("no mandate and legal", action(RegulatoryClass.LEGAL, "100.00", true),
                        context(true, true, null, "5000.00"), AutonomyLevel.L1,
                        List.of(NO_COVERING_MANDATE, LEGAL_CLASS)),
                Arguments.of("irreversible investment above share without suitability, low confidence, no mandate",
                        action(RegulatoryClass.INVESTMENT, "900.00", false), context(false, false, null, "5000.00"),
                        AutonomyLevel.L1, List.of(NO_COVERING_MANDATE, NOT_REVERSIBLE,
                                INVESTMENT_ABOVE_LIQUID_SHARE, SUITABILITY_INCOMPLETE, DATA_CONFIDENCE_LOW)),
                Arguments.of("above bounds and not reversible", action(RegulatoryClass.PAYMENT, "600.00", false),
                        covered(), AutonomyLevel.L2, List.of(AMOUNT_ABOVE_MANDATE_BOUNDS, NOT_REVERSIBLE)),
                Arguments.of("legal with low confidence", action(RegulatoryClass.LEGAL, "0.00", true),
                        context(false, true, BOUNDS, "5000.00"), AutonomyLevel.L1,
                        List.of(DATA_CONFIDENCE_LOW, LEGAL_CLASS)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    void theTableGivesTheLevelAndEveryFailedCriterion(String row, GateAction action, GateContext context,
            AutonomyLevel level, List<String> criteria) {
        GateDecision decision = AutonomyGate.decide(action, context, THRESHOLDS);

        assertThat(decision.level()).isEqualTo(level);
        assertThat(decision.cappingCriteria()).containsExactlyElementsOf(criteria);
    }

    @Test
    void identicalInputsGiveIdenticalDecisions() {
        GateAction action = action(RegulatoryClass.INVESTMENT, "900.00", false);
        GateContext context = context(false, false, null, "5000.00");

        assertThat(AutonomyGate.decide(action, context, THRESHOLDS))
                .isEqualTo(AutonomyGate.decide(action, context, THRESHOLDS));
    }

    @Test
    void theInvestmentShareComesFromTheThresholds() {
        GateAction action = action(RegulatoryClass.INVESTMENT, "800.00", true);

        assertThat(AutonomyGate.decide(action, covered(), THRESHOLDS).cappingCriteria())
                .contains(INVESTMENT_ABOVE_LIQUID_SHARE);
        assertThat(AutonomyGate.decide(action, context(true, true,
                new MandateBounds(new BigDecimal("1000.00"), BigDecimal.ZERO), "5000.00"),
                new GateThresholds(new BigDecimal("0.10"))).level()).isEqualTo(AutonomyLevel.L3);
    }

    @Test
    void eachLevelMapsToItsProposedStateAndL3IsNotAvailableYet() {
        assertThat(ActionState.proposedAt(AutonomyLevel.L0)).isEqualTo(ActionState.INFORMED);
        assertThat(ActionState.proposedAt(AutonomyLevel.L1)).isEqualTo(ActionState.RECOMMENDED);
        assertThat(ActionState.proposedAt(AutonomyLevel.L2)).isEqualTo(ActionState.AWAITING_CONFIRMATION);
        assertThatThrownBy(() -> ActionState.proposedAt(AutonomyLevel.L3)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theMvpActionTypesArePaymentsAndReversibleAndInTheL3Set() {
        for (ActionType type : ActionType.values()) {
            assertThat(type.regulatoryClass()).isEqualTo(RegulatoryClass.PAYMENT);
            assertThat(type.reversible()).isTrue();
            assertThat(type.l3Eligible()).isTrue();
        }
    }
}
