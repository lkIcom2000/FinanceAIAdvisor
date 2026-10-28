package de.financetool.advisory.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The fixed, pre-approved answers block routing releases instead of a draft: {@code TEMPLATES} of
 * {@code experiments/finlab/prompts.py}, verbatim, except {@code ESCALATE}: the handover card's text (UX-DR26,
 * Story 3.5). Since Story 13.1 every user-facing text also exists in German, written by hand (never translated by a
 * model) and chosen by the turn's {@link Language}; the English texts are unchanged.
 */
public final class ResponseTemplates {

    public static final String ESCALATE = "Your question has been passed to a human advisor at your bank. You'll see "
            + "their answer here in the chat.";
    static final String ESCALATE_DE = "Ihre Frage wurde an eine Beraterin oder einen Berater Ihrer Bank weitergegeben. "
            + "Die Antwort sehen Sie hier im Chat.";

    static final String REFUSE_OUT_OF_SCOPE = "This is outside what I can advise on: {oos} are not part of this "
            + "service, because they carry risks that don't fit a basic advisory service for everyday savers. What I "
            + "can do is look at your emergency fund, your savings goals and a diversified, low-cost ETF savings plan "
            + "with you.";
    static final String REFUSE_OUT_OF_SCOPE_DE = "Dazu kann ich nicht beraten. Nicht Teil dieses Service: {oos}. Diese "
            + "Themen tragen Risiken, die nicht zu einer Basisberatung für Alltagssparer passen. Was ich stattdessen "
            + "mit Ihnen tun kann: Ihren Notgroschen, Ihre Sparziele und einen breit gestreuten, kostengünstigen "
            + "ETF-Sparplan ansehen.";

    /** Why an {@code ASK_PROFILE} answer asks (Story 6.1): every such answer starts with it. */
    public static final String WHY_ASK_PROFILE = "Before I can say whether this fits you, I need to know a bit more "
            + "about your situation. It takes about three minutes.";
    static final String WHY_ASK_PROFILE_DE = "Bevor ich sagen kann, ob das zu Ihnen passt, muss ich etwas mehr über "
            + "Ihre Situation wissen. Das dauert etwa drei Minuten.";

    static final String ASK_PROFILE = WHY_ASK_PROFILE + " I need: {missing}. Could you tell me these? Then I'll tell "
            + "you what fits your situation and why.";
    static final String ASK_PROFILE_DE = WHY_ASK_PROFILE_DE + " Ich brauche: {missing}. Können Sie mir das sagen? "
            + "Dann sage ich Ihnen, was zu Ihrer Situation passt und warum.";

    /** The disclosed limitations of a recommendation on a profile with strict FR-17 fields still unknown (AD-23). */
    static final String LIMITATIONS = "This is based only on what I know so far. Not known yet: {fields}.";
    static final String LIMITATIONS_DE = "Das beruht nur auf dem, was ich bisher weiß. Noch nicht bekannt: {fields}.";

    /** The default missing-fields text of {@code _route}. */
    static final String DEFAULT_MISSING = "your goals, horizon, income and expenses, emergency fund and risk tolerance";
    static final String DEFAULT_MISSING_DE = "Ihre Ziele, Ihr Anlagehorizont, Einkommen und Ausgaben, Notgroschen und "
            + "Risikobereitschaft";

    /** The text of a parameter proposal card (AD-24): no figures, the card's rows carry them. */
    public static final String PARAMETER_PROPOSAL = "Here's what I understood from your question. Please check the "
            + "values below, then choose Calculate, or change them first.";
    /** The German proposal text; the button keeps its English caption (no UI translation). */
    static final String PARAMETER_PROPOSAL_DE = "Das habe ich aus Ihrer Frage verstanden. Bitte prüfen Sie die Werte "
            + "unten und wählen Sie dann Calculate, oder ändern Sie sie vorher.";

