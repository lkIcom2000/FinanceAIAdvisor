package de.financetool.advisory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.financetool.advisory.domain.ExposureFilter.Level;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** FR-43: the Data Exposure Levels, one row per case (Story 3.2). */
class ExposureFilterTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            I'm 34 and have 12,400 EUR, IBAN DE89 3704 0044 0532 0130 00 | I'm 30s and have 10,000–15,000 EUR, IBAN [IBAN]
            IBAN DE89370400440532013000 please                            | IBAN [IBAN] please
            IBAN GB29 NWBK 6016 1331 9268 19                              | IBAN [IBAN]
            write to lena.weber@example.com today                         | write to [EMAIL] today
            Client Weber asked                                            | Client X asked
            Customer Schmidt and Mr. Smith met Mrs Jones and Ms Lee       | Customer X and Mr. X met Mrs X and Ms X
            I have 12.400 EUR                                             | I have 10,000–15,000 EUR
            I have 12.400,50 €                                            | I have 10,000–15,000 EUR
            I have 1.234,56 Euro                                          | I have 1,000–1,500 EUR
            I have 12,400.50 euros                                        | I have 10,000–15,000 EUR
            I have 12400EUR                                               | I have 10,000–15,000 EUR
            I pay 500 EUR a month                                         | I pay 500–600 EUR a month
            I pay 2,5 EUR fees                                            | I pay 0–100 EUR fees
            I have 50 EUR                                                 | I have 0–100 EUR
            I have 0 EUR                                                  | I have 0 EUR
            we own 250000 EUR                                             | we own 250,000–300,000 EUR
            aged 47                                                       | aged 40s
            Age: 29                                                       | Age: 20s
            I am 61 years old                                             | I am 60s years old
            I’m 52                                                        | I’m 50s
            What is an ETF savings plan?                                  | What is an ETF savings plan?
            I'm 12,400 EUR short                                          | I'm 10,000–15,000 EUR short
            I'm 34.                                                       | I'm 30s.
            I'm 345 days into the plan                                    | I'm 345 days into the plan
            I have EUR 12,400                                             | I have 10,000–15,000 EUR
            I have €12.400 left                                           | I have 10,000–15,000 EUR left
            I have € 12.400,50                                            | I have 10,000–15,000 EUR
            Mr. Müller called                                             | Mr. X called
            Client Özdemir asked                                          | Client X asked
            Mrs Weber-Schmidt asked                                       | Mrs X asked
            Mrs. Anna Weber asked                                         | Mrs. X asked
            Herr Müller und Frau Özdemir                                  | Herr X und Frau X
            Kunde Schmidt und Kundin Weber                                | Kunde X und Kundin X
            ich bin 34                                                    | ich bin 30s
            Ich bin 34 Jahre alt                                          | Ich bin 30s Jahre alt
            mit 34 Jahre alt                                              | mit 30s Jahre alt
            meine Tochter ist 12 Jahre alt                                | meine Tochter ist 10s Jahre alt
            für 15 Jahre sparen                                           | für 15 Jahre sparen
            Laufzeit 20 Jahre                                             | Laufzeit 20 Jahre
            he is 41 years old                                            | he is 40s years old
            seit 10 Jahren                                                | seit 10 Jahren
            de89-3704-0044-0532-0130-00 is mine                           | [IBAN] is mine
            de89 3704 0044 0532 0130 00                                   | [IBAN]
            """)
    void aggregatedMessages(String message, String shown) {
        assertThat(ExposureFilter.filterMessage(message, Level.AGGREGATED, null)).isEqualTo(shown);
    }

    @Test
    void noneWithholdsTheWholeMessageWithoutADepersonalisedQuestion() {
        String message = "I'm 34 and have 12,400 EUR, IBAN DE89 3704 0044 0532 0130 00";
        assertThat(ExposureFilter.filterMessage(message, Level.NONE, null)).isEqualTo(ExposureFilter.WITHHELD)
                .isEqualTo("[question withheld: no personal data may be shared at this exposure level]");
        assertThat(ExposureFilter.filterMessage(message, Level.NONE, "  ")).isEqualTo(ExposureFilter.WITHHELD);
        assertThat(ExposureFilter.filterMessage(message, Level.NONE, "How large should an emergency fund be?"))
                .isEqualTo("How large should an emergency fund be?");
    }

    @ParameterizedTest(name = "band({0}) = {1}")
    @CsvSource(delimiter = '|', textBlock = """
            0        | 0 EUR
            -5       | 0 EUR
            0.4      | 0–100 EUR
            99       | 0–100 EUR
            100      | 100–200 EUR
            999      | 900–1,000 EUR
            1000     | 1,000–1,500 EUR
            3200     | 3,000–3,500 EUR
            12400    | 10,000–15,000 EUR
            1250000  | 1,000,000–1,500,000 EUR
            -1250.5  | 0 EUR
            Infinity | [AMOUNT]
            NaN      | [AMOUNT]
            """)
    void bands(double value, String band) {
        assertThat(ExposureFilter.band(value)).isEqualTo(band);
    }

    @Test
    void anAmountTooLongForADoubleIsNotShown() {
        assertThat(ExposureFilter.filterMessage("I have " + "9".repeat(400) + " EUR", Level.AGGREGATED, null))
                .isEqualTo("I have [AMOUNT]");
    }

    static Map<String, Object> profile() {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("age", 34);
        profile.put("net_income_monthly", 3200);
        profile.put("emergency_fund", 0);
        profile.put("savings", 12400.5);
        profile.put("investment_horizon_years", 15);
        profile.put("children", 2);
        profile.put("risk_tolerance", "medium");
        profile.put("note", "I'm 34 with 12,400 EUR");
        profile.put("employer", null);
        profile.put("goals", List.of("house", "pension"));
        return profile;
    }

    @Test
    void aggregatedProfileNumbersBecomeBandsAndDecadesExceptTheKeptKeys() {
        assertThat(ExposureFilter.filterProfile(profile(), Level.AGGREGATED)).containsExactly(
                Map.entry("age", "30s"), Map.entry("net_income_monthly", "3,000–3,500 EUR"),
                Map.entry("emergency_fund", "0 EUR"), Map.entry("savings", "10,000–15,000 EUR"),
                Map.entry("investment_horizon_years", 15), Map.entry("children", 2),
                Map.entry("risk_tolerance", "medium"), Map.entry("note", "I'm 30s with 10,000–15,000 EUR"),
                new java.util.AbstractMap.SimpleEntry<>("employer", null),
                Map.entry("goals", List.of("house", "pension")));
    }

    @Test
    void noneHasNoProfileContext() {
        assertThat(ExposureFilter.filterProfile(profile(), Level.NONE)).isEmpty();
    }

    static final List<PromptPayload.HistoryTurn> HISTORY = List.of(
            new PromptPayload.HistoryTurn("I'm 34, mail me at a@b.de", "You have 12,400 EUR."),
            new PromptPayload.HistoryTurn("What is an ETF?", "A fund."));
    static final String CORE = "- WithdrawalDuration:\n    capital: 500000 EUR\n    full withdrawals: 522";
    static final List<PromptPayload.Passage> PASSAGES =
            List.of(new PromptPayload.Passage("ETFs (BaFin, version 1, valid 2026-01-01 to open, p. 1)", "Text."));

    @Test
    void aggregatedFiltersTheHistoryTextsAndPassesCoreResultsInFull() {
        PromptPayload payload = ExposureFilter.apply(Level.AGGREGATED, "I have 12,400 EUR", null, HISTORY,
                profile(), CORE, PASSAGES);

        assertThat(payload.message()).isEqualTo("I have 10,000–15,000 EUR");
        assertThat(payload.messageWithheld()).isFalse();
        assertThat(payload.history()).containsExactly(
                new PromptPayload.HistoryTurn("I'm 30s, mail me at [EMAIL]", "You have 10,000–15,000 EUR."),
                new PromptPayload.HistoryTurn("What is an ETF?", "A fund."));
        assertThat(payload.profile()).containsEntry("age", "30s");
        assertThat(payload.toolResults()).isEqualTo(CORE);
        assertThat(payload.passages()).isEqualTo(PASSAGES);
    }

    @Test
    void noneHasNoHistoryNoProfileAndOnlyTheWithheldMessageButTheCoreResultsInFull() {
        PromptPayload payload = ExposureFilter.apply(Level.NONE, "I have 12,400 EUR", null, HISTORY, profile(),
                CORE, PASSAGES);

        assertThat(payload.message()).isEqualTo(ExposureFilter.WITHHELD);
        assertThat(payload.messageWithheld()).isTrue();
        assertThat(payload.history()).isEmpty();
        assertThat(payload.profile()).isEmpty();
        assertThat(payload.toolResults()).isEqualTo(CORE);
    }

    @Test
    void nonePassesTheDepersonalisedQuestionAsItIsLikeTheExperiment() {
        PromptPayload payload = ExposureFilter.apply(Level.NONE, "q",
                "How long does a monthly withdrawal of 1200 EUR last on 150000 EUR at 4%?", List.of(), Map.of(), "",
                List.of());

        assertThat(payload.message())
                .isEqualTo("How long does a monthly withdrawal of 1200 EUR last on 150000 EUR at 4%?");
        assertThat(payload.messageWithheld()).isFalse();
    }

    @Test
    void noneWithADepersonalisedQuestionShowsOnlyThatQuestion() {
        PromptPayload payload = ExposureFilter.apply(Level.NONE, "I'm 34 and have 12,400 EUR, what now?",
                "What should someone in their 30s do with savings?", HISTORY, profile(), CORE, PASSAGES);

        assertThat(payload.message()).isEqualTo("What should someone in their 30s do with savings?");
        assertThat(payload.messageWithheld()).isFalse();
        assertThat(payload.history()).isEmpty();
        assertThat(payload.profile()).isEmpty();
        assertThat(payload.toolResults()).isEqualTo(CORE);
    }
}
