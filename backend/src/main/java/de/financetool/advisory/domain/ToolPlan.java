package de.financetool.advisory.domain;

import de.financetool.shared.domain.SimulationSpec;
import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Stage 3's plan (AD-3): a pure function of the extracted parameters that names the whitelisted tool a calculation
 * needs and the parameters it runs with. A port of {@code _tool_plan} in {@code experiments/finlab/pipeline.py}; the
 * LLM never takes part. Only the withdrawal duration is planned so far.
 */
public final class ToolPlan {

    /** The calculation tools a plan can name; the registry maps each to its Core function. */
    public enum Tool {
        WITHDRAWAL_DURATION("withdrawalDuration");

        private final String function;

        Tool(String function) {
            this.function = function;
        }

        /** The spec's function id. */
        public String function() {
            return function;
        }

        public static Optional<Tool> ofFunction(String function) {
            for (Tool tool : values()) {
                if (tool.function.equals(function)) {
                    return Optional.of(tool);
                }
            }
            return Optional.empty();
        }
    }

    public static final String PORTFOLIO = "portfolio";
    public static final String MONTHLY_WITHDRAWAL = "monthlyWithdrawal";
    public static final String ANNUAL_RATE = "annualRate";

    /** Bounds a changed value must keep; the extracted values must keep them too to be computable. */
    static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");
    static final BigDecimal MAX_RATE = new BigDecimal("0.2");
    static final BigDecimal MIN_RATE = MAX_RATE.negate();

    /** The message speaks of the persona's own investments without naming an amount. */
    static final Pattern OWN_SAVINGS = Pattern.compile(
            "\\bmy (savings|portfolio|investments?|capital|assets|money)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * A planned calculation before account data is read.
     *
     * @param stated the parameters stated in the message, by spec key
     * @param portfolioFromAccount whether the portfolio is the persona's investments today (Core investment totals)
     */
    public record Planned(Tool tool, Map<String, BigDecimal> stated, boolean portfolioFromAccount) {

        public Planned {
            Objects.requireNonNull(tool, "tool");
            stated = Map.copyOf(stated);
        }
    }

    private ToolPlan() {
    }

    /**
     * The calculation the extraction asks for, if it has every parameter: withdrawal duration when the topic is
     * {@code withdrawal_duration} or portfolio and monthly withdrawal are extracted, and portfolio, withdrawal and
     * rate are known. A missing portfolio comes from the persona's investments if the message speaks of them.
     */
    public static Optional<Planned> plan(NluExtraction extraction, String message) {
        Map<String, Double> p = extraction.computationParams();
        boolean withdrawal = extraction.topic().equals("withdrawal_duration")
                || p.containsKey("portfolio") && p.containsKey("monthly_withdrawal");
        if (!withdrawal || !p.containsKey("monthly_withdrawal") || !p.containsKey("annual_rate")) {
            return Optional.empty();
        }
        BigDecimal monthly = money(p.get("monthly_withdrawal"));
        BigDecimal rate = rate(p.get("annual_rate"));
        // A portfolio of zero is the model's placeholder for "not stated", not a value.
        if (p.getOrDefault("portfolio", 0.0) > 0) {
            BigDecimal portfolio = money(p.get("portfolio"));
            return valid(portfolio, monthly, rate) ? Optional.of(new Planned(Tool.WITHDRAWAL_DURATION,
                    Map.of(PORTFOLIO, portfolio, MONTHLY_WITHDRAWAL, monthly, ANNUAL_RATE, rate), false))
                    : Optional.empty();
        }
        if (!OWN_SAVINGS.matcher(message).find() || !valid(BigDecimal.ONE, monthly, rate)) {
            return Optional.empty();
        }
        return Optional.of(new Planned(Tool.WITHDRAWAL_DURATION,
                Map.of(MONTHLY_WITHDRAWAL, monthly, ANNUAL_RATE, rate), true));
    }

    /**
     * The spec the proposal card shows.
     *
     * @param accountPortfolio the persona's investments today; required if the plan takes the portfolio from
     *     account data
     */
    public static SimulationSpec spec(Planned planned, SourcedNumber accountPortfolio) {
        List<SimulationSpec.Parameter> parameters = new ArrayList<>();
        if (planned.portfolioFromAccount()) {
            Objects.requireNonNull(accountPortfolio, "accountPortfolio");
            parameters.add(new SimulationSpec.Parameter(PORTFOLIO, "Starting portfolio", accountPortfolio.value(),
                    Unit.EUR, SimulationSpec.Source.ACCOUNT_DATA, accountPortfolio.provId()));
        } else {
            parameters.add(stated(PORTFOLIO, "Starting portfolio", planned, Unit.EUR));
        }
        parameters.add(stated(MONTHLY_WITHDRAWAL, "Monthly withdrawal", planned, Unit.EUR));
        parameters.add(stated(ANNUAL_RATE, "Assumed annual return", planned, Unit.FRACTION));
        return new SimulationSpec(planned.tool().function(), parameters);
    }

    /**
     * What is wrong with the values of a spec about to be calculated, in plain words; empty if it can run: a known
     * tool, a portfolio and a withdrawal above zero and at most 1,000,000,000 EUR, a return between -20 % and 20 %.
     */
    public static List<String> problems(SimulationSpec spec) {
        if (Tool.ofFunction(spec.function()).isEmpty()) {
            return List.of("This calculation is not available.");
        }
        List<String> problems = new ArrayList<>();
        BigDecimal portfolio = spec.parameter(PORTFOLIO).value();
        BigDecimal monthly = spec.parameter(MONTHLY_WITHDRAWAL).value();
        BigDecimal rate = spec.parameter(ANNUAL_RATE).value();
        if (!amount(portfolio)) {
            problems.add("The starting portfolio must be more than 0 and at most 1,000,000,000 EUR.");
        }
        if (!amount(monthly)) {
            problems.add("The monthly withdrawal must be more than 0 and at most 1,000,000,000 EUR.");
        }
        if (rate.compareTo(MIN_RATE) < 0 || rate.compareTo(MAX_RATE) > 0) {
            problems.add("The assumed annual return must be between -20 % and 20 %.");
        }
        return problems;
    }

    private static boolean valid(BigDecimal portfolio, BigDecimal monthly, BigDecimal rate) {
        return amount(portfolio) && amount(monthly) && rate.compareTo(MIN_RATE) >= 0 && rate.compareTo(MAX_RATE) <= 0;
    }

    private static boolean amount(BigDecimal value) {
        return value.signum() > 0 && value.compareTo(MAX_AMOUNT) <= 0;
    }

    private static SimulationSpec.Parameter stated(String key, String label, Planned planned, Unit unit) {
        return new SimulationSpec.Parameter(key, label, planned.stated().get(key), unit,
                SimulationSpec.Source.QUESTION, null);
    }

    /** An amount in EUR with cents, as the Core shows money. */
    private static BigDecimal money(double value) {
        return new BigDecimal(Double.toString(value)).setScale(2, RoundingMode.HALF_UP);
    }

    /** A rate as the shortest decimal of the extracted fraction ({@code 0.04}, never {@code 0.0400}). */
    private static BigDecimal rate(double value) {
        BigDecimal rate = new BigDecimal(Double.toString(value)).stripTrailingZeros();
        return rate.scale() < 0 ? rate.setScale(0) : rate;
    }
}