    /** The answer of a calculated what-if (Story 7.1, UX-DR17): fixed, no phrasing call; the card shows the chart. */
    static final String RANGE = "Between {p10} and {p90} EUR after {years} years. The middle outcome is {p50} EUR. "
            + "This is a range, not a promise.";
    static final String RANGE_DE = "Zwischen {p10} und {p90} EUR nach {years} Jahren. Das mittlere Ergebnis sind "
            + "{p50} EUR. Das ist eine Spanne, kein Versprechen.";

    public static final String NO_VALID_SOURCE = NoValidSource.TEXT;

    /** The in-scope next steps of every released refusal (UX-DR11), as suggestion chips. */
    public static final List<String> REFUSAL_SUGGESTIONS = List.of("Look at my emergency fund",
            "Help me set a savings goal");
    static final List<String> REFUSAL_SUGGESTIONS_DE = List.of("Meinen Notgroschen ansehen",
            "Mir beim Sparziel helfen");

    /** The callout of a trap whose only unverified premise is a guarantee without a name. */
    static final String GUARANTEE_CALLOUT = "I couldn't confirm the guarantee your question assumes.";
    static final String GUARANTEE_CALLOUT_DE = "Ich konnte die Garantie, von der Ihre Frage ausgeht, nicht bestätigen.";
    /** The callout when no premise can be named in the message's own words. */
    static final String ASSUMPTION_CALLOUT = "I couldn't confirm something your question assumes.";
    static final String ASSUMPTION_CALLOUT_DE = "Ich konnte etwas, von dem Ihre Frage ausgeht, nicht bestätigen.";

    /** The out-of-scope categories in German plain words; others keep their key with spaces. */
    static final Map<String, String> OUT_OF_SCOPE_DE = Map.ofEntries(
            Map.entry("single_stock", "Einzelaktien"),
            Map.entry("penny_stock", "Pennystocks"),
            Map.entry("derivative_or_certificate", "Derivate und Zertifikate"),
            Map.entry("crypto", "Kryptowerte"),
            Map.entry("forex_or_cfd", "Devisen- und CFD-Handel"),
            Map.entry("building_society_savings", "Bausparverträge"),
            Map.entry("insurance_tariff_selection", "die Auswahl von Versicherungstarifen"),
            Map.entry("individual_tax_advice", "individuelle Steuerberatung"),
            Map.entry("single_bond", "einzelne Anleihen"),
            Map.entry("legal_assessment", "rechtliche Prüfungen"));

    /** Profile fields in German plain words, for the profile question and the limitations sentence. */
    static final Map<String, String> FIELDS_DE = Map.ofEntries(
            Map.entry("age", "Alter"),
            Map.entry("occupation", "Beruf"),
            Map.entry("employment_status", "Beschäftigung"),
            Map.entry("net_income", "Nettoeinkommen"),
            Map.entry("gross_income", "Bruttoeinkommen"),
            Map.entry("monthly_expenses", "monatliche Ausgaben"),
            Map.entry("monthly_surplus", "monatlicher Überschuss"),
            Map.entry("emergency_fund", "Notgroschen"),
            Map.entry("current_balance", "aktueller Kontostand"),
            Map.entry("risk_tolerance", "Risikobereitschaft"),
            Map.entry("loss_aversion", "Sorge vor Verlusten"),
            Map.entry("loss_bearing_capacity", "Verlusttragfähigkeit"),
            Map.entry("investment_horizon_years", "Anlagehorizont"),
            Map.entry("goal", "Ziel"),
            Map.entry("knowledge_experience", "Kenntnisse und Erfahrungen"),
            Map.entry("sustainability_preference", "Nachhaltigkeitspräferenz"),
            Map.entry("current_holdings", "bestehende Anlagen"),
            Map.entry("tax_allowance_unused", "ungenutzter Sparerpauschbetrag"),
            Map.entry("pension_situation", "Altersvorsorge"),
            Map.entry("children", "Kinder"),
            Map.entry("household", "Haushalt"),
            Map.entry("dependants", "Personen, die von Ihrem Einkommen abhängen"),
            Map.entry("existing_contracts", "bestehende Verträge"),
            Map.entry("existing_contract_details", "Details zu bestehenden Verträgen"),
            Map.entry("amount", "Betrag"),
            Map.entry("requested_monthly_amount", "gewünschter Monatsbetrag"));

