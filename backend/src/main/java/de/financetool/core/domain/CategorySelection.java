package de.financetool.core.domain;

import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.modulith.NamedInterface;

/**
 * Core function {@code categorySelection}: the rule-based Category Recommendation (FR-20, FR-27), a port of
 * {@code select_category} of {@code experiments/finlab/core.py}. Its only inputs are the Suitability Profile (stored
 * plus the facts the message states, AD-23), the topic and Rule Parameters; no commission, inducement, operator
 * preference or product is an input. Pure.
 */
public final class CategorySelection {

    public static final String FUNCTION = "categorySelection";
    public static final String SHORT_HORIZON = "short_horizon_years_max";
    public static final String OUTPUT_CONFLICTS = "conflicts";
    public static final String OUTPUT_SURPLUS = "monthlySurplus";
    public static final String OUTPUT_EMERGENCY_FUND = "emergencyFund";
    public static final String OUTPUT_EMERGENCY_FUND_TARGET = "emergencyFundTarget";
    public static final String OUTPUT_REQUESTED = "requestedMonthlyAmount";
    public static final String OUTPUT_EQUITY_MIN = "equityShareMin";
    public static final String OUTPUT_EQUITY_MAX = "equityShareMax";
    public static final String BUILD_EMERGENCY_FUND_FIRST = "build_emergency_fund_first";
    public static final String EMERGENCY_FUND_BELOW_TARGET = "emergency fund below target";
    public static final String REQUESTED_EXCEEDS_SURPLUS = "requested monthly amount exceeds monthly surplus";

    /** Risk buckets in the experiment's matching order ({@code _risk_bucket}). */
    static final List<String> BUCKETS = List.of("very_low", "very_high", "low", "medium", "high");

    /** The profile fields the selection reads, in reading order. */
    static final List<String> FIELDS = List.of("investment_horizon_years", "risk_tolerance", "loss_aversion",
            "emergency_fund", "monthly_expenses", "monthly_surplus", "net_income", "requested_monthly_amount");

