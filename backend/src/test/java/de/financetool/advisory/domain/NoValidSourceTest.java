package de.financetool.advisory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Story 3.3 review: the no-valid-source chip is related to the topic and never the question just asked. */
class NoValidSourceTest {

    @Test
    void aMappedTopicGetsItsMappedChip() {
        assertThat(NoValidSource.suggestion("liquidity_management", "Where should I keep my reserve?"))
                .isEqualTo("How does an emergency fund work?");
        assertThat(NoValidSource.suggestion("tax_question", "How are ETFs taxed?"))
                .isEqualTo(NoValidSource.DEFAULT_SUGGESTION);
    }

    @Test
    void theChipIsNeverTheQuestionJustAsked() {
        String chip = NoValidSource.suggestion("savings_plan", "  what is an ETF SAVINGS plan?  ");
        assertThat(chip).isNotEqualToIgnoringCase(NoValidSource.DEFAULT_SUGGESTION)
                .isEqualTo("How does an emergency fund work?");
        assertThat(NoValidSource.suggestion("liquidity_management", "How does an emergency fund work?"))
                .isEqualTo(NoValidSource.DEFAULT_SUGGESTION);
        for (String fallback : NoValidSource.FALLBACKS) {
            assertThat(NoValidSource.suggestion("other", fallback)).isNotEqualToIgnoringCase(fallback);
        }
    }
}