    /** The Core's categories in German plain words (the English labels are the Core's own). */
    static final Map<String, String> CATEGORIES_DE = Map.ofEntries(
            Map.entry("review_duplicate_cover", "doppelt bestehenden Versicherungsschutz prüfen"),
            Map.entry("no_credit_financed_investing", "keine Geldanlage mit geliehenem Geld"),
            Map.entry("distributing_or_accumulating_with_unused_allowance",
                    "ein ausschüttender oder thesaurierender Fonds, der den ungenutzten Sparerpauschbetrag nutzt"),
            Map.entry("sustainable_diversified_etf", "ein nachhaltiger, breit gestreuter ETF"),
            Map.entry("broad_world_equity_etf_for_diversification", "ein breiter Welt-Aktien-ETF zur Streuung"),
            Map.entry("short_term_deposit_or_money_market", "eine kurzfristige Einlage oder ein Geldmarktfonds"),
            Map.entry("build_emergency_fund_first", "zuerst den Notgroschen aufbauen"),
            Map.entry("deposit_or_low_risk_mix", "Einlagen oder eine vorsichtige Mischung"),
            Map.entry("diversified_low_cost_world_equity_etf_savings_plan",
                    "ein Sparplan in einem breiten, günstigen Welt-Aktien-ETF"),
            Map.entry("balanced_etf_portfolio", "ein ausgewogenes ETF-Portfolio"));

    private ResponseTemplates() {
    }

    /** The handover text of an escalated turn in the turn's language. */
    public static String escalate(Language language) {
        return language == Language.DE ? ESCALATE_DE : ESCALATE;
    }

    /** The no-valid-source text in the turn's language. */
    public static String noValidSource(Language language) {
        return NoValidSource.text(language);
    }

    /** The parameter proposal text in the turn's language. */
    public static String parameterProposal(Language language) {
        return language == Language.DE ? PARAMETER_PROPOSAL_DE : PARAMETER_PROPOSAL;
    }

    /** The refusal chips in the turn's language. */
    public static List<String> refusalSuggestions(Language language) {
        return language == Language.DE ? REFUSAL_SUGGESTIONS_DE : REFUSAL_SUGGESTIONS;
    }

    /** Why an {@code ASK_PROFILE} answer asks, in the turn's language. */
    public static String whyAskProfile(Language language) {
        return language == Language.DE ? WHY_ASK_PROFILE_DE : WHY_ASK_PROFILE;
    }

    /** The answer of a calculated what-if: the Core's low end, high end and middle outcome at the horizon. */
    public static String range(BigDecimal p10, BigDecimal p90, BigDecimal p50, int years) {
        return range(p10, p90, p50, years, Language.EN);
    }

    /** Like {@link #range(BigDecimal, BigDecimal, BigDecimal, int)}; German amounts read "12.345,67". */
    public static String range(BigDecimal p10, BigDecimal p90, BigDecimal p50, int years, Language language) {
        Locale locale = language == Language.DE ? Locale.GERMAN : Locale.ENGLISH;
        return (language == Language.DE ? RANGE_DE : RANGE).replace("{p10}", amount(p10, locale))
                .replace("{p90}", amount(p90, locale)).replace("{p50}", amount(p50, locale))
                .replace("{years}", Integer.toString(years));
    }

