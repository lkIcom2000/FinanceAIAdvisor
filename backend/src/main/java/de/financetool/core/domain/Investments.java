package de.financetool.core.domain;

import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Core functions for the persona's investments: {@code holdingValue} (units × unit price), {@code savingsPlanAmount}
 * (an active plan's monthly amount) and {@code investmentTotals} (the starting value and monthly contribution of a
 * goal projection). Pure.
 */
public final class Investments {

    public static final String HOLDING_FUNCTION = "holdingValue";
    public static final String HOLDING_OUTPUT = "value";
    public static final String PLAN_FUNCTION = "savingsPlanAmount";
    public static final String PLAN_OUTPUT = "monthlyAmount";
    public static final String TOTALS_FUNCTION = "investmentTotals";
    public static final String OUTPUT_INITIAL = "holdingsValue";
    public static final String OUTPUT_MONTHLY = "monthlyContribution";

    private Investments() {
    }

    /**
     * The value of one holding: units × unit price at the valuation date, rounded to cents only in the output.
     *
     * @param id the holding's id, part of the figure key
     */
    public static CoreComputation holdingValue(String id, String name, BigDecimal units, BigDecimal unitPrice,
            LocalDate valuationDate) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(units, "units");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(valuationDate, "valuationDate");
        BigDecimal value = money(units.multiply(unitPrice));

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("holding", id);
        keyInputs.put("units", units);
        keyInputs.put("unitPrice", unitPrice);
        keyInputs.put("valuationDate", valuationDate);

        return new CoreComputation(HOLDING_FUNCTION, "holding value",
                "Based on the price of " + name + " on " + Wording.date(valuationDate) + ".", keyInputs,
                List.of(FigureInput.number("Units", units, Unit.COUNT),
                        FigureInput.number("Price per unit on " + Wording.date(valuationDate), unitPrice, Unit.EUR)),
                List.of(new Figure(HOLDING_OUTPUT, "Value of " + name, value, Unit.EUR)),
                List.of("The value is the number of units times the latest known price per unit."),
                Map.of());
    }

    /** The monthly amount of an active savings plan. */
    public static CoreComputation savingsPlanAmount(String id, String name, BigDecimal amount, int dayOfMonth,
            LocalDate startDate) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(startDate, "startDate");
        BigDecimal monthly = money(amount);

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("plan", id);
        keyInputs.put("amount", monthly);
        keyInputs.put("dayOfMonth", dayOfMonth);
        keyInputs.put("startDate", startDate);

        return new CoreComputation(PLAN_FUNCTION, "savings plan amount",
                "Based on your savings plan " + name + ", running since " + Wording.date(startDate) + ".", keyInputs,
                List.of(FigureInput.text("Savings plan", name),
                        FigureInput.text("Paid on", "Day " + dayOfMonth + " of each month")),
                List.of(new Figure(PLAN_OUTPUT, "Monthly amount of " + name, monthly, Unit.EUR)),
                List.of("The savings plan continues with the same amount each month."),
                Map.of());
    }

    /**
     * The starting value (sum of the holding values) and the monthly contribution (sum of the active savings plans)
     * of a goal projection.
     */
    public static CoreComputation totals(List<SourcedNumber> holdingValues, List<String> holdingNames,
            List<SourcedNumber> planAmounts, List<String> planNames) {
        if (holdingValues.size() != holdingNames.size() || planAmounts.size() != planNames.size()) {
            throw new IllegalArgumentException("one name per figure");
        }
        BigDecimal initial = money(holdingValues.stream().map(SourcedNumber::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal monthly = money(planAmounts.stream().map(SourcedNumber::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("holdings", holdingValues.stream().map(n -> (Object) n.provId()).toList());
        keyInputs.put("plans", planAmounts.stream().map(n -> (Object) n.provId()).toList());
        keyInputs.put("initial", initial);
        keyInputs.put("monthly", monthly);

        List<FigureInput> inputs = new ArrayList<>();
        for (int i = 0; i < holdingValues.size(); i++) {
            inputs.add(FigureInput.figure("Value of " + holdingNames.get(i), holdingValues.get(i)));
        }
        for (int i = 0; i < planAmounts.size(); i++) {
            inputs.add(FigureInput.figure("Monthly amount of " + planNames.get(i), planAmounts.get(i)));
        }
        if (inputs.isEmpty()) {
            inputs.add(FigureInput.text("Investments", "You don't have any investments or savings plans."));
        }

        return new CoreComputation(TOTALS_FUNCTION, "investment totals",
                "Based on your holdings and savings plans.", keyInputs, inputs,
                List.of(new Figure(OUTPUT_INITIAL, "Value of your investments today", initial, Unit.EUR),
                        new Figure(OUTPUT_MONTHLY, "Monthly savings plan contributions", monthly, Unit.EUR)),
                List.of("All your holdings and active savings plans are added up."),
                Map.of());
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
