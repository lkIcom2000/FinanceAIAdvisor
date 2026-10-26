package de.financetool.profile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * How the assessment card asks for each profile field (UX-DR14) and how an answer becomes a stored value: a choice
 * or slider stores its option key, an EUR amount a decimal with two places, a number an integer, a text the stripped
 * text. {@link Profiles#confirm} accepts only values of this form, so a card answer and a stored value never differ.
 */
public final class AssessmentFields {

    /** The input that fits a field. */
    public enum Input {
        CHOICE, EUR, NUMBER, SLIDER, TEXT
    }

    /** One option of a choice or slider: the stored key and what the card shows. */
    public record Option(String key, String label) {
    }

    /**
     * @param field the profile field
     * @param label the card's question label
     * @param input the input the card shows
     * @param options the choices in display order (a slider's from its lower to its upper end); empty otherwise
     * @param unit the unit after a number field (e.g. "years"); {@code null} for other inputs
     * @param min the smallest number or amount allowed
     * @param max the largest number or amount allowed, or the longest text
     */
    public record Field(String field, String label, Input input, List<Option> options, String unit, int min,
            int max) {

        public Field {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(input, "input");
            options = List.copyOf(options);
        }
    }

    private static final int MAX_AMOUNT = 10_000_000;
    private static final int MAX_TEXT = 200;

    /** Every field a card can ask for, in the order a card lists them. */
    private static final Map<String, Field> FIELDS = catalog(
            choice("goal", "What are you saving or investing for?",
                    option("retirement", "Retirement"), option("wealth_building", "Building wealth"),
                    option("home_purchase", "Buying a home"), option("education", "Education"),
                    option("purchase", "A larger purchase")),
            number("investment_horizon_years", "How long can the money stay invested?", "years", 0, 60),
            amount("net_income", "Your monthly net income"),
            amount("monthly_expenses", "Your monthly expenses"),
            amount("monthly_surplus", "What is left at the end of a month"),
            amount("emergency_fund", "Your emergency fund"),
            amount("current_balance", "Your current account balance"),
            choice("loss_bearing_capacity", "How much of the money could you afford to lose?",
                    option("none", "Nothing"), option("low", "A small part"),
                    option("medium", "A larger part"), option("high", "All of it")),
            new Field("risk_tolerance", "How much up and down in value are you comfortable with?", Input.SLIDER,
                    List.of(option("very low", "Very little"), option("low", "Little"), option("medium", "Some"),
                            option("high", "A lot"), option("very high", "Very much")), null, 0, 0),
            choice("loss_aversion", "How strongly do losses worry you?", option("low", "Not much"),
                    option("medium", "Somewhat"), option("high", "Strongly")),
            choice("knowledge_experience", "Your knowledge of and experience with investments",
                    option("none", "None yet"), option("basic", "Some basics"),
                    option("experienced", "Experienced")),
            choice("sustainability_preference", "Should your investments be sustainable?",
                    option("none", "No preference"), option("consider", "Take it into account"),
                    option("only", "Only sustainable investments")),
            amount("amount", "The amount you want to invest"),
            amount("requested_monthly_amount", "The monthly amount you have in mind"),
            text("current_holdings", "What do you already invest in?"),
            amount("tax_allowance_unused", "Your unused tax-free allowance this year"),
            text("pension_situation", "Your pension situation"),
            choice("employment_status", "Your employment", option("employed", "Employed"),
                    option("self_employed", "Self-employed"), option("civil_servant", "Civil servant"),
                    option("student", "Student or in training"), option("not_working", "Not working"),
                    option("retired", "Retired")),
            amount("gross_income", "Your gross income last year"),
            number("children", "Children you receive child benefit for", "children", 0, 15),
            number("age", "Your age", "years", 18, 120),
            text("occupation", "Your occupation"),
            choice("household", "Your household", option("single", "I live alone"),
                    option("parents", "With my parents"), option("partner", "With a partner"),
                    option("family", "With a partner and children"), option("single_parent", "Single parent")),
            number("dependants", "People who depend on your income", "people", 0, 15),
            text("existing_contracts", "Insurance you already have"),
            text("existing_contract_details", "The contract you want to switch"));

    private AssessmentFields() {
    }

    /** The field's card definition; empty for a field the card cannot ask for. */
    public static Optional<Field> of(String field) {
        return Optional.ofNullable(FIELDS.get(field));
    }

    /** Whether the card can ask for the field. */
    public static boolean askable(String field) {
        return FIELDS.containsKey(field);
    }

    /** The card position of a field: catalog order, unknown fields last. */
    public static int order(String field) {
        int i = 0;
        for (String key : FIELDS.keySet()) {
            if (key.equals(field)) {
                return i;
            }
            i++;
        }
        return Integer.MAX_VALUE;
    }

    /**
     * The stored value for an answer typed into the card.
     *
     * @throws IllegalArgumentException with a plain sentence for the card if the answer does not fit the field
     */
    public static Object parse(String field, String text) {
        Field definition = of(field).orElseThrow(() -> new IllegalArgumentException(
                "I can't store an answer called " + field + "."));
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Please answer: " + definition.label() + ".");
        }
        if (text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Please check your answer to: " + definition.label() + ".");
        }
        String value = text.strip();
        return switch (definition.input()) {
            case CHOICE, SLIDER -> definition.options().stream().filter(o -> o.key().equals(value)).findFirst()
                    .map(Option::key)
                    .orElseThrow(() -> new IllegalArgumentException("Please pick one of the choices for: "
                            + definition.label() + "."));
            case EUR -> amount(definition, value);
            case NUMBER -> number(definition, value);
            case TEXT -> {
                if (value.length() > definition.max()) {
                    throw new IllegalArgumentException("Please keep your answer to " + definition.label()
                            + " under " + definition.max() + " characters.");
                }
                yield value;
            }
        };
    }

    /**
     * The value to store for a field the card can ask for: what {@link #parse} makes of it (a number or a string);
     * other values of other fields are kept as they are.
     *
     * @throws IllegalArgumentException if a card field's value does not fit the field
     */
    public static Object normalize(String field, Object value) {
        if (!askable(field)) {
            return value;
        }
        String text = switch (value) {
            case BigDecimal d -> d.toPlainString();
            case Number n when !(n instanceof Double || n instanceof Float) -> n.toString();
            case String s -> s;
            case null, default -> throw new IllegalArgumentException("Please check your answer to: "
                    + of(field).orElseThrow().label() + ".");
        };
        return parse(field, text);
    }

    private static BigDecimal amount(Field definition, String value) {
        BigDecimal amount = decimal(definition, value);
        if (amount.signum() < 0 || amount.compareTo(BigDecimal.valueOf(definition.max())) > 0) {
            throw new IllegalArgumentException("Please enter an amount between 0 and " + definition.max()
                    + " euros for: " + definition.label() + ".");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static Integer number(Field definition, String value) {
        BigDecimal number = decimal(definition, value);
        if (number.stripTrailingZeros().scale() > 0 || number.compareTo(BigDecimal.valueOf(definition.min())) < 0
                || number.compareTo(BigDecimal.valueOf(definition.max())) > 0) {
            throw new IllegalArgumentException("Please enter a whole number from " + definition.min() + " to "
                    + definition.max() + " for: " + definition.label() + ".");
        }
        return number.intValueExact();
    }

    private static BigDecimal decimal(Field definition, String value) {
        if (!value.matches("-?\\d{1,9}(\\.\\d{1,2})?")) {
            throw new IllegalArgumentException("Please enter a number for: " + definition.label() + ".");
        }
        return new BigDecimal(value);
    }

    private static Map<String, Field> catalog(Field... fields) {
        Map<String, Field> out = new LinkedHashMap<>();
        for (Field field : fields) {
            if (!ProfileFields.known(field.field()) || out.put(field.field(), field) != null) {
                throw new IllegalStateException("Not a profile field or listed twice: " + field.field());
            }
        }
        return out;
    }

    private static Field choice(String field, String label, Option... options) {
        return new Field(field, label, Input.CHOICE, List.of(options), null, 0, 0);
    }

    private static Field amount(String field, String label) {
        return new Field(field, label, Input.EUR, List.of(), null, 0, MAX_AMOUNT);
    }

    private static Field number(String field, String label, String unit, int min, int max) {
        return new Field(field, label, Input.NUMBER, List.of(), unit, min, max);
    }

    private static Field text(String field, String label) {
        return new Field(field, label, Input.TEXT, List.of(), null, 0, MAX_TEXT);
    }

    private static Option option(String key, String label) {
        return new Option(key, label);
    }
}
