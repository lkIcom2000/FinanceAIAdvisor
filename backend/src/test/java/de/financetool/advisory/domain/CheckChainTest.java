package de.financetool.advisory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Check Chain against the experiment's own {@code run_checks}: {@code checks/golden.json} holds the results of
 * {@code experiments/finlab/checks.py} at {@code b72a70e} (run with {@code python -I}, retrieval on unless a case
 * says {@code "retrieval": false}) for the crafted cases of
 * {@code checks/cases.json}, which include the experiment's selftest cases. Every check id, applicability, pass,
 * reason code and details must match, except {@code IDD_NEEDS} (see {@link CheckChain#TARIFF}), tested on its own.
 */
class CheckChainTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final Set<CheckId> IMPLEMENTED = EnumSet.allOf(CheckId.class);

    record Case(String name, String text, MessageClass messageClass, String user, String topic, List<String> oos,
            boolean retrieval, int retrieved, Map<String, Object> category) {
    }

    /** The English cases of the golden comparison; the German ones (Story 13.1) are {@link #germanCases()}. */
    static List<Case> cases() {
        List<Map<String, Object>> raw = read("/checks/cases.json");
        return raw.stream().filter(c -> !c.containsKey("language")).map(c -> new Case((String) c.get("name"), (String) c.get("text"),
                MessageClass.valueOf((String) c.get("class")), (String) c.getOrDefault("user", ""),
                (String) c.getOrDefault("topic", "other"), list(c.get("oos")),
                (Boolean) c.getOrDefault("retrieval", true),
                ((Number) c.getOrDefault("retrieved", 0)).intValue(), map(c.get("category")))).toList();
    }

    @SuppressWarnings("unchecked")
    static List<String> list(Object value) {
        return value == null ? List.of() : (List<String>) value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    static <T> T read(String resource) {
        try (InputStream in = CheckChainTest.class.getResourceAsStream(resource)) {
            return JSON.readValue(in, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static ChainResult run(Case c) {
        return CheckChain.run(c.text(), new CheckChain.Facts(c.messageClass(), c.user(),
                c.messageClass() == MessageClass.RECOMMEND_CATEGORY, c.category(), c.topic(), c.oos(), c.retrieval(), c.retrieved(), List.of()));
    }

    static Stream<Arguments> goldenCases() {
        List<Map<String, Object>> golden = read("/checks/golden.json");
        List<Case> cases = cases();
        assertThat(golden).hasSameSizeAs(cases);
        return java.util.stream.IntStream.range(0, cases.size())
                .mapToObj(i -> Arguments.of(cases.get(i).name(), cases.get(i), golden.get(i)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    @SuppressWarnings("unchecked")
    void matchesTheExperimentsRunChecks(String name, Case c, Map<String, Object> golden) {
        assertThat(golden.get("name")).isEqualTo(name);
        ChainResult result = run(c);
        assertThat(result.results()).extracting(CheckResult::checkId).containsExactly(CheckId.values());
        for (Map<String, Object> expected : (List<Map<String, Object>>) golden.get("results")) {
            CheckId id = CheckId.valueOf((String) expected.get("check_id"));
            if (id == CheckId.IDD_NEEDS) {
                continue;
            }
            CheckResult actual = result.get(id);
            assertThat(actual.applicable()).as(id + " applicable").isEqualTo(expected.get("applicable"));
            assertThat(actual.passed()).as(id + " passed").isEqualTo(expected.get("passed"));
            assertThat(actual.reasonCode()).as(id + " reason").isEqualTo(expected.get("reason_code"));
            Map<String, Object> details = JSON.readValue(JSON.writeValueAsString(actual.details()),
                    new TypeReference<>() {
                    });
            assertThat(details).as(id + " details").isEqualTo(expected.get("details"));
        }
    }

    /**
     * Story 13.1: the German cases of {@code checks/cases.json} ({@code "language": "de"}), which the experiment never
     * ran, so {@code golden.json} has no entry for them; each names the expected reason code of the checks it is
     * about ({@code ""} = passed), with its own sourced values for the Number Check.
     */
    static Stream<Arguments> germanCases() {
        List<Map<String, Object>> raw = read("/checks/cases.json");
        return raw.stream().filter(c -> "de".equals(c.get("language"))).map(c -> Arguments.of(c.get("name"), c));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("germanCases")
    @SuppressWarnings("unchecked")
    void germanDraftsAreCheckedLikeEnglishOnes(String name, Map<String, Object> c) {
        MessageClass cls = MessageClass.valueOf((String) c.get("class"));
        List<java.math.BigDecimal> sourced = list(c.get("sourced")).stream().map(java.math.BigDecimal::new).toList();
        ChainResult result = CheckChain.run((String) c.get("text"), new CheckChain.Facts(cls,
                (String) c.getOrDefault("user", ""), cls == MessageClass.RECOMMEND_CATEGORY, map(c.get("category")),
                (String) c.getOrDefault("topic", "other"), list(c.get("oos")),
                (Boolean) c.getOrDefault("retrieval", true), ((Number) c.getOrDefault("retrieved", 0)).intValue(),
                sourced));
        ((Map<String, String>) c.get("expect")).forEach((id, reason) -> {
            CheckResult actual = result.get(CheckId.valueOf(id));
            assertThat(actual.applicable()).as(id + " applicable").isTrue();
            assertThat(actual.reasonCode()).as(id + " reason").isEqualTo(reason);
            assertThat(actual.passed()).as(id + " passed").isEqualTo(reason.isEmpty());
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyCheckThatCanFailOnWordingHasAFailingAndAPassingGermanCase() {
        List<Map<String, String>> expectations = germanCases()
                .map(a -> (Map<String, String>) ((Map<String, Object>) a.get()[1]).get("expect")).toList();
        for (CheckId id : IMPLEMENTED) {
            if (id == CheckId.DOCUMENTATION) { // structural: the Core selection, not the wording
                continue;
            }
            assertThat(expectations).as(id + " fails in German").anyMatch(e -> !e.getOrDefault(id.name(), "").isEmpty());
            assertThat(expectations).as(id + " passes in German").anyMatch(e -> "".equals(e.get(id.name())));
        }
    }

    @Test
    void everyImplementedCheckHasAPassingAndAFailingCase() {
        List<ChainResult> results = cases().stream().map(CheckChainTest::run).toList();
        for (CheckId id : IMPLEMENTED) {
            if (id != CheckId.DOCUMENTATION) { // cannot fail until a Core selection can be missing (Epic 6)
                assertThat(results).as(id + " fails somewhere").anyMatch(r -> r.get(id).failed());
            }
            assertThat(results).as(id + " passes applicably somewhere")
                    .anyMatch(r -> r.get(id).applicable() && r.get(id).passed());
        }
    }

    @Test
    void theNumberCheckAppliesToEveryTextAgainstTheSourcedValues() {
        CheckChain.Facts facts = new CheckChain.Facts(MessageClass.INFORM, "", false, null, "other", List.of(), false,
                0, List.of(new java.math.BigDecimal("522")));
        assertThat(CheckChain.run("You get 522 withdrawals.", facts).get(CheckId.NUMBER_MATCH).passed()).isTrue();
        CheckResult mismatch = CheckChain.run("You get 523 withdrawals.", facts).get(CheckId.NUMBER_MATCH);
        assertThat(mismatch.applicable()).isTrue();
        assertThat(mismatch.reasonCode()).isEqualTo("number_mismatch");
        assertThat(CheckChain.run("An ETF is a fund.", facts).get(CheckId.NUMBER_MATCH).passed()).isTrue();
    }

    static CheckResult idd(String text, String topic) {
        return CheckChain.run(text, new CheckChain.Facts(MessageClass.INFORM, "", false, null, topic, List.of(), true, 1, List.of()))
                .get(CheckId.IDD_NEEDS);
    }

    @Test
    void iddNeedsFailsWhenAProtectionAnswerNamesAnInsurerOrATariff() {
        assertThat(idd("Allianz offers good liability cover [S1].", "protection_needs"))
                .satisfies(r -> assertThat(r.failed()).isTrue())
                .extracting(CheckResult::reasonCode).isEqualTo("insurer_or_tariff_named");
        assertThat(idd("Take the tariff called Comfort.", "protection_duplicate").failed()).isTrue();
        assertThat(idd("R+V and HUK sell such cover.", "protection_needs").failed()).isTrue();
    }

    static ChainResult needsRecommendation(Map<String, Object> category) {
        return CheckChain.run("Your liability cover protects you against the risk of damage claims.",
                new CheckChain.Facts(MessageClass.RECOMMEND_CATEGORY, "", true, category, "protection_needs",
                        List.of(), false, 0, List.of()));
    }

    @Test
    void iddNeedsFailsAProtectionRecommendationMadeBeforeTheNeedsFactsWereCollected() {
        Map<String, Object> collected = Map.of("category", CheckChain.DEMANDS_AND_NEEDS, "conflicts", List.of(),
                CheckChain.STATEMENT_COMPLETE, true, CheckChain.NEEDS_FACTS_COLLECTED, true);
        assertThat(needsRecommendation(collected).get(CheckId.IDD_NEEDS).passed()).isTrue();
        assertThat(needsRecommendation(collected).get(CheckId.DOCUMENTATION).passed()).isTrue();
        assertThat(needsRecommendation(collected).get(CheckId.COST_TRANSPARENCY).applicable()).isFalse();

        Map<String, Object> missing = new java.util.HashMap<>(collected);
        missing.put(CheckChain.NEEDS_FACTS_COLLECTED, false);
        assertThat(needsRecommendation(missing).get(CheckId.IDD_NEEDS).reasonCode()).isEqualTo("needs_facts_missing");
        assertThat(needsRecommendation(null).get(CheckId.IDD_NEEDS).reasonCode()).isEqualTo("needs_facts_missing");
        assertThat(needsRecommendation(null).get(CheckId.DOCUMENTATION).reasonCode())
                .isEqualTo("no_core_selection_recorded");
        Map<String, Object> unknown = new java.util.HashMap<>(collected);
        unknown.remove(CheckChain.NEEDS_FACTS_COLLECTED);
        assertThat(needsRecommendation(unknown).get(CheckId.IDD_NEEDS).reasonCode()).isEqualTo("needs_facts_missing");
        assertThat(CheckChain.run("Your liability cover protects you against the risk of damage claims.",
                new CheckChain.Facts(MessageClass.RECOMMEND_CATEGORY, "", true, missing, "protection_duplicate",
                        List.of(), false, 0, List.of())).get(CheckId.IDD_NEEDS).reasonCode())
                .isEqualTo("needs_facts_missing");
        // naming an insurer still fails with its own code
        assertThat(CheckChain.run("Allianz covers your liability risk.", new CheckChain.Facts(
                MessageClass.RECOMMEND_CATEGORY, "", true, collected, "protection_needs", List.of(), false, 0,
                List.of())).get(CheckId.IDD_NEEDS).reasonCode()).isEqualTo("insurer_or_tariff_named");
    }

    @Test
    void iddNeedsPassesAtNeedsLevelAndOnlyAppliesToProtectionTopics() {
        assertThat(idd("Liability insurance covers damage you cause to others.", "protection_needs"))
                .satisfies(r -> assertThat(r.applicable()).isTrue()).satisfies(r -> assertThat(r.passed()).isTrue());
        assertThat(idd("Tariffs differ; compare what you need first.", "protection_needs").passed()).isTrue();
        // word boundaries: "ergonomic" and "taxable" contain insurer names but name none
        assertThat(idd("An ergonomic desk is taxable.", "protection_needs").passed()).isTrue();
        assertThat(idd("Allianz is a large insurer.", "other").applicable()).isFalse();
    }

    @Test
    void sourceValidityChecksCitationsAgainstThePassagesInThePrompt() {
        CheckChain.Facts droppedByBudget =
                new CheckChain.Facts(MessageClass.INFORM, "q", false, null, "other", List.of(), true, 1, List.of());
        assertThat(CheckChain.run("Answer [S2].", droppedByBudget).get(CheckId.SOURCE_VALIDITY).reasonCode())
                .isEqualTo("invalid_citation");
        assertThat(CheckChain.run("Answer [S1].", droppedByBudget).get(CheckId.SOURCE_VALIDITY).passed()).isTrue();
        CheckChain.Facts none = new CheckChain.Facts(MessageClass.INFORM, "q", false, null, "other", List.of(), true, 0, List.of());
        assertThat(CheckChain.run("Answer [S1].", none).get(CheckId.SOURCE_VALIDITY).reasonCode())
                .isEqualTo("no_valid_source");
        CheckChain.Facts off = new CheckChain.Facts(MessageClass.INFORM, "q", false, null, "other", List.of(), false, 0, List.of());
        assertThat(CheckChain.run("Answer.", off).get(CheckId.SOURCE_VALIDITY))
                .isEqualTo(CheckResult.notApplicable(CheckId.SOURCE_VALIDITY));
    }

    static CheckResult documentation(Map<String, Object> category) {
        return CheckChain.run("A broad equity ETF fits.", new CheckChain.Facts(MessageClass.RECOMMEND_CATEGORY, "",
                true, category, "savings_plan", List.of(), false, 0, List.of())).get(CheckId.DOCUMENTATION);
    }

    @Test
    void documentationFailsWithoutACoreSelectionOrWithAnIncompleteStatement() {
        assertThat(documentation(null)).satisfies(r -> assertThat(r.applicable()).isTrue())
                .satisfies(r -> assertThat(r.passed()).isFalse())
                .satisfies(r -> assertThat(r.reasonCode()).isEqualTo("no_core_selection_recorded"));
        assertThat(documentation(Map.of("category", "equity_etf_broad")))
                .satisfies(r -> assertThat(r.applicable()).isTrue()).satisfies(r -> assertThat(r.passed()).isTrue());
        assertThat(documentation(Map.of("category", "equity_etf_broad", CheckChain.STATEMENT_COMPLETE, true))
                .passed()).isTrue();
        assertThat(documentation(Map.of("category", "equity_etf_broad", CheckChain.STATEMENT_COMPLETE, false)))
                .satisfies(r -> assertThat(r.passed()).isFalse())
                .satisfies(r -> assertThat(r.reasonCode()).isEqualTo("statement_incomplete"));
        assertThat(CheckChain.run("An ETF is a fund.", new CheckChain.Facts(MessageClass.INFORM, "", false,
                Map.of("category", "x"), "other", List.of(), false, 0, List.of())).get(CheckId.DOCUMENTATION).applicable())
                .as("only recommendations").isFalse();
    }

    @Test
    void negativePassageCountsAreClampedToNone() {
        assertThat(new CheckChain.Facts(MessageClass.INFORM, "", false, null, "other", List.of(), true, -2, List.of())
                .passagesInPrompt()).isZero();
    }

    @Test
    void citationMarkersAreRemovedWithTheirLeadingSpaceAndCounted() {
        assertThat(CitationMarkers.strip("An ETF is a fund [S1]. It trades daily[S2][S3]."))
                .isEqualTo(new CitationMarkers.Stripped("An ETF is a fund. It trades daily.", 3));
        assertThat(CitationMarkers.strip("No markers, [S] or [s1] here."))
                .isEqualTo(new CitationMarkers.Stripped("No markers, [S] or [s1] here.", 0));
    }

    @Test
    void sameTextSameResults() {
        for (Case c : cases()) {
            assertThat(run(c)).isEqualTo(run(c));
        }
    }
}
