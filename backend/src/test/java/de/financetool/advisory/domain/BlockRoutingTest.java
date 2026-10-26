package de.financetool.advisory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** FR-52 block routing, one test per row ({@code _route} plus invalid Rule Parameter), and the flags per level. */
class BlockRoutingTest {

    static Classification classification(MessageClass c, List<String> missing, List<String> oos) {
        return new Classification(c, List.of(), missing, List.of(), oos, "other");
    }

    static CheckResult failed(CheckId id, String code) {
        return new CheckResult(id, true, false, code, Map.of());
    }

    static final Classification REFUSE = classification(MessageClass.REFUSE_OUT_OF_SCOPE, List.of(),
            List.of("derivative_or_certificate", "crypto"));
    static final Classification ASK = classification(MessageClass.ASK_PROFILE, List.of(), List.of());
    static final Classification INFORM = classification(MessageClass.INFORM, List.of(), List.of());

    @Test
    void anInvalidRuleParameterEscalatesBeforeAnyOtherRow() {
        BlockRouting.Route r = BlockRouting.route(List.of(failed(CheckId.SCOPE, "x")), REFUSE, true);
        assertThat(r.outcome()).isEqualTo(ResponseOutcome.ESCALATE);
        assertThat(r.route()).isEqualTo("invalid_rule_parameter");
        assertThat(r.text()).isEqualTo(ResponseTemplates.ESCALATE);
        assertThat(BlockRouting.route(List.of(), INFORM, true).outcome()).isEqualTo(ResponseOutcome.ESCALATE);
    }

    @Test
    void aFailedScopeOnARefusalReleasesTheRefusalTemplate() {
        BlockRouting.Route r = BlockRouting.route(
                List.of(failed(CheckId.SCOPE, "refusal_or_alternative_missing"),
                        failed(CheckId.PROFILE_COMPLETE, "x")), REFUSE, false);
        assertThat(r.outcome()).isEqualTo(ResponseOutcome.REFUSE_OUT_OF_SCOPE);
        assertThat(r.route()).isEqualTo("block_scope");
        assertThat(r.fired()).containsExactly(CheckId.SCOPE, CheckId.PROFILE_COMPLETE);
        assertThat(r.text()).isEqualTo("This is outside what I can advise on: derivative or certificate, crypto are "
                + "not part of this service, because they carry risks that don't fit a basic advisory service for "
                + "everyday savers. What I can do is look at your emergency fund, your savings goals and a "
                + "diversified, low-cost ETF savings plan with you.");
        assertThat(ResponseTemplates.refuseOutOfScope(List.of())).contains(": these products are not part");
    }

    @Test
    void aFailedScopeOutsideARefusalEscalates() {
        BlockRouting.Route r = BlockRouting.route(List.of(failed(CheckId.SCOPE, "out_of_scope_recommendation")),
                INFORM, false);
        assertThat(r.outcome()).isEqualTo(ResponseOutcome.ESCALATE);
        assertThat(r.route()).isEqualTo("block_scope");
    }

    @Test
    void aFailedProfileCheckAsksForTheProfileWithTheDefaultOrTheMissingFields() {
        BlockRouting.Route r = BlockRouting.route(List.of(failed(CheckId.PROFILE_COMPLETE, "verdict_without_profile")),
                ASK, false);
        assertThat(r.outcome()).isEqualTo(ResponseOutcome.ASK_PROFILE);
        assertThat(r.route()).isEqualTo("block_profile");
        assertThat(r.fired()).containsExactly(CheckId.PROFILE_COMPLETE);
        assertThat(r.text()).isEqualTo("Before I can say whether this fits you, I need to know a bit more about your "
                + "situation. It takes about three minutes. I need: your goals, horizon, income and expenses, "
                + "emergency fund and risk tolerance. Could you tell me these? Then I'll tell you what fits your "
                + "situation and why.");
        Classification withMissing = classification(MessageClass.ASK_PROFILE, List.of("horizon", "risk"), List.of());
        assertThat(BlockRouting.route(List.of(failed(CheckId.PROFILE_COMPLETE, "x")), withMissing, false).text())
                .contains("I need: horizon, risk. Could");
    }

    @Test
    void onlyNoValidSourceReleasesTheNoValidSourceTemplate() {
        BlockRouting.Route r = BlockRouting.route(List.of(failed(CheckId.SOURCE_VALIDITY, "no_valid_source")), INFORM,
                false);
        assertThat(r.outcome()).isEqualTo(ResponseOutcome.INFORM);
        assertThat(r.route()).isEqualTo("no_valid_source");
        assertThat(r.text()).isEqualTo(NoValidSource.TEXT);
        assertThat(r.noValidSource()).isTrue();
        assertThat(r.fired()).containsExactly(CheckId.SOURCE_VALIDITY);

        BlockRouting.Route withOther = BlockRouting.route(List.of(failed(CheckId.SOURCE_VALIDITY, "no_valid_source"),
                failed(CheckId.NO_GUARANTEE_OR_TIMING, "guarantee_or_timing")), INFORM, false);
        assertThat(withOther.outcome()).isEqualTo(ResponseOutcome.ESCALATE);
        assertThat(withOther.route()).isEqualTo("block_no_guarantee_or_timing_source_validity");
        assertThat(withOther.noValidSource()).isFalse();

        BlockRouting.Route citation = BlockRouting.route(List.of(failed(CheckId.SOURCE_VALIDITY,
                "invalid_citation")), INFORM, false);
        assertThat(citation.outcome()).isEqualTo(ResponseOutcome.ESCALATE);
        assertThat(citation.route()).isEqualTo("block_source_validity");
    }

