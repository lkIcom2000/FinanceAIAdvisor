package de.financetool.advisory.application;

import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.CheckChain;
import de.financetool.advisory.domain.TurnState;
import de.financetool.audit.EvidenceOrigin;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.DemandsAndNeeds;
import de.financetool.core.domain.NeedsFigures;
import de.financetool.profile.AssessmentFields;
import de.financetool.profile.ProfileCompleteness;
import de.financetool.shared.RequestContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The demands-and-needs statement of a {@code RECOMMEND_CATEGORY} turn on a protection topic (Story 6.4, FR-18):
 * runs the Core's {@code demandsAndNeeds} on the effective Needs Profile and the contracts in the banking data, and
 * builds the statement and the figure chips from its result. No category is selected and no product named; the Core
 * decides everything.
 */
@Component
class NeedsStatements {

    /** The Needs Profile facts in plain words. */
    static final Map<String, String> FACT_LABELS = Map.of(
            "household", "Household",
            "dependants", "People who depend on your income",
            "occupation", "Occupation");

    private final CoreGateway core;

    NeedsStatements(CoreGateway core) {
        this.core = core;
    }

    /** Runs the statement on the stored Needs Profile with the stated facts of this turn (AD-23). */
    TurnState.Tools run(RequestContext context, Map<String, Object> stored, Map<String, Object> stated) {
        Map<String, Object> profile = ProfileCompleteness.effective(stored, stated);
        NeedsFigures needs = core.needs(context, EvidenceOrigin.TURN, profile);
        return TurnState.Tools.needsStated(needs, statement(needs));
    }

    /**
     * The Check Chain's view of the statement: the {@code demands_and_needs} record, whether every area is there and
     * whether household, dependants and occupation were collected before it ({@code IDD_NEEDS}).
     */
    static Map<String, Object> facts(NeedsFigures needs) {
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("category", CheckChain.DEMANDS_AND_NEEDS);
        category.put("conflicts", List.of());
        category.put(CheckChain.STATEMENT_COMPLETE, needs.statement().areas().size() == 3);
        category.put(CheckChain.NEEDS_FACTS_COLLECTED, needs.statement().factsCollected());
        return category;
    }

    /** The statement as the answer stores it: the areas, the Needs Profile facts used and the Core's figures. */
    static ChatMessage.NeedsStatement statement(NeedsFigures needs) {
        DemandsAndNeeds.Statement statement = needs.statement();
        List<ChatMessage.NeedsStatement.Area> areas = statement.areas().stream()
                .map(a -> new ChatMessage.NeedsStatement.Area(a.area(), a.need(), a.have(), a.check())).toList();
        List<ChatMessage.Recommendation.Fact> facts = new ArrayList<>();
        Map<String, Object> read = keyInputs(needs);
        for (String field : statement.profileFields()) {
            facts.add(new ChatMessage.Recommendation.Fact(FACT_LABELS.get(field), shown(field, read.get(field))));
        }
        return new ChatMessage.NeedsStatement(areas, facts, needs.result().numbers());
    }

    /** The audit view (FR-18): the areas and the Needs Profile fields the statement used. */
    static Map<String, Object> audit(NeedsFigures needs) {
        if (needs == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("areas", needs.statement().areas().stream().map(DemandsAndNeeds.Area::area).toList());
        out.put("profileFields", needs.statement().profileFields());
        out.put("factsCollected", needs.statement().factsCollected());
        return out;
    }

    /** The profile values the statement read, from its inputs (field label → text). */
    private static Map<String, Object> keyInputs(NeedsFigures needs) {
        Map<String, Object> out = new LinkedHashMap<>();
        needs.result().inputs().forEach(i -> {
            if (FACT_LABELS.containsKey(i.label())) {
                out.put(i.label(), i.text());
            }
        });
        return out;
    }

    /** A choice as the card shows it ("I live alone"), other values as they are. */
    static String shown(String field, Object value) {
        String text = String.valueOf(value);
        return AssessmentFields.of(field).flatMap(f -> f.options().stream().filter(o -> o.key().equals(text))
                .findFirst()).map(AssessmentFields.Option::label).orElse(text);
    }
}
