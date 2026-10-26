package de.financetool.advisory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The fixed first line of a Category Recommendation (Story 6.3, UX-DR43). */
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
}
