package de.financetool.advisory.application;

import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.CheckChain;
import de.financetool.advisory.domain.TurnState;
import de.financetool.audit.EvidenceOrigin;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.CategorySelection;
import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.RecommendationFigures;
import de.financetool.knowledge.NoValidRuleParameterException;
import de.financetool.profile.ProfileCompleteness;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.SourcedNumber;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The Category Recommendation of a {@code RECOMMEND_CATEGORY} turn (Story 6.3, FR-20, FR-27): runs the Core's
 * category selection and cost calculation on the effective profile, and builds the Suitability Statement and the
 * figure chips from their results. Nothing here selects or computes; the Core does.
 */
@Component
class Recommendations {

    /** The figure chips of a recommendation, in card order. */
    static final List<String> CHIPS = List.of(CategorySelection.OUTPUT_SURPLUS,
            CategorySelection.OUTPUT_EMERGENCY_FUND);
    /** The selection outputs the statement lists; the conflict count and equity bounds are not figures to show. */
    static final Set<String> STATEMENT_FIGURES = Set.of(CategorySelection.OUTPUT_SURPLUS,
            CategorySelection.OUTPUT_EMERGENCY_FUND, CategorySelection.OUTPUT_EMERGENCY_FUND_TARGET,
            CategorySelection.OUTPUT_REQUESTED, CategorySelection.OUTPUT_EQUITY_MIN,
            CategorySelection.OUTPUT_EQUITY_MAX);
    /** The profile fields the selection reads, in plain words. */
    static final Map<String, String> FACT_LABELS = Map.of(
            "investment_horizon_years", "Investment horizon",
            "risk_tolerance", "Risk tolerance",
            "loss_aversion", "Concern about losses",
            "emergency_fund", "Emergency fund",
            "monthly_expenses", "Monthly expenses",
            "monthly_surplus", "Monthly surplus",
            "net_income", "Monthly net income",
            "requested_monthly_amount", "Monthly amount you have in mind");

    private final CoreGateway core;

    Recommendations(CoreGateway core) {
        this.core = core;
    }

    /**
     * Runs the selection on the stored profile with the stated facts of this turn (AD-23), and the cost calculation
     * for an investment topic. A Rule Parameter without a valid version marks the turn instead.
     */
    TurnState.Tools run(RequestContext context, Map<String, Object> stored, Map<String, Object> stated,
            String topic) {
        Map<String, Object> profile = ProfileCompleteness.effective(stored, stated);
        try {
            RecommendationFigures recommendation = core.recommendation(context, EvidenceOrigin.TURN, profile, topic,
                    CheckChain.investTopic(topic));
            return TurnState.Tools.recommended(recommendation, statement(recommendation, profile));
        } catch (NoValidRuleParameterException e) {
            return new TurnState.Tools(null, null, null, false, List.of(), true);
        }
    }

    /** The Check Chain's view of the selection: category, conflicts and whether the statement is complete. */
    static Map<String, Object> facts(ChatMessage.Recommendation statement, String topic) {
        if (statement == null) {
            return null;
        }
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("category", statement.category());
        category.put("conflicts", statement.conflicts());
        category.put(CheckChain.STATEMENT_COMPLETE, !statement.facts().isEmpty()
                && !statement.risks().isEmpty() && (!CheckChain.investTopic(topic) || !statement.costs().isEmpty()));
        return category;
    }

    /** The Suitability Statement: profile facts used, selection figures, main risks and ex-ante costs. */
    static ChatMessage.Recommendation statement(RecommendationFigures recommendation, Map<String, Object> profile) {
        CategorySelection.Selection details = recommendation.details();
        List<ChatMessage.Recommendation.Fact> facts = new ArrayList<>();
        for (String field : details.profileFields()) {
            Object value = profile.get(field);
            if (ProfileCompleteness.present(value)) {
                facts.add(new ChatMessage.Recommendation.Fact(
                        FACT_LABELS.getOrDefault(field, field.replace('_', ' ')), shown(field, value)));
            }
        }
        List<SourcedNumber> figures = numbers(recommendation.selection(), STATEMENT_FIGURES);
        List<SourcedNumber> costs = recommendation.costs() == null ? List.of()
                : recommendation.costs().numbers();
        return new ChatMessage.Recommendation(details.category(), details.label(), details.conflicts(), facts,
                figures, details.risks(), costs);
    }

    /**
     * The figure chips: the monthly surplus and the emergency fund. A profile value equal to the account figure it
     * was pre-filled from carries that figure's key, so the chip links to the Overview.
     */
    List<SourcedNumber> chips(RequestContext context, RecommendationFigures recommendation) {
        CoreResult selection = recommendation.selection();
        List<SourcedNumber> chips = new ArrayList<>();
        for (int i = 0; i < selection.outputs().size(); i++) {
            String key = selection.outputs().get(i).outputKey();
            if (!CHIPS.contains(key)) {
                continue;
            }
            SourcedNumber chip = selection.numbers().get(i);
            if (key.equals(CategorySelection.OUTPUT_EMERGENCY_FUND)) {
                SourcedNumber account = core.profileFigures(context, EvidenceOrigin.TURN).emergencyFund()
                        .number(CoreGateway.EMERGENCY_FUND);
                if (account.value().compareTo(chip.value()) == 0) {
                    chip = account;
                }
            }
            chips.add(chip);
        }
        return chips;
    }

    /** The outputs of {@code result} with the given keys, in output order. */
    private static List<SourcedNumber> numbers(CoreResult result, Set<String> keys) {
        List<SourcedNumber> out = new ArrayList<>();
        for (int i = 0; i < result.outputs().size(); i++) {
            if (keys.contains(result.outputs().get(i).outputKey())) {
                out.add(result.numbers().get(i));
            }
        }
        return out;
    }

    /** A profile value as the statement shows it: amounts with two decimals and EUR, choices in words. */
    static String shown(String field, Object value) {
        if (field.equals("investment_horizon_years")) {
            BigDecimal years = decimal(value);
            return years == null ? value.toString() : years.stripTrailingZeros().toPlainString() + " years";
        }
        if (field.equals("risk_tolerance") || field.equals("loss_aversion")) {
            return value.toString().replace('_', ' ');
        }
        BigDecimal amount = decimal(value);
        return amount == null ? value.toString() : String.format(Locale.ENGLISH, "%,.2f EUR", amount);
    }

    private static BigDecimal decimal(Object value) {
        try {
            return value instanceof Number n ? new BigDecimal(n.toString()) : new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