    @Test
    void aFailedNumberCheckEscalates() {
        BlockRouting.Route r = BlockRouting.route(List.of(failed(CheckId.NUMBER_MATCH, "number_mismatch")), INFORM,
                false);
        assertThat(r.outcome()).isEqualTo(ResponseOutcome.ESCALATE);
        assertThat(r.route()).isEqualTo("number_mismatch_after_regeneration");
        assertThat(r.text()).isEqualTo(ResponseTemplates.ESCALATE);
    }

    @Test
    void anyOtherFailureEscalatesAndNamesTheFiredChecksSortedByName() {
        BlockRouting.Route r = BlockRouting.route(List.of(failed(CheckId.NO_PRODUCT_NAMING, "product_named"),
                failed(CheckId.CONFLICT_OF_INTEREST, "x")), classification(MessageClass.RECOMMEND_CATEGORY,
                List.of(), List.of()), false);
        assertThat(r.outcome()).isEqualTo(ResponseOutcome.ESCALATE);
        assertThat(r.route()).isEqualTo("block_conflict_of_interest_no_product_naming");
        assertThat(r.fired()).containsExactly(CheckId.NO_PRODUCT_NAMING, CheckId.CONFLICT_OF_INTEREST);
        assertThat(r.text()).isEqualTo("Your question has been passed to a human advisor at your bank. You'll see "
                + "their answer here in the chat.");
    }

    @Test
    void noFailureReleasesTheDraft() {
        BlockRouting.Route r = BlockRouting.route(List.of(), REFUSE, false);
        assertThat(r.released()).isTrue();
        assertThat(r.outcome()).isNull();
        assertThat(r.text()).isNull();
        assertThat(r.route()).isEqualTo("released");
        assertThat(r.fired()).isEmpty();
    }

    @Test
    void componentFlagsPerLevel() {
        assertThat(ComponentFlags.of(ComponentFlags.Level.A))
                .isEqualTo(new ComponentFlags(ComponentFlags.RuleEngineMode.SHADOW, false, false));
        assertThat(ComponentFlags.of(ComponentFlags.Level.A_B))
                .isEqualTo(new ComponentFlags(ComponentFlags.RuleEngineMode.ENFORCING, false, true));
        assertThat(ComponentFlags.of(ComponentFlags.Level.A_B_C))
                .isEqualTo(new ComponentFlags(ComponentFlags.RuleEngineMode.ENFORCING, true, true));
        assertThat(ComponentFlags.of(ComponentFlags.Level.A).shadowMode()).isTrue();
        assertThat(ComponentFlags.of(ComponentFlags.Level.A_B).shadowMode()).isFalse();
    }

    @Test
    void theRegenerationFixIsTheExperimentsTargetedText() {
        assertThat(RegenerationFix.of(List.of(failed(CheckId.SOURCE_VALIDITY, "invalid_citation")), 2)).contains(
                "Base the explanation on the SOURCES and cite each source you use as [S1] to [S2]. If the SOURCES "
                        + "do not cover the question, say so. Rewrite the whole answer.");
        assertThat(RegenerationFix.of(List.of(failed(CheckId.SOURCE_VALIDITY, "no_valid_source")), 0)).isEmpty();
        assertThat(RegenerationFix.of(List.of(failed(CheckId.SCOPE, "x")), 0)).isEmpty();
        CheckResult numbers = new CheckResult(CheckId.NUMBER_MATCH, true, false, "number_mismatch",
                Map.of("mismatches", List.of(Map.of("text", "7.3%"), Map.of("text", "12"))));
        assertThat(RegenerationFix.of(List.of(numbers, failed(CheckId.SOURCE_VALIDITY, "missing_citation")), 1))
                .contains("Your draft used numbers that are not in TOOL RESULTS, SOURCES or the user's message: "
                        + "7.3%, 12. Use only the numbers from TOOL RESULTS, SOURCES or the user's message. Do not "
                        + "calculate. Base the explanation on the SOURCES and cite each source you use as [S1] to "
                        + "[S1]. If the SOURCES do not cover the question, say so. Rewrite the whole answer.");
    }

    @Test
    void numbersInTheUsersMessageParseBothFormatsAndSkipDatesAndReferences() {
        assertThat(NumberParser.numbersIn("I have 12.500,50 EUR and 1,000 EUR, about 7% since 3 March 2024, "
                + "see Article 25."))
                .containsExactly(new BigDecimal("12500.50"), new BigDecimal("1000"), new BigDecimal("7"),
                        new BigDecimal("0.07"), new BigDecimal("2024"));
        assertThat(NumberParser.numbersIn("2,5k and -3")).containsExactly(new BigDecimal("2500.0"),
                new BigDecimal("-3"));
        assertThat(NumberParser.parse("1. First\n2) second 40")).extracting(NumberParser.Found::text)
                .containsExactly("40");
    }
}