    /** Each category in plain words. */
    public static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("review_duplicate_cover", "review cover that may be held twice"),
            Map.entry("no_credit_financed_investing", "no investing with borrowed money"),
            Map.entry("distributing_or_accumulating_with_unused_allowance",
                    "a distributing or accumulating fund that uses the unused tax-free allowance"),
            Map.entry("sustainable_diversified_etf", "a sustainable, broadly diversified ETF"),
            Map.entry("broad_world_equity_etf_for_diversification",
                    "a broad world equity ETF to spread the holdings"),
            Map.entry("short_term_deposit_or_money_market", "a short-term deposit or a money market fund"),
            Map.entry(BUILD_EMERGENCY_FUND_FIRST, "build the emergency fund first"),
            Map.entry("deposit_or_low_risk_mix", "deposits or a cautious mix"),
            Map.entry("diversified_low_cost_world_equity_etf_savings_plan",
                    "a savings plan in a broad, inexpensive world equity ETF"),
            Map.entry("balanced_etf_portfolio", "a balanced ETF portfolio"));

    /** The main risks of each category, plain sentences for the Suitability Statement. */
    public static final Map<String, List<String>> RISKS = Map.ofEntries(
            Map.entry("review_duplicate_cover", List.of(
                    "Cancelling a contract too early can leave a gap in cover.")),
            Map.entry("no_credit_financed_investing", List.of(
                    "Losses on borrowed money add to the loan you still have to repay.")),
            Map.entry("distributing_or_accumulating_with_unused_allowance", List.of(
                    "The value of the fund can fall; past returns are no guide to future ones.")),
            Map.entry("sustainable_diversified_etf", List.of(
                    "The value can fall, also for several years.",
                    "A narrower sustainable index can move differently from the broad market.")),
            Map.entry("broad_world_equity_etf_for_diversification", List.of(
                    "The value can fall, also for several years.")),
            Map.entry("short_term_deposit_or_money_market", List.of(
                    "Inflation can reduce what the money buys.")),
            Map.entry(BUILD_EMERGENCY_FUND_FIRST, List.of(
                    "Without an emergency fund, an unexpected expense can force you to sell investments at a loss.",
                    "Equity investments can fall in value, also for several years.")),
            Map.entry("deposit_or_low_risk_mix", List.of(
                    "Inflation can reduce what the money buys.",
                    "A low-risk mix can still lose value.")),
            Map.entry("diversified_low_cost_world_equity_etf_savings_plan", List.of(
                    "The value can fall, also for several years.",
                    "Currency moves affect a world equity fund.")),
            Map.entry("balanced_etf_portfolio", List.of(
                    "The value can fall, also for several years.",
                    "Bond prices fall when interest rates rise.")));

    /**
     * The non-numeric result.
     *
     * @param category the category key
     * @param reasons why, plain sentences
     * @param conflicts where the request conflicts with the profile
     * @param riskBucket the risk tolerance bucket, {@code null} if unknown
     * @param profileFields the profile fields the selection read and found
     * @param ruleParameters the Rule Parameters the selection used
     */
    @NamedInterface("results")
    public record Selection(String category, List<String> reasons, List<String> conflicts, String riskBucket,
            List<String> profileFields, List<String> ruleParameters) {

        public Selection {
            Objects.requireNonNull(category, "category");
            reasons = List.copyOf(reasons);
            conflicts = List.copyOf(conflicts);
            profileFields = List.copyOf(profileFields);
            ruleParameters = List.copyOf(ruleParameters);
        }

        /** The category in plain words. */
        public String label() {
            return LABELS.getOrDefault(category, category.replace('_', ' '));
        }

        /** The category's main risks. */
        public List<String> risks() {
            return RISKS.getOrDefault(category, List.of());
        }
    }

    /** The computation for the gateway and the selection for the turn. */
    public record Selected(CoreComputation computation, Selection selection) {
    }

    private CategorySelection() {
    }

    /** The Rule Parameters the gateway passes: horizon limit, emergency fund months and the equity share ranges. */
    public static List<String> parameterKeys() {
        List<String> keys = new ArrayList<>(List.of(SHORT_HORIZON, EmergencyFundTarget.MONTHS_MIN));
        for (String bucket : List.of("very_low", "low", "medium", "high", "very_high")) {
            keys.add(equityKey(bucket, "min"));
            keys.add(equityKey(bucket, "max"));
        }
        return List.copyOf(keys);
    }

    static String equityKey(String bucket, String end) {
        return "equity_share_" + bucket + "_" + end;
    }

    /**
     * @param profile profile field → value (amounts and numbers as {@link Number} or numeric text, choices as keys)
     * @param topic the NLU topic
     * @param parameters every key of {@link #parameterKeys()}
     */
    public static Selected of(Map<String, ?> profile, String topic, LocalDate asOf,
            Map<String, RuleParameterValue> parameters) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(asOf, "asOf");
        topic = topic == null ? "" : topic;
        for (String key : parameterKeys()) {
            if (parameters.get(key) == null || !parameters.get(key).key().equals(key)) {
                throw new IllegalArgumentException("Rule Parameter " + key + " missing");
            }
        }
        Map<String, String> used = new TreeMap<>();
        RuleParameterValue shortParameter = parameters.get(SHORT_HORIZON);
        used.put(shortParameter.key(), shortParameter.version());
        BigDecimal shortHorizon = shortParameter.value();

        BigDecimal horizon = decimal(profile.get("investment_horizon_years"));
        String risk = bucket(profile.get("risk_tolerance"));
        String lossAversion = text(profile.get("loss_aversion"));
        BigDecimal emergencyFund = money(profile.get("emergency_fund"));
        BigDecimal expenses = money(profile.get("monthly_expenses"));
        BigDecimal income = money(profile.get("net_income"));
        BigDecimal surplus = money(profile.get("monthly_surplus"));
        if (surplus == null && income != null && expenses != null) {
            surplus = income.subtract(expenses);
        }
        BigDecimal requested = money(profile.get("requested_monthly_amount"));

        // _ef_adequate: null when the fund or the expenses are unknown
        BigDecimal target = null;
        Boolean efOk = null;
        if (expenses != null) {
            RuleParameterValue months = parameters.get(EmergencyFundTarget.MONTHS_MIN);
            used.put(months.key(), months.version());
            target = expenses.multiply(months.value()).setScale(2, RoundingMode.HALF_UP);
            if (emergencyFund != null) {
                efOk = emergencyFund.compareTo(target) >= 0;
            }
        }

        List<String> conflicts = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        String category;
        String equityBucket = null;
        if (topic.equals("protection_duplicate")) {
            category = "review_duplicate_cover";
            reasons.add("Same risk appears to be covered by more than one contract");
        } else if (topic.equals("leverage_or_loan")) {
            category = "no_credit_financed_investing";
            reasons.add("Credit-financed equity investing adds loss risk the profile cannot absorb");
            if (Boolean.FALSE.equals(efOk)) {
                conflicts.add(EMERGENCY_FUND_BELOW_TARGET);
            }
        } else if (topic.equals("product_variant_tax")) {
            category = "distributing_or_accumulating_with_unused_allowance";
            reasons.add("Unused saver allowance can absorb distributions or the advance lump sum");
        } else if (topic.equals("sustainability_fit")) {
            category = "sustainable_diversified_etf";
            reasons.add("Stated sustainability preference must be met (MiFID II sustainability preferences)");
        } else if (topic.equals("diversification")) {
            category = "broad_world_equity_etf_for_diversification";
            reasons.add("Concentrated single-sector holdings; a broad index reduces concentration risk");
        } else if (horizon != null && horizon.compareTo(shortHorizon) <= 0) {
            category = "short_term_deposit_or_money_market";
            reasons.add("Horizon of " + plain(horizon) + " years is at most " + plain(shortHorizon) + " years");
        } else if (Boolean.FALSE.equals(efOk)) {
            category = BUILD_EMERGENCY_FUND_FIRST;
            reasons.add("Emergency fund below target");
            conflicts.add(EMERGENCY_FUND_BELOW_TARGET);
        } else if ("very_low".equals(risk) || "high".equals(lossAversion) || "very_high".equals(lossAversion)) {
            category = "deposit_or_low_risk_mix";
            reasons.add("Very low risk tolerance or strong loss aversion");
        } else {
            category = "high".equals(risk) || "very_high".equals(risk)
                    ? "diversified_low_cost_world_equity_etf_savings_plan" : "balanced_etf_portfolio";
            equityBucket = risk == null ? "medium" : risk;
            reasons.add("Risk tolerance '" + equityBucket + "' with horizon "
                    + (horizon == null ? "None" : plain(horizon)) + " years");
        }
        if (requested != null && surplus != null && requested.compareTo(surplus) > 0) {
            conflicts.add(REQUESTED_EXCEEDS_SURPLUS);
        }

        List<Figure> outputs = new ArrayList<>();
        outputs.add(new Figure(OUTPUT_CONFLICTS, "Conflicts with your profile", BigDecimal.valueOf(conflicts.size()),
                Unit.COUNT));
        if (equityBucket != null) {
            RuleParameterValue min = parameters.get(equityKey(equityBucket, "min"));
            RuleParameterValue max = parameters.get(equityKey(equityBucket, "max"));
            used.put(min.key(), min.version());
            used.put(max.key(), max.version());
            outputs.add(new Figure(OUTPUT_EQUITY_MIN, "Equity share, lower bound", min.value(), Unit.FRACTION));
            outputs.add(new Figure(OUTPUT_EQUITY_MAX, "Equity share, upper bound", max.value(), Unit.FRACTION));
        }
        if (surplus != null) {
            outputs.add(new Figure(OUTPUT_SURPLUS, "Monthly surplus", surplus, Unit.EUR));
        }
        if (emergencyFund != null) {
            outputs.add(new Figure(OUTPUT_EMERGENCY_FUND, "Emergency fund", emergencyFund, Unit.EUR));
        }
        if (target != null) {
            outputs.add(new Figure(OUTPUT_EMERGENCY_FUND_TARGET, "Emergency fund target", target, Unit.EUR));
        }
        if (requested != null) {
            outputs.add(new Figure(OUTPUT_REQUESTED, "Requested monthly amount", requested, Unit.EUR));
        }

        Map<String, Object> read = new LinkedHashMap<>();
        for (String field : FIELDS) {
            Object value = canonical(profile.get(field));
            if (value != null) {
                read.put(field, value);
            }
        }
        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("profile", read);
        keyInputs.put("topic", topic);
        List<FigureInput> inputs = new ArrayList<>();
        read.forEach((field, value) -> inputs.add(value instanceof BigDecimal number
                ? FigureInput.number(field.replace('_', ' '), number,
                        field.equals("investment_horizon_years") ? Unit.YEARS : Unit.EUR)
                : FigureInput.text(field.replace('_', ' '), value.toString())));
        Selection selection = new Selection(category, reasons, conflicts, risk, List.copyOf(read.keySet()),
                List.copyOf(used.keySet()));
        CoreComputation computation = new CoreComputation(FUNCTION, "category selection",
                "Based only on your profile and the rule parameters; no commission or product preference is an"
                        + " input.",
                keyInputs, inputs, outputs,
                List.of("Selection inputs are limited to the profile and Rule Parameters (no commission, no product"
                                + " preference)",
                        "Category level only: no issuer, ISIN, insurer or tariff",
                        "Selected category: " + selection.label()),
                used);
        return new Selected(computation, selection);
    }

    /** {@code _risk_bucket}: the first bucket the risk tolerance text contains. */
    static String bucket(Object riskTolerance) {
        String value = text(riskTolerance);
        if (value == null) {
            return null;
        }
        String key = value.toLowerCase(Locale.ROOT).replace(' ', '_');
        return BUCKETS.stream().filter(key::contains).findFirst().orElse(null);
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().strip();
    }

    /** A profile number as a decimal; {@code null} when absent or not a number. */
    static BigDecimal decimal(Object value) {
        try {
            return switch (value) {
                case null -> null;
                case BigDecimal d -> d;
                case Double d -> BigDecimal.valueOf(d);
                case Float f -> BigDecimal.valueOf(f.doubleValue());
                case Number n -> new BigDecimal(n.toString());
                default -> value.toString().isBlank() ? null : new BigDecimal(value.toString().strip());
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal money(Object value) {
        BigDecimal d = decimal(value);
        return d == null ? null : d.setScale(2, RoundingMode.HALF_UP);
    }

    /** A profile value in a form {@link CanonicalForm} accepts; numeric text counts as a number, as in the rules. */
    private static Object canonical(Object value) {
        BigDecimal number = decimal(value);
        if (number != null) {
            return number.stripTrailingZeros().scale() < 0 ? number.setScale(0) : number.stripTrailingZeros();
        }
        return text(value);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
