package de.financetool.advisory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The fixed first line of a Category Recommendation (Story 6.3, UX-DR43) and the German texts (Story 13.1). */
class ResponseTemplatesTest {

    @Test
    void theLeadNamesTheConflictsInPlainWordsOrTheCategory() {
        assertThat(ResponseTemplates.recommendationLead("build the emergency fund first",
                List.of("emergency fund below target", "requested monthly amount exceeds monthly surplus")))
                .isEqualTo("This plan doesn't fit right now: the emergency fund is below its target; the monthly "
                        + "amount is more than what is left each month.");
        assertThat(ResponseTemplates.recommendationLead("a balanced ETF portfolio", List.of()))
                .isEqualTo("Recommended category: a balanced ETF portfolio.");
    }

    @Test
    void theLeadIsAddedOnceAndHoldsNoNumbersNorWordsTheSuitabilityCheckLooksFor() {
        String lead = ResponseTemplates.recommendationLead("x", List.of("emergency fund below target",
                "requested monthly amount exceeds monthly surplus"));
        String text = ResponseTemplates.withRecommendationLead("Because of your figures.", lead);
        assertThat(text).isEqualTo(lead + "\n\nBecause of your figures.");
        assertThat(ResponseTemplates.withRecommendationLead(text, lead)).isEqualTo(text);
        assertThat(lead).doesNotContainPattern("\\d").doesNotContain("your");
        assertThat(CheckChain.RISK.matcher(lead).find()).isFalse();
        for (String label : de.financetool.core.domain.CategorySelection.LABELS.values()) {
            String plain = ResponseTemplates.recommendationLead(label, List.of());
            assertThat(plain).as(label).doesNotContain("your");
            assertThat(CheckChain.RISK.matcher(plain).find()).as(label).isFalse();
            assertThat(CheckChain.COST.matcher(plain).find()).as(label).isFalse();
        }
    }

    @Test
    void theNeedsLeadNamesNoProductAndLeavesTheSuitabilityWordsToTheModel() {
        String lead = ResponseTemplates.NEEDS_LEAD;
        assertThat(lead).doesNotContainPattern("\\d").doesNotContain("your");
        assertThat(CheckChain.RISK.matcher(lead).find()).isFalse();
        assertThat(CheckChain.TARIFF.matcher(lead.toLowerCase(java.util.Locale.ROOT)).find()).isFalse();
        assertThat(CheckChain.INSURERS).noneMatch(n -> lead.toLowerCase(java.util.Locale.ROOT).contains(n + " "));
    }

    @Test
    void theGermanLeadsHoldNoNumbersNorWordsTheSuitabilityOrCostCheckLooksFor() {
        assertThat(ResponseTemplates.recommendationLead("x", "x", List.of("emergency fund below target",
                "requested monthly amount exceeds monthly surplus"), Language.DE))
                .isEqualTo("Dieser Plan passt gerade nicht: der Notgroschen liegt unter seinem Ziel; der Monatsbetrag "
                        + "ist höher als das, was jeden Monat übrig bleibt.");
        assertThat(ResponseTemplates.recommendationLead("balanced_etf_portfolio", "a balanced ETF portfolio",
                List.of(), Language.DE)).isEqualTo("Empfohlene Kategorie: ein ausgewogenes ETF-Portfolio.");
        assertThat(ResponseTemplates.recommendationLead("balanced_etf_portfolio", "a balanced ETF portfolio",
                List.of(), Language.EN)).isEqualTo("Recommended category: a balanced ETF portfolio.");
        assertThat(ResponseTemplates.CATEGORIES_DE).containsOnlyKeys(
                de.financetool.core.domain.CategorySelection.LABELS.keySet());
        List<String> leads = new java.util.ArrayList<>(ResponseTemplates.CATEGORIES_DE.keySet().stream()
                .map(c -> ResponseTemplates.recommendationLead(c, c, List.of(), Language.DE)).toList());
        leads.add(ResponseTemplates.needsLead(Language.DE));
        leads.add(ResponseTemplates.recommendationLead("x", "x", List.copyOf(ResponseTemplates.CONFLICTS_DE.keySet()),
                Language.DE));
        for (String lead : leads) {
            assertThat(lead).as(lead).doesNotContainPattern("\\d");
            assertThat(CheckChain.CUSTOMER_DE.matcher(lead).find()).as(lead).isFalse();
            assertThat(CheckChain.RISK.matcher(lead).find()).as(lead).isFalse();
            assertThat(CheckChain.COST.matcher(lead).find()).as(lead).isFalse();
            assertThat(CheckChain.TARIFF.matcher(lead.toLowerCase(java.util.Locale.ROOT)).find()).as(lead).isFalse();
        }
    }

