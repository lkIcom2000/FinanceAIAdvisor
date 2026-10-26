package de.financetool.core.domain;

import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Core function {@code costCalculation}: the ex-ante costs of a broad world equity ETF against an active fund on a
 * constant invested amount (FR-20), a port of {@code cost_comparison} of {@code experiments/finlab/core.py}. Pure.
 */
public final class CostCalculation {

    public static final String FUNCTION = "costCalculation";
    public static final String ETF_TER = "reference_ter_world_equity_etf";
    public static final String ACTIVE_TER = "reference_ter_active_fund";
    public static final String FRONT_LOAD = "reference_front_load_active_fund";
    public static final String OUTPUT_ETF_TER = "etfTer";
    public static final String OUTPUT_ACTIVE_TER = "activeTer";
    public static final String OUTPUT_FRONT_LOAD = "frontLoad";
    public static final String OUTPUT_ETF_TOTAL = "etfCostsTotal";
    public static final String OUTPUT_ACTIVE_TOTAL = "activeCostsTotal";
    public static final String OUTPUT_DIFFERENCE = "costDifference";
    public static final String OUTPUT_FRONT_LOAD_AMOUNT = "frontLoadAmount";
    /** The comparison period of a recommendation ({@code _advice_tools}). */
    public static final int YEARS = 10;
    /** The amount compared when the profile names none ({@code _advice_tools}). */
    public static final BigDecimal DEFAULT_AMOUNT = new BigDecimal("10000.00");

    private CostCalculation() {
    }

    /**
     * The amount a recommendation compares ({@code _advice_tools}): the profile's {@code amount}, else its requested
     * monthly amount times 12, else {@link #DEFAULT_AMOUNT}; a zero or missing value counts as none.
     */
    public static BigDecimal amount(Map<String, ?> profile) {
        BigDecimal amount = CategorySelection.decimal(profile.get("amount"));
        if (amount != null && amount.signum() > 0) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal monthly = CategorySelection.decimal(profile.get("requested_monthly_amount"));
        if (monthly != null && monthly.signum() > 0) {
            return monthly.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP);
        }
        return DEFAULT_AMOUNT;
    }

    /** @param amount the invested amount in EUR, constant over {@code years}; no returns assumed */
    public static CoreComputation of(BigDecimal amount, int years, RuleParameterValue etfTer,
            RuleParameterValue activeTer, RuleParameterValue frontLoad) {
        Objects.requireNonNull(amount, "amount");
        requireKey(etfTer, ETF_TER);
        requireKey(activeTer, ACTIVE_TER);
        requireKey(frontLoad, FRONT_LOAD);
        if (amount.signum() < 0 || years <= 0) {
            throw new IllegalArgumentException("amount must not be negative and years positive");
        }
        BigDecimal invested = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal span = BigDecimal.valueOf(years);
        BigDecimal etfTotal = money(invested.multiply(etfTer.value()).multiply(span));
        BigDecimal loadAmount = money(invested.multiply(frontLoad.value()));
        BigDecimal activeTotal = money(invested.multiply(frontLoad.value())
                .add(invested.multiply(activeTer.value()).multiply(span)));

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("amount", invested);
        keyInputs.put("years", years);
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put(etfTer.key(), etfTer.version());
        versions.put(activeTer.key(), activeTer.version());
        versions.put(frontLoad.key(), frontLoad.version());
        return new CoreComputation(FUNCTION, "cost calculation",
                "Based on reference costs of a broad world equity ETF and of an active fund.",
                keyInputs,
                List.of(FigureInput.number("Amount invested", invested, Unit.EUR),
                        FigureInput.number("Years", span, Unit.YEARS)),
                List.of(new Figure(OUTPUT_ETF_TER, "ETF ongoing charges per year", etfTer.value(), Unit.FRACTION),
                        new Figure(OUTPUT_ACTIVE_TER, "Active fund ongoing charges per year", activeTer.value(),
                                Unit.FRACTION),
                        new Figure(OUTPUT_FRONT_LOAD, "Active fund front-end load", frontLoad.value(), Unit.FRACTION),
                        new Figure(OUTPUT_ETF_TOTAL, "ETF costs over " + years + " years", etfTotal, Unit.EUR),
                        new Figure(OUTPUT_ACTIVE_TOTAL, "Active fund costs over " + years + " years", activeTotal,
                                Unit.EUR),
                        new Figure(OUTPUT_DIFFERENCE, "Cost difference", activeTotal.subtract(etfTotal), Unit.EUR),
                        new Figure(OUTPUT_FRONT_LOAD_AMOUNT, "Front-end load in EUR", loadAmount, Unit.EUR)),
                List.of("Ex-ante costs on a constant invested amount, no returns assumed",
                        "ETF: ongoing charges only; active fund: front-end load once plus ongoing charges",
                        "Illustrative reference costs, not the costs of a specific product"),
                versions);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static void requireKey(RuleParameterValue parameter, String key) {
        Objects.requireNonNull(parameter, key);
        if (!parameter.key().equals(key)) {
            throw new IllegalArgumentException("expected Rule Parameter " + key + ", got " + parameter.key());
        }
    }
}
