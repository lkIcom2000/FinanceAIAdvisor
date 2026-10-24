package de.financetool.advisory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** UX-DR26: the plain reason class comes from the route only; other routes show none. */
class HandoverReasonTest {

    @Test
    void onlyTheRuleParameterAndTheNumberRoutesHaveAPlainReasonClass() {
        assertThat(HandoverReason.of("invalid_rule_parameter")).contains(HandoverReason.RULE_NOT_CONFIRMED);
        assertThat(HandoverReason.of("number_mismatch_after_regeneration"))
                .contains(HandoverReason.FIGURES_NOT_CONFIRMED);
        assertThat(HandoverReason.of("block_no_product_naming")).isEmpty();
        assertThat(HandoverReason.of("block_source_validity")).isEmpty();
    }

    @Test
    void aStoredNameMapsBackToItsPhraseAndAnUnknownOneToNone() {
        assertThat(HandoverReason.textOf("FIGURES_NOT_CONFIRMED"))
                .isEqualTo("because I couldn't confirm every figure in my answer");
        assertThat(HandoverReason.textOf(null)).isNull();
        assertThat(HandoverReason.textOf("SOMETHING_ELSE")).isNull();
    }
}
