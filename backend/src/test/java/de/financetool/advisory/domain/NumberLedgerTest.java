package de.financetool.advisory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The per-turn ledger's sources and their order (Story 3.6). */
class NumberLedgerTest {

    @Test
    void aTurnsLedgerHoldsRuleParametersUserNumbersKeptPassagesAndWhatTheFilterShowsInThatOrder() {
        PromptPayload payload = new PromptPayload("I have 2.000 EUR", false,
                List.of(new PromptPayload.HistoryTurn("Earlier I said 750 EUR.", "Agent said 999.")),
                Map.of("income", Map.of("band", "3,000-4,000 EUR")), "",
                List.of(new PromptPayload.Passage("KID p. 1", "Costs are 0.2% a year."),
                        new PromptPayload.Passage("KID p. 2", "Dropped 42.")));
        Map<String, BigDecimal> parameters = new LinkedHashMap<>();
        parameters.put("emergency_fund_months_min", new BigDecimal("3"));

        NumberLedger ledger = NumberLedger.of("I have 2.000 EUR in 2026", payload, 0, 1, parameters);

        assertThat(ledger.entries()).extracting(NumberLedger.Entry::source).containsExactly(
                NumberLedger.Source.RULE_PARAMETER, NumberLedger.Source.USER, NumberLedger.Source.USER,
                NumberLedger.Source.USER, NumberLedger.Source.KB, NumberLedger.Source.KB,
                NumberLedger.Source.EXPOSURE, NumberLedger.Source.EXPOSURE, NumberLedger.Source.EXPOSURE);
        assertThat(ledger.values()).containsExactly(new BigDecimal("3"), new BigDecimal("2000"),
                new BigDecimal("2026"), new BigDecimal("750"), new BigDecimal("0.2"), new BigDecimal("0.002"),
                new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("4000"));
        assertThat(ledger.entries().getFirst().label()).isEqualTo("emergency_fund_months_min");
        assertThat(ledger.entries().get(4).label()).isEqualTo("KID p. 1");
        assertThat(ledger.values()).as("the agent's earlier answer and the dropped passage are not sources")
                .doesNotContain(new BigDecimal("999"), new BigDecimal("42"));
    }

    @Test
    void historyTurnsThePromptBudgetDroppedAreNotSources() {
        PromptPayload payload = new PromptPayload("q", false, List.of(new PromptPayload.HistoryTurn("I said 111.", "a"),
                new PromptPayload.HistoryTurn("I said 222.", "b")), Map.of("savings", 1.0E7), "", List.of());

        NumberLedger ledger = NumberLedger.of("q", payload, 1, 0, Map.of());

        assertThat(ledger.values()).contains(new BigDecimal("222")).doesNotContain(new BigDecimal("111"));
        assertThat(ledger.values()).as("a Double profile value in plain digits")
                .contains(new BigDecimal("10000000")).doesNotContain(new BigDecimal("7"));
    }

    @Test
    void withAddsWithoutChangingTheLedger() {
        NumberLedger core = NumberLedger.EMPTY.with(NumberLedger.Source.CORE, "prov-1", List.of(BigDecimal.ONE));
        assertThat(NumberLedger.EMPTY.entries()).isEmpty();
        assertThat(core.entries()).containsExactly(
                new NumberLedger.Entry(BigDecimal.ONE, NumberLedger.Source.CORE, "prov-1"));
    }
}
