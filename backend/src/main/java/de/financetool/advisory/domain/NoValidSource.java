package de.financetool.advisory.domain;

import java.util.Map;

/**
 * The fixed answer of an {@code INFORM} turn without a valid source (FR-13): {@code TEMPLATES["NO_VALID_SOURCE"]} of
 * {@code experiments/finlab/prompts.py}, verbatim, and one related in-scope question as a suggestion chip.
 */
public final class NoValidSource {

    public static final String TEXT = "I can't answer this reliably: there is no valid source on this topic in my "
            + "knowledge base for today's date, and I don't answer such questions from memory. A bank advisor can "
            + "help you with it, or you can ask me about a related topic I have sources for.";

    public static final String DEFAULT_SUGGESTION = "What is an ETF savings plan?";

    /** A related in-scope question per NLU topic; topics without one get {@link #DEFAULT_SUGGESTION}. */
    static final Map<String, String> SUGGESTIONS = Map.ofEntries(
            Map.entry("liquidity_management", "How does an emergency fund work?"),
            Map.entry("short_term_investment", "How does an emergency fund work?"),
            Map.entry("shortfall_check", "How does an emergency fund work?"),
            Map.entry("asset_allocation", "Why does diversification matter?"),
            Map.entry("diversification", "Why does diversification matter?"),
            Map.entry("cost_question", "What costs does an ETF have?"),
            Map.entry("fund_selection", "What costs does an ETF have?"),
            Map.entry("retirement_strategy", "How do state retirement subsidies work?"),
            Map.entry("subsidy_eligibility", "How do state retirement subsidies work?"),
            Map.entry("withdrawal_duration", "How does a withdrawal plan work?"),
            Map.entry("protection_needs", "Which insurance needs should I check?"),
            Map.entry("protection_duplicate", "Which insurance needs should I check?"),
            Map.entry("contract_switch", "Which insurance needs should I check?"));

    private NoValidSource() {
    }

    /** The related in-scope question for an NLU topic. */
    public static String suggestion(String topic) {
        return SUGGESTIONS.getOrDefault(topic, DEFAULT_SUGGESTION);
    }
}