    /** An amount as the Core shows it, with thousands separators and cents ("12,345.67", German "12.345,67"). */
    private static String amount(BigDecimal value, Locale locale) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(locale));
        return format.format(value.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * The premise callout of a {@code DETECT_TRAP} card: one sentence naming the unverified premises in the message's
     * own words (named entities, described laws that occur in the message), never details about them.
     *
     * @param message the user's message; a name the NLU proposed that is not in it is not quoted
     */
    public static String premiseCallout(List<Premise> unverified, String message) {
        return premiseCallout(unverified, message, Language.EN);
    }

    /** Like {@link #premiseCallout(List, String)}, in the turn's language. */
    public static String premiseCallout(List<Premise> unverified, String message, Language language) {
        boolean de = language == Language.DE;
        String low = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        List<String> parts = new java.util.ArrayList<>();
        for (Premise p : unverified) {
            String words = p.kind().equals(Premise.DESCRIBED_LAW)
                    ? (p.description() == null ? "" : p.description().strip()) : p.namedEntity().strip();
            if (words.isEmpty() || !low.contains(words.toLowerCase(java.util.Locale.ROOT))) {
                continue;
            }
            String part = p.kind().equals(Premise.DESCRIBED_LAW)
                    ? (de ? "ein Gesetz oder eine Vorschrift zu " : "a law or regulation on ") + words
                    : "\"" + words + "\"";
            if (!parts.contains(part)) {
                parts.add(part);
            }
        }
        if (!parts.isEmpty()) {
            return de ? "Ich konnte nicht bestätigen, dass " + String.join(" oder ", parts) + " existiert."
                    : "I couldn't confirm that " + String.join(" or ", parts) + " exists.";
        }
        boolean guarantee = unverified.stream().anyMatch(p -> p.kind().equals(Premise.GUARANTEE));
        return de ? (guarantee ? GUARANTEE_CALLOUT_DE : ASSUMPTION_CALLOUT_DE)
                : guarantee ? GUARANTEE_CALLOUT : ASSUMPTION_CALLOUT;
    }

    /** The refusal for the out-of-scope categories ({@code ", ".join(oos).replace("_", " ") or "these products"}). */
    public static String refuseOutOfScope(List<String> outOfScope) {
        return refuseOutOfScope(outOfScope, Language.EN);
    }

    /** Like {@link #refuseOutOfScope(List)}, in the turn's language. */
    public static String refuseOutOfScope(List<String> outOfScope, Language language) {
        if (language == Language.DE) {
            String oos = String.join(", ", outOfScope.stream()
                    .map(c -> OUT_OF_SCOPE_DE.getOrDefault(c, c.replace("_", " "))).toList());
            return REFUSE_OUT_OF_SCOPE_DE.replace("{oos}", oos.isEmpty() ? "diese Produkte" : oos);
        }
        String oos = String.join(", ", outOfScope).replace("_", " ");
        return REFUSE_OUT_OF_SCOPE.replace("{oos}", oos.isEmpty() ? "these products" : oos);
    }

    /** An {@code ASK_PROFILE} draft that starts with {@link #WHY_ASK_PROFILE}. */
    public static String withProfileReason(String draft) {
        return withProfileReason(draft, Language.EN);
    }

    /** Like {@link #withProfileReason(String)}, with the sentence in the turn's language. */
    public static String withProfileReason(String draft, Language language) {
        String why = whyAskProfile(language);
        String text = draft.strip();
        return text.startsWith(why) ? text : (why + " " + text).strip();
    }

    /**
     * A recommendation draft that ends with the limitations sentence; unchanged without limitations or when it
     * already ends with it (a regeneration may repeat the previous answer).
     */
    public static String withLimitations(String draft, List<String> limitations) {
        return withLimitations(draft, limitations, Language.EN);
    }

    /** Like {@link #withLimitations(String, List)}, with the sentence in the turn's language. */
    public static String withLimitations(String draft, List<String> limitations, Language language) {
        if (limitations.isEmpty()) {
            return draft;
        }
        String sentence = language == Language.DE ? LIMITATIONS_DE.replace("{fields}", fields(limitations))
                : LIMITATIONS.replace("{fields}", String.join(", ", limitations).replace('_', ' '));
        String text = draft.strip();
        return text.endsWith(sentence) ? text : text + "\n\n" + sentence;
    }

    /**
     * The fixed first line of a Category Recommendation (UX-DR43): the conflict when the plan does not fit, otherwise
     * the category in plain words. It holds no figures (the chips carry them), no "your" and no risk wording, so the
     * {@code SUITABILITY} check still needs the model's reasons.
     */
    public static String recommendationLead(String categoryLabel, List<String> conflicts) {
        if (conflicts.isEmpty()) {
            return "Recommended category: " + categoryLabel + ".";
        }
        return "This plan doesn't fit right now: " + String.join("; ", conflicts.stream()
                .map(ResponseTemplates::plainConflict).toList()) + ".";
    }

    /**
     * Like {@link #recommendationLead(String, List)}, in the turn's language; German names the category by its key
     * ({@link #CATEGORIES_DE}, else the English label). No "Ihr" and no risk wording either.
     */
    public static String recommendationLead(String category, String categoryLabel, List<String> conflicts,
            Language language) {
        if (language != Language.DE) {
            return recommendationLead(categoryLabel, conflicts);
        }
        if (conflicts.isEmpty()) {
            return "Empfohlene Kategorie: " + CATEGORIES_DE.getOrDefault(category, categoryLabel) + ".";
        }
        return "Dieser Plan passt gerade nicht: " + String.join("; ", conflicts.stream()
                .map(c -> CONFLICTS_DE.getOrDefault(c, c)).toList()) + ".";
    }

    /**
     * The fixed first line of a demands-and-needs statement (FR-18): it names the three areas and that no product is
     * recommended; no "your" and no risk wording, so the {@code SUITABILITY} check still needs the model's text.
     */
    public static final String NEEDS_LEAD = "Demands and needs: liability, occupational disability and cover held"
            + " twice. This is not a product recommendation.";
    static final String NEEDS_LEAD_DE = "Wünsche und Bedürfnisse: Haftpflicht, Berufsunfähigkeit und doppelt "
            + "bestehender Schutz. Dies ist keine Produktempfehlung.";

    /** The demands-and-needs lead in the turn's language. */
    public static String needsLead(Language language) {
        return language == Language.DE ? NEEDS_LEAD_DE : NEEDS_LEAD;
    }

    /** A conflict of the Core's category selection in plain words, as the first line and the panel show it. */
    public static String plainConflict(String conflict) {
        return CONFLICTS.getOrDefault(conflict, conflict);
    }

    /** The conflicts of the Core's category selection in plain words. */
    static final Map<String, String> CONFLICTS = Map.of(
            "emergency fund below target", "the emergency fund is below its target",
            "requested monthly amount exceeds monthly surplus",
            "the monthly amount is more than what is left each month");
    static final Map<String, String> CONFLICTS_DE = Map.of(
            "emergency fund below target", "der Notgroschen liegt unter seinem Ziel",
            "requested monthly amount exceeds monthly surplus",
            "der Monatsbetrag ist höher als das, was jeden Monat übrig bleibt");

    /** A recommendation draft that starts with its fixed first line; unchanged when it already does. */
    public static String withRecommendationLead(String draft, String lead) {
        String text = draft.strip();
        return text.startsWith(lead) ? text : (lead + "\n\n" + text).strip();
    }

    /** The profile question for the missing fields, with the default text when none are known. */
    public static String askProfile(List<String> missingFields) {
        return askProfile(missingFields, Language.EN);
    }

    /** Like {@link #askProfile(List)}, in the turn's language. */
    public static String askProfile(List<String> missingFields, Language language) {
        if (language == Language.DE) {
            String missing = fields(missingFields);
            return ASK_PROFILE_DE.replace("{missing}", missing.isEmpty() ? DEFAULT_MISSING_DE : missing);
        }
        String missing = String.join(", ", missingFields).replace('_', ' ');
        return ASK_PROFILE.replace("{missing}", missing.isEmpty() ? DEFAULT_MISSING : missing);
    }

    /** Profile fields in German plain words, comma-separated. */
    private static String fields(List<String> fields) {
        return String.join(", ", fields.stream().map(f -> FIELDS_DE.getOrDefault(f, f.replace('_', ' '))).toList());
    }
}
