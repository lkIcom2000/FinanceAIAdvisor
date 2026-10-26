package de.financetool.advisory.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the NLU role extracts from a user message (AD-4): data only, never an instruction. A port of
 * {@code experiments/finlab/nlu.py} (commit {@code b72a70e}): the schema the model must follow ({@link #schema()}),
 * the defaults that make every field present ({@link #normalize(Map)}) and the deterministic grounding rules that
 * require evidence in the message before a proposal can change the class ({@link #ground(String)}).
 *
 * @param statedProfile profile facts the message states; they apply to this turn only and are never persisted
 * @param computationParams figures for a calculation, rates as fractions
 */
public record NluExtraction(
        String requestType,
        String subject,
        String topic,
        List<String> productCategories,
        List<Premise> assertedPremises,
        boolean instructionOverrideAttempt,
        boolean marketTimingRequest,
        Map<String, Object> statedProfile,
        Map<String, Double> computationParams,
        String depersonalizedQuestion) {

    public static final List<String> REQUEST_TYPES = List.of("general_explanation", "personal_recommendation",
            "product_selection", "computation", "profile_or_eligibility_question", "other");
    public static final List<String> SUBJECTS = List.of("self", "specific_client", "generic");
    public static final List<String> TOPICS = List.of("asset_allocation", "savings_plan", "investment_decision",
            "diversification", "short_term_investment", "sustainability_fit", "product_variant_tax",
            "retirement_strategy", "leverage_or_loan", "fund_selection", "liquidity_management",
            "subsidy_eligibility", "contract_switch", "protection_needs", "protection_duplicate",
            "withdrawal_duration", "shortfall_check", "savings_projection", "cost_question", "regulation_or_concept",
            "tax_question", "market_timing", "other");
    public static final List<String> PRODUCT_CATEGORIES = List.of("single_stock", "penny_stock",
            "derivative_or_certificate", "crypto", "forex_or_cfd", "building_society_savings",
            "insurance_tariff_selection", "individual_tax_advice", "single_bond", "etf", "fund", "deposit",
            "money_market", "insurance_needs", "pension_product", "gold_or_commodity", "loan", "other");
    public static final List<String> PREMISE_KINDS = List.of("law_or_regulation", "product_or_isin",
            Premise.GUARANTEE, "institution_rule_or_capability", "historical_fact", "other");

    /** The profile fields the NLU may extract, with their JSON type ({@code PROFILE_FIELDS} of {@code profile.py}). */
    public static final Map<String, String> PROFILE_FIELDS = profileFields();

    static final List<String> COMPUTATION_PARAMS = List.of("portfolio", "monthly_withdrawal", "annual_rate",
            "net_income", "fixed_costs", "balance", "due_amount", "variable_spending", "monthly_contribution",
            "years", "initial");

    private static final int FLAGS = Pattern.UNICODE_CHARACTER_CLASS;
    /** Legal-assessment wording near a document word adds {@link Classifier#LEGAL_ASSESSMENT}. */
    private static final String LEGAL = "(legal(ly)?|lawful|unlawful|enforceable|rechtlich\\w*|rechtens"
            + "|rechtswirksam\\w*|unwirksam\\w*)";
    private static final String DOCUMENT = "(letters?|contracts?|clauses?|termination|vertr(a|ä)g\\w*|klausel\\w*"
            + "|kündigung\\w*)";
    private static final int FLAGS_CI = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;
    /** A legal assessment counts only for the user's own or a shown document ("my contract", "this letter"). */
    static final Pattern OWN_DOCUMENT = Pattern.compile("\\b(my|our|this|mein\\w*|unser\\w*|dies\\w*)\\b", FLAGS_CI);

    /**
     * An out-of-scope category counts only if the message contains matching wording. Deviation from {@code nlu.py}:
     * German wording is added (the experiment's lists are English only, so R08's "Tarifname" lost its category).
     */
    static final Map<String, Pattern> SCOPE_EVIDENCE = patterns(FLAGS,
            "single_stock", "\\b(stocks?|shares?|equit(y|ies)|aktien?|einzelaktien?|einzelwert\\w*)\\b",
            "penny_stock", "\\b(penny|pennystocks?)\\b",
            "derivative_or_certificate", "\\b(derivatives?|certificates?|knock-?outs?|options?|futures?|warrants?"
                    + "|turbos?|leveraged?|zertifikat\\w*|derivat\\w*|optionsschein\\w*|hebel\\w*)\\b",
            "crypto", "\\b(crypto\\w*|bitcoin|btc|ether(eum)?|altcoins?|stablecoins?|coins?|tokens?|nfts?"
                    + "|blockchain|krypto\\w*)\\b",
            "forex_or_cfd", "\\b(forex|fx|cfds?|currency trading|contracts? for difference|devisen\\w*"
                    + "|differenzkontrakt\\w*)\\b",
            "building_society_savings", "\\b(building society|bauspar\\w*|home savings contract)\\b",
            "insurance_tariff_selection", "\\b(tariffs?|which (insurer|insurance company|provider|policy|insurance)|"
                    + "compare (insurers|policies|insurance offers)|(cheapest|best) (insurer|policy|"
                    + "insurance company)|tarif\\w*|versicherer\\w*|welche versicherung\\w*|"
                    + "(günstigste|beste) versicherung\\w*)\\b",
            "individual_tax_advice", "\\b(tax return|tax advi[cs]\\w*|steuererkl\\w*|(reduce|avoid|save) (my )?"
                    + "tax(es)?|steuerberat\\w*|steuern? sparen)\\b",
            "single_bond", "\\b(bonds?|anleihen?|debentures?)\\b");

    /**
     * Unambiguous wording adds the category even if the NLU missed it. German wording is added (see
     * {@link #SCOPE_EVIDENCE}); {@code Tarif…} counts, the English "tariff" does not (it also names a health
     * insurance plan in general questions).
     */
    static final Map<String, Pattern> SCOPE_STRONG = patterns(FLAGS,
            "single_stock", "\\b((single|individual|specific|which) (stocks?|shares?)|einzelaktien?|welche aktien?)\\b",
            "penny_stock", "\\b(penny stocks?|pennystocks?)\\b",
            "crypto", "\\b(crypto\\w*|bitcoin|ether(eum)?|altcoins?|stablecoins?|nfts?|krypto\\w*)\\b",
            "forex_or_cfd", "\\b(forex|cfds?|devisenhandel)\\b",
            "building_society_savings", "\\b(building society|bauspar\\w*)\\b",
            "insurance_tariff_selection", "\\btarif(?!f|vertr|lich|runde|lohn|partner|verhandl)\\w*",
            Classifier.LEGAL_ASSESSMENT, "\\b" + LEGAL + "\\b[^.?!]{0,60}\\b" + DOCUMENT + "\\b|\\b" + DOCUMENT
                    + "\\b[^.?!]{0,60}\\b" + LEGAL + "\\b");

    static final Pattern ADVICE = Pattern.compile("\\b(should|can|could|may|shall) (i|we)\\b|\\bi should\\b"
            + "|\\bfor me\\b|\\bmy (wealth|savings|money|portfolio|income|capital)\\b", FLAGS_CI);
    static final Pattern PERSON = Pattern.compile("\\b(i|i'm|i've|i'd|me|my|mine|we|our|ours)\\b", FLAGS_CI);
    static final Pattern CLIENT = Pattern.compile("\\b(client|customer|profile|he|she|his|her)\\b", FLAGS_CI);
    private static final String MONEY = "(\\d{1,3}(?:[.,]\\d{3})+|\\d+)(?:[.,]\\d+)?\\s?(?:EUR|€|euros?)";
    private static final Pattern MONTHLY = Pattern.compile(
            MONEY + "\\s*(?:per month|a month|monthly|each month)", FLAGS_CI);
    private static final Pattern PORTFOLIO = Pattern.compile(
            "\\b(portfolio|capital|savings|wealth|assets|lump sum)\\b[^.?]{0,20}?" + MONEY, FLAGS_CI);
    private static final Pattern RATE = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s?%\\s*(?:\\w+\\s+){0,3}(return|interest|yield|growth)", FLAGS_CI);
    private static final Pattern THOUSANDS = Pattern.compile("[.,](?=\\d{3}\\b)", FLAGS);

    /** The extraction and the notes of the grounding rules that changed it. */
    public record Grounded(NluExtraction extraction, List<String> notes) {

        public Grounded {
            Objects.requireNonNull(extraction, "extraction");
            notes = List.copyOf(notes);
        }
    }

    public NluExtraction {
        Objects.requireNonNull(requestType, "requestType");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(depersonalizedQuestion, "depersonalizedQuestion");
        productCategories = List.copyOf(productCategories);
        assertedPremises = List.copyOf(assertedPremises);
        statedProfile = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(statedProfile));
        computationParams = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(computationParams));
    }

    /**
     * Fills defaults so downstream rules never see missing keys ({@code normalize} of {@code nlu.py}); an
     * unparseable model answer is normalised from an empty map.
     */
    public static NluExtraction normalize(Map<String, ?> d) {
        Object requestType = d.get("request_type");
        Object subject = d.get("subject");
        Object topic = d.get("topic");
        List<String> categories = new ArrayList<>();
        for (Object c : list(d.get("product_categories"))) {
            if (c instanceof String s && PRODUCT_CATEGORIES.contains(s)) {
                categories.add(s);
            }
        }
        List<Premise> premises = new ArrayList<>();
        for (Object p : list(d.get("asserted_premises"))) {
            if (p instanceof Map<?, ?> m) {
                premises.add(new Premise(text(m.get("claim")), m.get("kind") == null ? "other" : text(m.get("kind")),
                        text(m.get("named_entity"))));
            }
        }
        Map<String, Object> profile = new LinkedHashMap<>();
        if (d.get("stated_profile") instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                if (k instanceof String key && PROFILE_FIELDS.containsKey(key) && !empty(v)) {
                    profile.put(key, v);
                }
            });
        }
        Map<String, Double> params = new LinkedHashMap<>();
        if (d.get("computation_params") instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                // Python's isinstance(v, (int, float)) also accepts a boolean.
                if (v instanceof Number n) {
                    params.put(String.valueOf(k), n.doubleValue());
                } else if (v instanceof Boolean b) {
                    params.put(String.valueOf(k), b ? 1.0 : 0.0);
                }
            });
        }
        Object question = d.get("depersonalized_question");
        return new NluExtraction(
                requestType instanceof String s && REQUEST_TYPES.contains(s) ? s : "other",
                subject instanceof String s && SUBJECTS.contains(s) ? s : "self",
                topic instanceof String s && TOPICS.contains(s) ? s : "other",
                categories, premises, truthy(d.get("instruction_override_attempt")),
                truthy(d.get("market_timing_request")), profile, params,
                truthy(question) ? text(question) : "");
    }

    /** {@code normalize({})}: the extraction of an unparseable model answer. */
    public static NluExtraction defaults() {
        return normalize(Map.of());
    }

    /**
     * This extraction with the deterministic grounding rules applied ({@code ground} of {@code nlu.py}): an
     * out-of-scope category needs matching wording, unambiguous wording adds one, advice on an out-of-scope product
     * is a personal request, a message about nobody is generic, and withdrawal figures are taken from the message.
     */
    public Grounded ground(String message) {
        List<String> notes = new ArrayList<>();
        String low = message.toLowerCase(Locale.ROOT);

        List<String> cats = new ArrayList<>(productCategories);
        for (String c : List.copyOf(cats)) {
            if (SCOPE_EVIDENCE.containsKey(c) && !SCOPE_EVIDENCE.get(c).matcher(low).find()) {
                cats.remove(c);
                notes.add("dropped category '" + c + "': no matching wording in the message");
            }
        }
        SCOPE_STRONG.forEach((c, rx) -> {
            boolean own = !c.equals(Classifier.LEGAL_ASSESSMENT) || OWN_DOCUMENT.matcher(message).find();
            if (!cats.contains(c) && own && rx.matcher(low).find()) {
                cats.add(c);
                notes.add("added category '" + c + "' from the message wording");
            }
        });

        String type = requestType;
        if (type.equals("general_explanation") && ADVICE.matcher(message).find()
                && cats.stream().anyMatch(SCOPE_EVIDENCE::containsKey)) {
            type = "personal_recommendation";
            notes.add("request asks for advice for the user on an out-of-scope product");
        }
        // App addition: assessing the user's own letter or contract is always a personal request.
        if (cats.contains(Classifier.LEGAL_ASSESSMENT) && !Classifier.PERSONAL_TYPES.contains(type)
                && !type.equals("computation") && PERSON.matcher(message).find()) {
            type = "personal_recommendation";
            notes.add("request asks for a legal assessment of the user's own document");
        }

        String who = subject;
        if (!who.equals("generic") && !PERSON.matcher(message).find() && !CLIENT.matcher(message).find()) {
            who = "generic";
            notes.add("subject set to generic: the message refers to no person");
        }

        Map<String, Double> p = new LinkedHashMap<>(computationParams);
        if (low.contains("withdraw") && !p.containsKey("monthly_withdrawal")) {
            Matcher m = MONTHLY.matcher(message);
            if (m.find()) {
                p.put("monthly_withdrawal", amount(m.group(1)));
                notes.add("monthly_withdrawal taken from the message");
            }
        }
        if (p.containsKey("monthly_withdrawal") && !p.containsKey("portfolio")) {
            Matcher m = PORTFOLIO.matcher(message);
            Matcher monthly = MONTHLY.matcher(message);
            int monthlyStart = monthly.find() ? monthly.start() : -1;
            boolean found = false;
            // Deviation from nlu.py: the amount of the monthly withdrawal is never the portfolio ("my savings ...
            // if I withdraw 200 EUR a month" has no portfolio amount).
            while (!found && m.find()) {
                found = m.start(2) != monthlyStart;
            }
            if (found) {
                p.put("portfolio", amount(m.group(2)));
            } else if (p.getOrDefault("initial", 0.0) > 0) {
                // Deviation from nlu.py: the model fills "initial": 0.0 for "my savings", which is no portfolio.
                p.put("portfolio", p.get("initial"));
            }
            if (p.containsKey("portfolio")) {
                notes.add("portfolio taken from the message");
            }
        }
        if (p.containsKey("monthly_withdrawal") && !p.containsKey("annual_rate")) {
            Matcher m = RATE.matcher(message);
            if (m.find()) {
                p.put("annual_rate", number(m.group(1).replace(",", ".")) / 100);
                notes.add("annual_rate taken from the message");
            }
        }
        return new Grounded(new NluExtraction(type, who, topic, cats, assertedPremises, instructionOverrideAttempt,
                marketTimingRequest, statedProfile, p, depersonalizedQuestion), notes);
    }

    /** The extraction with the experiment's keys, for the Audit Record. */
    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("request_type", requestType);
        out.put("subject", subject);
        out.put("topic", topic);
        out.put("product_categories", productCategories);
        out.put("asserted_premises", assertedPremises.stream().map(Premise::toMap).toList());
        out.put("instruction_override_attempt", instructionOverrideAttempt);
        out.put("market_timing_request", marketTimingRequest);
        out.put("stated_profile", statedProfile);
        out.put("computation_params", computationParams);
        out.put("depersonalized_question", depersonalizedQuestion);
        return out;
    }

    /** The JSON schema the NLU model must follow (Ollama {@code format}), {@code SCHEMA} of {@code nlu.py}. */
    public static Map<String, Object> schema() {
        Map<String, Object> premise = new LinkedHashMap<>();
        premise.put("type", "object");
        premise.put("properties", ordered(
                "claim", Map.of("type", "string"),
                "kind", ordered("type", "string", "enum", PREMISE_KINDS),
                "named_entity", Map.of("type", "string")));
        premise.put("required", List.of("claim", "kind", "named_entity"));

        Map<String, Object> profile = new LinkedHashMap<>();
        PROFILE_FIELDS.forEach((field, type) -> {
            if (type.equals("array")) {
                profile.put(field, ordered("type", "array", "items", ordered(
                        "type", "object",
                        "properties", ordered("risk", Map.of("type", "string"), "holder", Map.of("type", "string")),
                        "required", List.of("risk", "holder"))));
            } else {
                profile.put(field, Map.of("type", type));
            }
        });
        Map<String, Object> params = new LinkedHashMap<>();
        for (String param : COMPUTATION_PARAMS) {
            params.put(param, Map.of("type", "number"));
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("request_type", ordered("type", "string", "enum", REQUEST_TYPES));
        properties.put("subject", ordered("type", "string", "enum", SUBJECTS));
        properties.put("topic", ordered("type", "string", "enum", TOPICS));
        properties.put("product_categories", ordered("type", "array", "items",
                ordered("type", "string", "enum", PRODUCT_CATEGORIES)));
        properties.put("asserted_premises", ordered("type", "array", "items", premise));
        properties.put("instruction_override_attempt", Map.of("type", "boolean"));
        properties.put("market_timing_request", Map.of("type", "boolean"));
        properties.put("stated_profile", ordered("type", "object", "properties", profile));
        properties.put("computation_params", ordered("type", "object", "properties", params));
        properties.put("depersonalized_question", Map.of("type", "string"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("request_type", "subject", "topic", "product_categories",
                "asserted_premises", "instruction_override_attempt", "market_timing_request", "stated_profile",
                "computation_params", "depersonalized_question"));
        return schema;
    }

    /** {@code _amount} of {@code nlu.py}: thousands separators removed, a decimal comma read as a point. */
    static double amount(String s) {
        return number(THOUSANDS.matcher(s).replaceAll("").replace(",", "."));
    }

    /** Like Python's {@code float}: any Unicode decimal digit counts. */
    private static double number(String s) {
        StringBuilder ascii = new StringBuilder();
        s.codePoints().forEach(c -> {
            if (Character.isDigit(c)) {
                ascii.append(Character.digit(c, 10));
            } else {
                ascii.appendCodePoint(c);
            }
        });
        return Double.parseDouble(ascii.toString());
    }

    private static Map<String, String> profileFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        String[] entries = {"age", "number", "occupation", "string", "employment_status", "string",
            "net_income", "number", "gross_income", "number", "monthly_expenses", "number",
            "monthly_surplus", "number", "emergency_fund", "number", "current_balance", "number",
            "risk_tolerance", "string", "loss_aversion", "string", "loss_bearing_capacity", "string",
            "investment_horizon_years", "number", "goal", "string", "knowledge_experience", "string",
            "sustainability_preference", "string", "current_holdings", "string", "tax_allowance_unused", "number",
            "pension_situation", "string", "children", "number", "household", "string", "dependants", "string",
            "existing_contract_details", "string", "existing_contracts", "array", "amount", "number",
            "requested_monthly_amount", "number"};
        for (int i = 0; i < entries.length; i += 2) {
            fields.put(entries[i], entries[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(fields);
    }

    private static Map<String, Pattern> patterns(int flags, String... entries) {
        Map<String, Pattern> out = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            out.put(entries[i], Pattern.compile(entries[i + 1], flags));
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    private static Map<String, Object> ordered(Object... entries) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            out.put((String) entries[i], entries[i + 1]);
        }
        return out;
    }

    private static Collection<?> list(Object value) {
        return value instanceof Collection<?> c ? c : List.of();
    }

    private static String text(Object value) {
        return value == null ? "" : value instanceof String s ? s : String.valueOf(value);
    }

    /** Python's {@code v in (None, "", [], {})}. */
    private static boolean empty(Object v) {
        return v == null || "".equals(v) || v instanceof Collection<?> c && c.isEmpty()
                || v instanceof Map<?, ?> m && m.isEmpty();
    }

    /** Python's truthiness. */
    private static boolean truthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.doubleValue() != 0;
        }
        if (v instanceof String s) {
            return !s.isEmpty();
        }
        return !empty(v);
    }
}
