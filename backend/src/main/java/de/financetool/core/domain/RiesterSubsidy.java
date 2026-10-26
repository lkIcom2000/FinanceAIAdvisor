package de.financetool.core.domain;

import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Core function {@code riesterSubsidy}: the yearly Riester allowances and the minimum own contribution that earns them
 * in full (FR-20, §§ 10a, 84-86 EStG). Pure; the Rule Parameter values valid at the date asked come in as arguments. A
 * port of {@code riester_subsidy} in {@code experiments/finlab/core.py}, in decimal arithmetic.
 */
public final class RiesterSubsidy {

    public static final String FUNCTION = "riesterSubsidy";

    public static final String BASIC = "riester_basic_allowance";
    public static final String CHILD_NEW = "riester_child_allowance_born_2008_or_later";
    public static final String CHILD_OLD = "riester_child_allowance_born_before_2008";
    public static final String RATE = "riester_min_contribution_rate";
    public static final String FLOOR = "riester_min_contribution_floor";
    public static final String CAP = "riester_special_expense_cap";
    /** Every Rule Parameter the function needs; the gateway refuses to compute unless all are valid. */
    public static final List<String> PARAMETERS = List.of(BASIC, CHILD_NEW, CHILD_OLD, RATE, FLOOR, CAP);

    public static final String BASIC_ALLOWANCE = "basicAllowance";
    public static final String CHILD_ALLOWANCES = "childAllowances";
    public static final String TOTAL_ALLOWANCES = "totalAllowances";
    public static final String MIN_OWN_CONTRIBUTION = "minOwnContribution";
    public static final String ELIGIBLE_CHILDREN = "eligibleChildren";

    /** Children born in this year or later get the higher child allowance. */
    static final int NEW_CHILD_ALLOWANCE_FROM = 2008;

    private RiesterSubsidy() {
    }

    /**
     * @param priorYearGrossIncome the gross income subject to pension insurance in the previous year, in EUR
     * @param childrenBirthYears the birth years of the children child benefit is received for
     * @param parameters the values of {@link #PARAMETERS}, by key
     */
    public static CoreComputation compute(BigDecimal priorYearGrossIncome, List<Integer> childrenBirthYears,
            Map<String, RuleParameterValue> parameters) {
        Objects.requireNonNull(priorYearGrossIncome, "priorYearGrossIncome");
        List<Integer> children = List.copyOf(childrenBirthYears);
        if (priorYearGrossIncome.signum() < 0) {
            throw new IllegalArgumentException("an income of at least zero");
        }
        BigDecimal basic = value(parameters, BASIC);
        BigDecimal childNew = value(parameters, CHILD_NEW);
        BigDecimal childOld = value(parameters, CHILD_OLD);
        BigDecimal childTotal = children.stream()
                .map(year -> year >= NEW_CHILD_ALLOWANCE_FROM ? childNew : childOld)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allowances = basic.add(childTotal);
        BigDecimal minTotal = value(parameters, CAP).min(value(parameters, RATE).multiply(priorYearGrossIncome));
        BigDecimal own = value(parameters, FLOOR).max(minTotal.subtract(allowances));

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("priorYearGrossIncome", priorYearGrossIncome);
        keyInputs.put("childrenBirthYears", children.stream().sorted().toList());
        List<FigureInput> inputs = List.of(
                FigureInput.number("Gross income in the previous year", priorYearGrossIncome, Unit.EUR),
                FigureInput.number("Children with child benefit", BigDecimal.valueOf(children.size()), Unit.COUNT));
        List<Figure> outputs = List.of(
                new Figure(BASIC_ALLOWANCE, "Basic allowance per year", money(basic), Unit.EUR),
                new Figure(CHILD_ALLOWANCES, "Child allowances per year", money(childTotal), Unit.EUR),
                new Figure(TOTAL_ALLOWANCES, "Total allowances per year", money(allowances), Unit.EUR),
                new Figure(MIN_OWN_CONTRIBUTION, "Minimum own contribution per year for the full allowances",
                        money(own), Unit.EUR),
                new Figure(ELIGIBLE_CHILDREN, "Children eligible for a child allowance",
                        BigDecimal.valueOf(children.size()), Unit.COUNT));
        // No digits in fixed texts: the Number Check would flag a faithful echo of them.
        List<String> assumptions = List.of(
                "Directly eligible person, e.g. an employee with statutory pension insurance",
                "Child allowances only for children child benefit is received for",
                "The tax deduction for special expenses is not included");
        Map<String, String> versions = new LinkedHashMap<>();
        PARAMETERS.forEach(key -> versions.put(key, parameters.get(key).version()));
        return new CoreComputation(FUNCTION, "Riester subsidy",
                "Based on the income and children you gave and the subsidy rules valid on the date asked.", keyInputs,
                inputs, outputs, assumptions, versions);
    }

    private static BigDecimal value(Map<String, RuleParameterValue> parameters, String key) {
        RuleParameterValue parameter = parameters.get(key);
        if (parameter == null) {
            throw new IllegalArgumentException("missing Rule Parameter " + key);
        }
        return parameter.value();
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
