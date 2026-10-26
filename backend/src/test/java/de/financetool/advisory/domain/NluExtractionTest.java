package de.financetool.advisory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.financetool.testing.PilotNlu;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Story 3.3: {@code normalize}, {@code ground} and the schema as ports of {@code nlu.py}, and the classification of
 * every recorded E1 pilot extraction against the experiment's own result for the same inputs (generated once with
 * {@code experiments/finlab} at {@code b72a70e}: registry without Knowledge Base, profile stub incomplete, nothing
 * computable).
 */
class NluExtractionTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void normalizeFillsEveryDefault() {
        NluExtraction d = NluExtraction.defaults();
        assertThat(d.requestType()).isEqualTo("other");
        assertThat(d.subject()).isEqualTo("self");
        assertThat(d.topic()).isEqualTo("other");
        assertThat(d.productCategories()).isEmpty();
        assertThat(d.assertedPremises()).isEmpty();
        assertThat(d.instructionOverrideAttempt()).isFalse();
        assertThat(d.marketTimingRequest()).isFalse();
        assertThat(d.statedProfile()).isEmpty();
        assertThat(d.computationParams()).isEmpty();
        assertThat(d.depersonalizedQuestion()).isEmpty();
    }

    @Test
    void normalizeDropsWhatIsNotInTheEnumsOrFields() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("request_type", "gossip");
        raw.put("subject", "generic");
        raw.put("topic", "savings_plan");
        raw.put("product_categories", List.of("etf", "unicorn", 7));
        raw.put("asserted_premises", List.of(Map.of("claim", "X exists", "kind", "law_or_regulation",
                "named_entity", "X"), "not a premise"));
        raw.put("instruction_override_attempt", "yes");
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("age", 41);
        profile.put("favourite_colour", "blue");
        profile.put("goal", "");
        profile.put("children", 0);
        profile.put("existing_contracts", List.of());
        raw.put("stated_profile", profile);
        raw.put("computation_params", Map.of("years", 25, "initial", "a lot", "monthly_contribution", 300.5));
        raw.put("depersonalized_question", null);

        NluExtraction n = NluExtraction.normalize(raw);
        assertThat(n.requestType()).isEqualTo("other");
        assertThat(n.subject()).isEqualTo("generic");
        assertThat(n.topic()).isEqualTo("savings_plan");
        assertThat(n.productCategories()).containsExactly("etf");
        assertThat(n.assertedPremises()).containsExactly(new Premise("X exists", "law_or_regulation", "X"));
        assertThat(n.instructionOverrideAttempt()).as("Python truthiness").isTrue();
        assertThat(n.statedProfile()).containsOnlyKeys("age", "children");
        assertThat(n.computationParams()).containsOnly(Map.entry("years", 25.0),
                Map.entry("monthly_contribution", 300.5));
        assertThat(n.depersonalizedQuestion()).isEmpty();
        assertThat(NluExtraction.normalize(n.toMap())).as("idempotent").isEqualTo(n);
    }

    static NluExtraction proposal(String requestType, String subject, List<String> categories,
            Map<String, Double> params) {
        return new NluExtraction(requestType, subject, "other", categories, List.of(), false, false, Map.of(), params,
                "");
    }

    @Test
    void aCategoryNeedsMatchingWordingAndUnambiguousWordingAddsOne() {
        NluExtraction.Grounded g = proposal("general_explanation", "generic", List.of("crypto", "etf"), Map.of())
                .ground("What is an ETF? And what about penny stocks?");
        assertThat(g.extraction().productCategories()).containsExactly("etf", "penny_stock");
        assertThat(g.notes()).containsExactly("dropped category 'crypto': no matching wording in the message",
                "added category 'penny_stock' from the message wording");
    }

    @Test
    void adviceOnAnOutOfScopeProductIsAPersonalRequest() {
        NluExtraction.Grounded g = proposal("general_explanation", "self", List.of("single_stock"), Map.of())
                .ground("Should I buy shares of one company?");
        assertThat(g.extraction().requestType()).isEqualTo("personal_recommendation");
    }

    @Test
    void aMessageAboutNobodyIsGeneric() {
        assertThat(proposal("general_explanation", "self", List.of(), Map.of()).ground("What is a TER?")
                .extraction().subject()).isEqualTo("generic");
        assertThat(proposal("general_explanation", "self", List.of(), Map.of()).ground("What is my TER?")
                .extraction().subject()).isEqualTo("self");
        assertThat(proposal("general_explanation", "specific_client", List.of(), Map.of())
                .ground("What would suit her?").extraction().subject()).isEqualTo("specific_client");
    }

    @Test
    void withdrawalFiguresAreTakenFromTheMessage() {
        NluExtraction.Grounded g = proposal("computation", "self", List.of(), Map.of()).ground(
                "I have a portfolio of 500,000 EUR and want to withdraw 2.000 EUR monthly at a 4 % annual return.");
        assertThat(g.extraction().computationParams()).containsEntry("monthly_withdrawal", 2000.0)
                .containsEntry("portfolio", 500000.0).containsEntry("annual_rate", 0.04);

        NluExtraction.Grounded fromInitial = proposal("computation", "self", List.of(),
                Map.of("initial", 80000.0)).ground("How long can I withdraw 500 EUR per month?");
        assertThat(fromInitial.extraction().computationParams()).containsEntry("portfolio", 80000.0);
    }

    /** Deviation from {@code nlu.py}: the portfolio pattern would take the withdrawal's own amount. */
    @Test
    void theMonthlyWithdrawalsAmountIsNeverThePortfolio() {
        NluExtraction.Grounded g = proposal("computation", "self", List.of(), Map.of()).ground(
                "How long will my savings last if I withdraw 200 EUR a month at a 3% return?");

        assertThat(g.extraction().computationParams()).containsEntry("monthly_withdrawal", 200.0)
                .doesNotContainKey("portfolio");

        NluExtraction.Grounded zeroInitial = proposal("computation", "self", List.of(),
                Map.of("initial", 0.0)).ground(
                "How long will my savings last if I withdraw 200 EUR a month at a 3% return?");
        assertThat(zeroInitial.extraction().computationParams()).doesNotContainKey("portfolio");
    }

    @Test
    void theSchemaIsTheExperimentsSchema() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/nlu/schema.json")) {
            Object expected = JSON.readValue(in, Object.class);
            Object actual = JSON.readValue(JSON.writeValueAsString(NluExtraction.schema()), Object.class);
            assertThat(actual).isEqualTo(expected);
            assertThat(JSON.writeValueAsString(NluExtraction.schema())).isEqualTo(JSON.writeValueAsString(expected));
        }
    }

    @Test
    void everyPilotExtractionIsClassifiedStablyAndAsTheExperimentClassifiesIt() {
        List<Map<String, Object>> expected = PilotNlu.jsonLines("/nlu/e1-llama3-pilot-expected.jsonl");
        List<Map<String, Object>> pilot = PilotNlu.jsonLines(PilotNlu.RESOURCE);
        assertThat(pilot).hasSize(65);
        assertThat(expected).hasSameSizeAs(pilot);
        List<MessageClass> firstRun = new ArrayList<>();
        for (int run = 0; run < 2; run++) {
            for (int i = 0; i < pilot.size(); i++) {
                Map<String, Object> item = pilot.get(i);
                Map<String, Object> want = expected.get(i);
                assertThat(want.get("item_id")).isEqualTo(item.get("item_id"));
                String message = (String) item.get("question");
                @SuppressWarnings("unchecked")
                NluExtraction raw = NluExtraction.normalize((Map<String, Object>) item.get("nlu"));
                NluExtraction.Grounded grounded = raw.ground(message);
                Classification c = Classifier.classify(grounded.extraction(), Map.of(),
                        ProfileCompleteness.INCOMPLETE, ClassifierTest.REGISTRY, false, message);
                String id = "item " + item.get("item_id");
                assertThat(c.messageClass().name()).as(id).isEqualTo(want.get("class"));
                assertThat(c.reasons()).as(id).isEqualTo(want.get("reasons"));
                assertThat(c.outOfScope()).as(id).isEqualTo(want.get("out_of_scope"));
                assertThat(c.unverifiedPremises().stream().map(p -> json(p.toMap())).toList()).as(id)
                        .isEqualTo(premises(want.get("unverified_premises")));
                assertThat(grounded.notes()).as(id).isEqualTo(want.get("notes"));
                assertThat(json(grounded.extraction().toMap())).as(id).isEqualTo(json(want.get("grounded")));
                if (run == 0) {
                    firstRun.add(c.messageClass());
                } else {
                    assertThat(c.messageClass()).as(id + " second run").isEqualTo(firstRun.get(i));
                }
            }
        }
    }

    /** The premise maps with only the keys the port keeps. */
    @SuppressWarnings("unchecked")
    static List<Object> premises(Object premises) {
        List<Object> out = new ArrayList<>();
        for (Object p : (List<Object>) premises) {
            Map<String, Object> m = new LinkedHashMap<>((Map<String, Object>) p);
            m.keySet().retainAll(List.of("claim", "kind", "named_entity", "description"));
            out.add(json(m));
        }
        return out;
    }

    /** A JSON value with every number as a double, for comparing Python ints with the port's doubles. */
    @SuppressWarnings("unchecked")
    static Object json(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), json(v)));
            return out;
        }
        if (value instanceof List<?> l) {
            return l.stream().map(NluExtractionTest::json).toList();
        }
        return value;
    }
}