    @Test
    void everyClassTextExistsInGerman() {
        Language de = Language.DE;
        assertThat(ResponseTemplates.escalate(de)).startsWith("Ihre Frage wurde")
                .isNotEqualTo(ResponseTemplates.escalate(Language.EN));
        assertThat(ResponseTemplates.escalate(Language.EN)).isEqualTo(ResponseTemplates.ESCALATE);
        assertThat(ResponseTemplates.refuseOutOfScope(List.of("building_society_savings"), de))
                .startsWith("Dazu kann ich nicht beraten. Nicht Teil dieses Service: Bausparverträge.");
        assertThat(ResponseTemplates.refuseOutOfScope(List.of("individual_tax_advice", "crypto"), de))
                .startsWith("Dazu kann ich nicht beraten. Nicht Teil dieses Service: individuelle Steuerberatung, "
                        + "Kryptowerte.");
        assertThat(ResponseTemplates.OUT_OF_SCOPE_DE).as("every out-of-scope category has German words")
                .containsOnlyKeys(Classifier.OUT_OF_SCOPE_CATEGORIES);
        assertThat(ResponseTemplates.refuseOutOfScope(List.of("building_society_savings"), Language.EN))
                .isEqualTo(ResponseTemplates.refuseOutOfScope(List.of("building_society_savings")));
        assertThat(ResponseTemplates.refusalSuggestions(de)).containsExactly("Meinen Notgroschen ansehen",
                "Mir beim Sparziel helfen");
        assertThat(ResponseTemplates.askProfile(List.of("investment_horizon_years", "risk_tolerance"), de))
                .startsWith(ResponseTemplates.whyAskProfile(de))
                .contains("Ich brauche: Anlagehorizont, Risikobereitschaft.");
        assertThat(ResponseTemplates.askProfile(List.of(), de)).contains(ResponseTemplates.DEFAULT_MISSING_DE);
        assertThat(ResponseTemplates.withProfileReason("Wie lange möchten Sie anlegen?", de))
                .isEqualTo(ResponseTemplates.WHY_ASK_PROFILE_DE + " Wie lange möchten Sie anlegen?");
        assertThat(ResponseTemplates.withLimitations("Text.", List.of("loss_aversion"), de))
                .isEqualTo("Text.\n\nDas beruht nur auf dem, was ich bisher weiß. Noch nicht bekannt: Sorge vor "
                        + "Verlusten.");
        assertThat(ResponseTemplates.parameterProposal(de)).startsWith("Das habe ich aus Ihrer Frage verstanden.");
        assertThat(ResponseTemplates.range(new java.math.BigDecimal("12345.678"), new java.math.BigDecimal("45000"),
                new java.math.BigDecimal("23456.7"), 25, de)).isEqualTo("Zwischen 12.345,68 und 45.000,00 EUR nach 25 "
                + "Jahren. Das mittlere Ergebnis sind 23.456,70 EUR. Das ist eine Spanne, kein Versprechen.");
        assertThat(ResponseTemplates.noValidSource(de)).startsWith("Darauf kann ich nicht verlässlich antworten");
        assertThat(NoValidSource.suggestion("cost_question", "x", de)).isEqualTo("Welche Kosten hat ein ETF?");
        assertThat(NoValidSource.suggestion("unknown", "Was ist ein ETF-Sparplan?", de))
                .isEqualTo("Wie funktioniert ein Notgroschen?");
        assertThat(ResponseTemplates.premiseCallout(List.of(), "x", de)).isEqualTo(
                ResponseTemplates.ASSUMPTION_CALLOUT_DE);
        String message = "Gilt das Sparbonusgesetz 2026 und ein Gesetz zur Rentenaufstockung für mich?";
        Premise named = new Premise("the law exists", "named_law", "Sparbonusgesetz 2026");
        Premise described = new Premise("a law exists", Premise.DESCRIBED_LAW, "", "Rentenaufstockung");
        assertThat(ResponseTemplates.premiseCallout(List.of(named), message, de))
                .isEqualTo("Ich konnte nicht bestätigen, dass \"Sparbonusgesetz 2026\" existiert.");
        assertThat(ResponseTemplates.premiseCallout(List.of(named, described), message, de))
                .isEqualTo("Ich konnte nicht bestätigen, dass \"Sparbonusgesetz 2026\" oder ein Gesetz oder eine "
                        + "Vorschrift zu Rentenaufstockung existiert.");
        assertThat(ResponseTemplates.premiseCallout(List.of(new Premise("returns are guaranteed", Premise.GUARANTEE,
                "")), "Garantiert mir der ETF 5 % pro Jahr?", de)).isEqualTo(ResponseTemplates.GUARANTEE_CALLOUT_DE);
        assertThat(ResponseTemplates.premiseCallout(List.of(new Premise("x", "named_law", "Unbekanntgesetz")),
                message, de)).as("a name not in the message is not quoted")
                .isEqualTo(ResponseTemplates.ASSUMPTION_CALLOUT_DE);
    }

    @Test
    void germanTemplatesPassTheGermanScopeCheck() {
        String refusal = ResponseTemplates.refuseOutOfScope(List.of("crypto"), Language.DE);
        assertThat(CheckChain.REFUSAL.matcher(refusal).find()).isTrue();
        assertThat(CheckChain.ALTERNATIVE.matcher(refusal).find()).isTrue();
        String ask = ResponseTemplates.askProfile(List.of("goal"), Language.DE);
        assertThat(CheckChain.ASK.matcher(ask).find()).isTrue();
        assertThat(CheckChain.VERDICT.matcher(ask).find()).isFalse();
    }
}
