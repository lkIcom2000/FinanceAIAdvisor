package de.financetool.advisory.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The per-turn Evidence ledger of every sourced number (AD-2; {@code Evidence} of
 * {@code experiments/finlab/evidence.py}): a number in the draft must match one of these, or the Number Check fails.
 * Immutable; every {@code with} returns a new ledger.
 *
 * @param entries the sourced numbers, in the order they were added
 */
public record NumberLedger(List<Entry> entries) {

    public static final NumberLedger EMPTY = new NumberLedger(List.of());

    /** Where a sourced number comes from. */
    public enum Source {
        CORE, RULE_PARAMETER, USER, KB, EXPOSURE
    }

    /**
     * One sourced number.
     *
     * @param label the Core item's {@code provId}, the Rule Parameter's key or what the numbers were read from
     */
    public record Entry(BigDecimal value, Source source, String label) {

        public Entry {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(label, "label");
        }
    }

    public NumberLedger {
        entries = List.copyOf(entries);
    }

    /**
     * The ledger of a turn without Core results, in the experiment's order: Rule Parameters valid at the as-of date,
     * the user's message, the Knowledge Base passages in the prompt, then what the exposure filter shows. Deviation:
     * the user's earlier messages in the prompt history count as the user's own numbers too.
     *
     * @param droppedHistoryTurns how many of the payload's history turns the prompt budget dropped (oldest first)
     * @param passagesInPrompt how many of the payload's passages the prompt budget kept
     * @param ruleParameters the key and value of every Rule Parameter valid at the turn's as-of date
     */
    public static NumberLedger of(String userText, PromptPayload payload, int droppedHistoryTurns,
            int passagesInPrompt, Map<String, BigDecimal> ruleParameters) {
        NumberLedger ledger = EMPTY;
        for (Map.Entry<String, BigDecimal> parameter : ruleParameters.entrySet()) {
            ledger = ledger.with(Source.RULE_PARAMETER, parameter.getKey(), List.of(parameter.getValue()));
        }
        ledger = ledger.with(Source.USER, "numbers in the user's message", NumberParser.numbersIn(userText));
        List<PromptPayload.HistoryTurn> history = payload.history();
        for (PromptPayload.HistoryTurn turn : history.subList(Math.min(Math.max(0, droppedHistoryTurns),
                history.size()), history.size())) {
            ledger = ledger.with(Source.USER, "numbers in an earlier user message",
                    NumberParser.numbersIn(turn.userText()));
        }
        for (PromptPayload.Passage passage : payload.passages().subList(0,
                Math.min(passagesInPrompt, payload.passages().size()))) {
            ledger = ledger.with(Source.KB, passage.citation(), NumberParser.numbersIn(passage.text()));
        }
        return ledger.with(Source.EXPOSURE, "values shown after the exposure filter",
                NumberParser.numbersIn(payload.message() + " " + flatten(payload.profile())));
    }

    /** The ledger with {@code values} added under {@code source} and {@code label}. */
    public NumberLedger with(Source source, String label, List<BigDecimal> values) {
        List<Entry> all = new ArrayList<>(entries);
        values.forEach(v -> all.add(new Entry(v, source, label)));
        return new NumberLedger(all);
    }

    /** Every sourced value, for the Number Check. */
    public List<BigDecimal> values() {
        return entries.stream().map(Entry::value).toList();
    }

    /** The profile context's values as one text, so its numbers can be parsed (the experiment dumps it as JSON). */
    private static String flatten(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().map(NumberLedger::flatten).collect(Collectors.joining(" ; "));
        }
        if (value instanceof List<?> list) {
            return list.stream().map(NumberLedger::flatten).collect(Collectors.joining(" ; "));
        }
        if (value instanceof Number number) {
            // plain digits: a Double's "1.0E7" would parse as 1.0 and 7
            return new BigDecimal(number.toString()).toPlainString();
        }
        return value == null ? "" : value.toString();
    }
}
