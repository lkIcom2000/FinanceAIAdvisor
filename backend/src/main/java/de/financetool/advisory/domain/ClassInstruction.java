package de.financetool.advisory.domain;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The class instruction of the phrasing system part (layer A): the versioned file {@code class-<CLASS>.txt} with its
 * placeholders filled as {@code _system_prompt} of {@code experiments/finlab/pipeline.py} fills
 * {@code CLASS_INSTRUCTIONS}.
 *
 * <p>The premise claims are the only text from the message or the NLU that reaches the system part. They pass the
 * aggregated exposure filter at every level ({@link ExposureFilter#filterDerived}), lose every slot marker and brace, and are inserted in one pass over the
 * template, so inserted text is never scanned for placeholders.
 */
public final class ClassInstruction {

    /** The missing-fields text when the profile check names no fields. */
    public static final String DEFAULT_MISSING =
            "your goals, horizon, income and expenses, emergency fund, risk tolerance";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(missing|category|conflicts|oos|premises)}");
    private static final Pattern BRACES = Pattern.compile("[{}]");

    private ClassInstruction() {
    }

    /** The prompt file of a class, without the {@code .txt} extension. */
    public static String fileName(MessageClass messageClass) {
        return "class-" + messageClass.name();
    }

    /**
     * @param template the class instruction file's text
     * @param category the recommended product category ({@code null} or blank until the category tools exist)
     * @param conflicts the profile conflicts of the recommendation (empty until the category tools exist)
     */
    public static String fill(String template, Classification classification, String category, String conflicts) {
        String missing = classification.missingFields().isEmpty() ? DEFAULT_MISSING
                : String.join(", ", classification.missingFields());
        String oos = classification.outOfScope().isEmpty() ? "this product"
                : String.join(", ", classification.outOfScope()).replace("_", " ");
        String premises = classification.unverifiedPremises().stream()
                .map(p -> ExposureFilter.filterDerived(p.claim()))
                .collect(Collectors.joining("; "));
        String conflictText = conflicts == null || conflicts.isEmpty() ? ""
                : "The request conflicts with the profile (" + conflicts
                        + "): recommend against it and state the conflicting figures.";
        Map<String, String> values = Map.of(
                "missing", missing,
                "category", category == null ? "" : category.replace("_", " "),
                "conflicts", conflictText,
                "oos", oos,
                "premises", premises);
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(inert(values.get(m.group(1)))));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Text without slot markers and braces, so it can neither open a slot nor look like a placeholder. */
    static String inert(String text) {
        return PhrasingPrompt.data(BRACES.matcher(text).replaceAll(""));
    }
}
