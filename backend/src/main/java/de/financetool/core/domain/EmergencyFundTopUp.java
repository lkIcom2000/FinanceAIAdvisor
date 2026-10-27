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
 * Core function {@code emergencyFundTopUp}: how much to move into the emergency fund now, the gap to its target but
 * at most what an average month leaves over (net income minus monthly expenses, never below zero). Pure.
 */
public final class EmergencyFundTopUp {

    public static final String FUNCTION = "emergencyFundTopUp";
    public static final String OUTPUT = "amount";

    private EmergencyFundTopUp() {
    }

    /**
     * @param current the emergency fund today ({@link EmergencyFundTarget#OUTPUT_CURRENT})
     * @param target its target ({@link EmergencyFundTarget#OUTPUT_TARGET})
     * @param netIncome the average monthly net income ({@link MonthlyNetIncome#OUTPUT})
     * @param monthlyExpenses the average monthly expenses ({@link EmergencyFundTarget#OUTPUT_MONTHLY_EXPENSES})
     */
    public static CoreComputation of(SourcedNumber current, SourcedNumber target, SourcedNumber netIncome,
            SourcedNumber monthlyExpenses) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(netIncome, "netIncome");
        Objects.requireNonNull(monthlyExpenses, "monthlyExpenses");
        BigDecimal gap = target.value().subtract(current.value()).max(BigDecimal.ZERO);
        BigDecimal surplus = netIncome.value().subtract(monthlyExpenses.value()).max(BigDecimal.ZERO);
        BigDecimal amount = gap.min(surplus).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("current", current.value());
        keyInputs.put("target", target.value());
        keyInputs.put("netIncome", netIncome.value());
        keyInputs.put("monthlyExpenses", monthlyExpenses.value());

        return new CoreComputation(FUNCTION, "emergency fund top-up",
                "Based on your emergency fund, its target, your net income and your expenses.", keyInputs,
                List.of(FigureInput.figure("Emergency fund", current),
                        FigureInput.figure("Emergency fund target", target),
                        FigureInput.figure("Average monthly net income", netIncome),
                        FigureInput.figure("Average monthly expenses", monthlyExpenses)),
                List.of(new Figure(OUTPUT, "Suggested top-up of your emergency fund", amount, Unit.EUR)),
                List.of("Tops up at most the gap between your emergency fund and its target.",
                        "Uses at most what an average month leaves over: your net income minus your expenses."),
                Map.of());
    }
}
