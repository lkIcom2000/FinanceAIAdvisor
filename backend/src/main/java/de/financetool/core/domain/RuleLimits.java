package de.financetool.core.domain;

import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Core function {@code ruleLimits}: the limits suggested for the two rule templates (FR-36). Safety cushion: the
 * lowest forecast balance below zero per transfer, twice that per month, the emergency fund target as the overnight
 * deposit's floor. Emergency fund top-up: the suggested top-up per transfer and per month, 0 EUR as the current
 * account's floor. Amounts are rounded up to the next 100 EUR, at least 100 EUR. Pure.
 */
public final class RuleLimits {

    public static final String FUNCTION = "ruleLimits";
    public static final String OUTPUT_CUSHION_MAX_PER_TRANSFER = "cushionMaxPerTransfer";
    public static final String OUTPUT_CUSHION_MAX_PER_MONTH = "cushionMaxPerMonth";
    public static final String OUTPUT_CUSHION_FLOOR = "cushionFloor";
    public static final String OUTPUT_TOP_UP_MAX_PER_TRANSFER = "topUpMaxPerTransfer";
    public static final String OUTPUT_TOP_UP_MAX_PER_MONTH = "topUpMaxPerMonth";
    public static final String OUTPUT_TOP_UP_FLOOR = "topUpFloor";

    static final BigDecimal STEP = new BigDecimal("100");

    private RuleLimits() {
    }

    /**
     * @param lowest the lowest expected balance of the forecast ({@link ShortfallDetection#OUTPUT_LOWEST})
     * @param emergencyFundTarget the emergency fund target ({@link EmergencyFundTarget#OUTPUT_TARGET})
     * @param topUp the suggested emergency fund top-up ({@link EmergencyFundTopUp#OUTPUT})
     */
    public static CoreComputation of(SourcedNumber lowest, SourcedNumber emergencyFundTarget, SourcedNumber topUp) {
        Objects.requireNonNull(lowest, "lowest");
        Objects.requireNonNull(emergencyFundTarget, "emergencyFundTarget");
        Objects.requireNonNull(topUp, "topUp");
        BigDecimal cushion = roundUp(lowest.value().negate());
        BigDecimal topUpAmount = roundUp(topUp.value());
        BigDecimal floor = emergencyFundTarget.value().max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("lowest", lowest.value());
        keyInputs.put("emergencyFundTarget", emergencyFundTarget.value());
        keyInputs.put("topUp", topUp.value());

        return new CoreComputation(FUNCTION, "suggested rule limits",
                "Based on your lowest forecast balance, your emergency fund target and the suggested top-up.",
                keyInputs,
                List.of(FigureInput.figure("Lowest balance in the next three months", lowest),
                        FigureInput.figure("Emergency fund target", emergencyFundTarget),
                        FigureInput.figure("Suggested top-up of your emergency fund", topUp)),
                List.of(new Figure(OUTPUT_CUSHION_MAX_PER_TRANSFER, "Suggested most per transfer", cushion, Unit.EUR),
                        new Figure(OUTPUT_CUSHION_MAX_PER_MONTH, "Suggested most per month",
                                cushion.multiply(BigDecimal.TWO), Unit.EUR),
                        new Figure(OUTPUT_CUSHION_FLOOR, "Suggested minimum for your overnight deposit", floor,
                                Unit.EUR),
                        new Figure(OUTPUT_TOP_UP_MAX_PER_TRANSFER, "Suggested most per transfer", topUpAmount,
                                Unit.EUR),
                        new Figure(OUTPUT_TOP_UP_MAX_PER_MONTH, "Suggested most per month", topUpAmount, Unit.EUR),
                        new Figure(OUTPUT_TOP_UP_FLOOR, "Suggested minimum for your current account",
                                BigDecimal.ZERO.setScale(2), Unit.EUR)),
                List.of("Amounts are rounded up to the next 100 EUR, at least 100 EUR.",
                        "A safety cushion covers the lowest forecast balance; it may move that twice a month.",
                        "Your overnight deposit keeps at least your emergency fund target."),
                Map.of());
    }

    /** Rounded up to the next {@link #STEP}, at least one step; a negative amount counts as 0. */
    static BigDecimal roundUp(BigDecimal amount) {
        BigDecimal steps = amount.max(BigDecimal.ZERO).divide(STEP, 0, RoundingMode.CEILING);
        return STEP.multiply(steps.max(BigDecimal.ONE)).setScale(2, RoundingMode.UNNECESSARY);
    }
}
