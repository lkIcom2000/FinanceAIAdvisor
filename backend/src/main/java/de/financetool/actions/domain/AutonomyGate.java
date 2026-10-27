package de.financetool.actions.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The Autonomy Gate (FR-35, AD-10): gives an Action its maximum autonomy level. Each failed criterion caps the level;
 * the lowest cap wins. With no criterion failed, only the L3 set ({@link ActionType#l3Eligible}) reaches L3, every
 * other Action L2. Pure and deterministic: identical inputs give identical decisions.
 */
public final class AutonomyGate {

    public static final String NO_COVERING_MANDATE = "no_covering_mandate";
    public static final String AMOUNT_ABOVE_MANDATE_BOUNDS = "amount_above_mandate_bounds";
    public static final String NOT_REVERSIBLE = "not_reversible";
    public static final String INVESTMENT_ABOVE_LIQUID_SHARE = "investment_above_liquid_share";
    public static final String SUITABILITY_INCOMPLETE = "suitability_incomplete";
    public static final String INSURANCE_CLASS = "insurance_class";
    public static final String DATA_CONFIDENCE_LOW = "data_confidence_low";
    public static final String LEGAL_CLASS = "legal_class";
    public static final String NOT_IN_L3_SET = "not_in_l3_set";

    private static final Map<String, AutonomyLevel> CAPS = Map.of(
            NO_COVERING_MANDATE, AutonomyLevel.L2,
            AMOUNT_ABOVE_MANDATE_BOUNDS, AutonomyLevel.L2,
            NOT_REVERSIBLE, AutonomyLevel.L2,
            INVESTMENT_ABOVE_LIQUID_SHARE, AutonomyLevel.L2,
            SUITABILITY_INCOMPLETE, AutonomyLevel.L1,
            INSURANCE_CLASS, AutonomyLevel.L1,
            DATA_CONFIDENCE_LOW, AutonomyLevel.L1,
            LEGAL_CLASS, AutonomyLevel.L1,
            NOT_IN_L3_SET, AutonomyLevel.L2);

    private AutonomyGate() {
    }

    /**
     * The level a gate criterion caps an Action at, or empty for an id that is not a gate criterion (such as a
     * failed check at confirmation).
     */
    public static Optional<AutonomyLevel> cap(String criterion) {
        return Optional.ofNullable(CAPS.get(criterion));
    }

    public static GateDecision decide(GateAction action, GateContext context, GateThresholds thresholds) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(thresholds, "thresholds");
        BigDecimal amount = action.amountOrZero();
        boolean investment = action.regulatoryClass() == RegulatoryClass.INVESTMENT;

        List<Cap> caps = new ArrayList<>();
        if (context.mandate().isEmpty()) {
            caps.add(new Cap(NO_COVERING_MANDATE));
        } else {
            MandateBounds bounds = context.mandate().get();
            if (amount.compareTo(bounds.maxPerAction()) > 0
                    || context.emergencyFundAfter().compareTo(bounds.floor()) < 0) {
                caps.add(new Cap(AMOUNT_ABOVE_MANDATE_BOUNDS));
            }
        }
        if (!action.reversible()) {
            caps.add(new Cap(NOT_REVERSIBLE));
        }
        if (investment && amount.compareTo(
                thresholds.investmentShareOfLiquidAssets().multiply(context.liquidAssets())) > 0) {
            caps.add(new Cap(INVESTMENT_ABOVE_LIQUID_SHARE));
        }
        if (investment && !context.suitabilityComplete()) {
            caps.add(new Cap(SUITABILITY_INCOMPLETE));
        }
        if (action.regulatoryClass() == RegulatoryClass.INSURANCE) {
            caps.add(new Cap(INSURANCE_CLASS));
        }
        if (!context.dataConfidenceHigh()) {
            caps.add(new Cap(DATA_CONFIDENCE_LOW));
        }
        if (action.regulatoryClass() == RegulatoryClass.LEGAL) {
            caps.add(new Cap(LEGAL_CLASS));
        }

        if (caps.isEmpty()) {
            return action.type().l3Eligible() ? new GateDecision(AutonomyLevel.L3, List.of())
                    : new GateDecision(AutonomyLevel.L2, List.of(NOT_IN_L3_SET));
        }
        AutonomyLevel level = caps.stream().map(Cap::level).min(Comparator.naturalOrder()).orElseThrow();
        return new GateDecision(level, caps.stream().map(Cap::criterion).toList());
    }

    private record Cap(String criterion, AutonomyLevel level) {

        Cap(String criterion) {
            this(criterion, CAPS.get(criterion));
        }
    }
}
