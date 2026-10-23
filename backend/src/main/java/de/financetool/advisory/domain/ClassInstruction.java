package de.financetool.advisory.domain;

import java.util.stream.Collectors;

/**
 * The class instruction of the phrasing system part (layer A): the versioned file {@code class-<CLASS>.txt} with its
 * placeholders filled as {@code _system_prompt} of {@code experiments/finlab/pipeline.py} fills
 * {@code CLASS_INSTRUCTIONS}.
 */
public final class ClassInstruction {

    /** The missing-fields text when the profile check names no fields. */
    public static final String DEFAULT_MISSING =
            "your goals, horizon, income and expenses, emergency fund, risk tolerance";

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
        String premises = classification.unverifiedPremises().stream().map(Premise::claim)
                .collect(Collectors.joining("; "));
        String conflictText = conflicts == null || conflicts.isEmpty() ? ""
                : "The request conflicts with the profile (" + conflicts
                        + "): recommend against it and state the conflicting figures.";
        return template.replace("{missing}", missing)
                .replace("{category}", category == null ? "" : category.replace("_", " "))
                .replace("{conflicts}", conflictText)
                .replace("{oos}", oos)
                .replace("{premises}", premises);
    }
}
