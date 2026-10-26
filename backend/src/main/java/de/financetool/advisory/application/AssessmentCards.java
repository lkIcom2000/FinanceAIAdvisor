package de.financetool.advisory.application;

import de.financetool.advisory.domain.ChatMessage;
import de.financetool.audit.EvidenceOrigin;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.ProfileFigures;
import de.financetool.profile.AssessmentFields;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.SourcedNumber;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The assessment card an {@code ASK_PROFILE} answer starts (UX-DR14): every missing field in one form, the values
 * derivable from transaction data pre-filled from Core results.
 */
@Component
class AssessmentCards {

    /** The fields pre-filled from account data. */
    static final Set<String> FROM_ACCOUNT = Set.of("net_income", "monthly_expenses", "emergency_fund");

    private final CoreGateway core;

    AssessmentCards(CoreGateway core) {
        this.core = core;
    }

    /**
     * The card for an {@code ASK_PROFILE} answer's missing fields; empty if the card can ask for none of them.
     * Account-derived fields carry their Core figure; a net income of zero is not pre-filled.
     */
    Optional<ChatMessage.Assessment> open(RequestContext context, List<String> missingFields) {
        List<String> keys = fields(missingFields);
        if (keys.isEmpty()) {
            return Optional.empty();
        }
        Map<String, SourcedNumber> prefill = keys.stream().anyMatch(FROM_ACCOUNT::contains)
                ? prefill(core.profileFigures(context, EvidenceOrigin.TURN)) : Map.of();
        List<ChatMessage.Assessment.Field> fields = keys.stream().map(key -> {
            SourcedNumber figure = prefill.get(key);
            return figure == null ? new ChatMessage.Assessment.Field(key, null, null)
                    : new ChatMessage.Assessment.Field(key, figure.value().toPlainString(), figure.provId());
        }).toList();
        return Optional.of(new ChatMessage.Assessment(fields, ChatMessage.Assessment.Status.OPEN));
    }

    /**
     * The card's fields for the classifier's missing fields, in catalog order: a single field as listed, a group
     * ({@code a or b}) by its first field the card can ask for, unless one of its fields is listed already.
     */
    static List<String> fields(List<String> missingFields) {
        List<String> out = new ArrayList<>();
        List<List<String>> groups = new ArrayList<>();
        for (String missing : missingFields) {
            List<String> group = List.of(missing.split(" or "));
            if (group.size() == 1) {
                if (AssessmentFields.askable(missing) && !out.contains(missing)) {
                    out.add(missing);
                }
            } else {
                groups.add(group);
            }
        }
        for (List<String> group : groups) {
            if (group.stream().noneMatch(out::contains)) {
                group.stream().filter(AssessmentFields::askable).findFirst().ifPresent(out::add);
            }
        }
        out.sort(Comparator.comparingInt(AssessmentFields::order));
        return out;
    }

    private static Map<String, SourcedNumber> prefill(ProfileFigures figures) {
        SourcedNumber income = figures.netIncome().number(CoreGateway.NET_INCOME);
        SourcedNumber expenses = figures.emergencyFund().number(CoreGateway.MONTHLY_EXPENSES);
        SourcedNumber fund = figures.emergencyFund().number(CoreGateway.EMERGENCY_FUND);
        return income.value().signum() > 0
                ? Map.of("net_income", income, "monthly_expenses", expenses, "emergency_fund", fund)
                : Map.of("monthly_expenses", expenses, "emergency_fund", fund);
    }
}
