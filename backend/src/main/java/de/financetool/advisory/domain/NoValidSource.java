package de.financetool.advisory.domain;

import java.util.List;
import java.util.Map;

/**
 * The fixed answer of an {@code INFORM} turn without a valid source (FR-13): {@code TEMPLATES["NO_VALID_SOURCE"]} of
 * {@code experiments/finlab/prompts.py}, verbatim, and one related in-scope question as a suggestion chip.
 */
public final class NoValidSource {

    public static final String TEXT = "I can't answer this reliably: there is no valid source on this topic in my "
            + "knowledge base for today's date, and I don't answer such questions from memory. A bank advisor can "
            + "help you with it, or you can ask me about a related topic I have sources for.";

    /** The German text (Story 13.1), written by hand. */
    static final String TEXT_DE = "Darauf kann ich nicht verlässlich antworten: Für das heutige Datum gibt es in "
            + "meiner Wissensbasis keine gültige Quelle zu diesem Thema, und solche Fragen beantworte ich nicht aus dem "
            + "Gedächtnis. Eine Beraterin oder ein Berater Ihrer Bank kann Ihnen dabei helfen, oder Sie fragen mich zu "
            + "einem verwandten Thema, zu dem ich Quellen habe.";

    public static final String DEFAULT_SUGGESTION = "What is an ETF savings plan?";
    static final String DEFAULT_SUGGESTION_DE = "Was ist ein ETF-Sparplan?";

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

    /** The German related questions, keyed like {@link #SUGGESTIONS}. */
    static final Map<String, String> SUGGESTIONS_DE = Map.ofEntries(
            Map.entry("liquidity_management", "Wie funktioniert ein Notgroschen?"),
            Map.entry("short_term_investment", "Wie funktioniert ein Notgroschen?"),
            Map.entry("shortfall_check", "Wie funktioniert ein Notgroschen?"),
            Map.entry("asset_allocation", "Warum ist Streuung wichtig?"),
            Map.entry("diversification", "Warum ist Streuung wichtig?"),
            Map.entry("cost_question", "Welche Kosten hat ein ETF?"),
            Map.entry("fund_selection", "Welche Kosten hat ein ETF?"),
            Map.entry("retirement_strategy", "Wie funktioniert die staatliche Altersvorsorge-Förderung?"),
            Map.entry("subsidy_eligibility", "Wie funktioniert die staatliche Altersvorsorge-Förderung?"),
            Map.entry("withdrawal_duration", "Wie funktioniert ein Entnahmeplan?"),
            Map.entry("protection_needs", "Welchen Versicherungsbedarf sollte ich prüfen?"),
            Map.entry("protection_duplicate", "Welchen Versicherungsbedarf sollte ich prüfen?"),
            Map.entry("contract_switch", "Welchen Versicherungsbedarf sollte ich prüfen?"));

    static final List<String> FALLBACKS_DE = List.of(DEFAULT_SUGGESTION_DE, "Wie funktioniert ein Notgroschen?",
            "Warum ist Streuung wichtig?");

    private NoValidSource() {
    }

    /** The fixed answer in the turn's language. */
    public static String text(Language language) {
        return language == Language.DE ? TEXT_DE : TEXT;
    }

    /** In-scope questions to fall back on, in order, when the related one is the question just asked. */
    static final List<String> FALLBACKS = List.of(DEFAULT_SUGGESTION, "How does an emergency fund work?",
            "Why does diversification matter?");

    /**
     * The related in-scope question for an NLU topic, never the question just asked (trimmed, ignoring case): then
     * the first other in-scope question.
     */
    public static String suggestion(String topic, String question) {
        return suggestion(topic, question, Language.EN);
    }

    /** Like {@link #suggestion(String, String)}, in the turn's language. */
    public static String suggestion(String topic, String question, Language language) {
        boolean de = language == Language.DE;
        String related = (de ? SUGGESTIONS_DE : SUGGESTIONS).getOrDefault(topic,
                de ? DEFAULT_SUGGESTION_DE : DEFAULT_SUGGESTION);
        if (!same(related, question)) {
            return related;
        }
        return (de ? FALLBACKS_DE : FALLBACKS).stream().filter(s -> !same(s, question)).findFirst().orElseThrow();
    }

    private static boolean same(String suggestion, String question) {
        return question != null && suggestion.strip().equalsIgnoreCase(question.strip());
    }
}
