package de.financetool.advisory.domain;

import de.financetool.core.domain.CoreResult;
import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.util.ArrayList;
import java.util.List;

/**
 * The Core results as the phrasing prompt shows them under {@code TOOL RESULTS}: a port of
 * {@code format_tool_results} in {@code experiments/finlab/prompts.py}. The model gets only this text, never a tool
 * name to call.
 */
public final class ToolResults {

    private ToolResults() {
    }

    public static String format(List<CoreResult> results) {
        List<String> lines = new ArrayList<>();
        for (CoreResult r : results) {
            lines.add("- " + r.function() + ":");
            for (SourcedNumber n : r.numbers()) {
                lines.add("    " + n.label() + ": " + n.value().toPlainString() + " " + unit(n.unit()));
            }
            if (!r.assumptions().isEmpty()) {
                lines.add("    assumptions: " + String.join("; ", r.assumptions()));
            }
        }
        return String.join("\n", lines);
    }

    /** {@code _unit}: how the prompt names a unit. */
    private static String unit(Unit unit) {
        return switch (unit) {
            case EUR -> "EUR";
            case FRACTION -> "(as a fraction of one; state it as a percentage)"; // no digits, see NumberCheck
            case PP -> "percentage points";
            case MONTHS -> "months";
            case YEARS -> "years";
            case COUNT -> "";
            case FLAG -> "(one means yes, zero means no)";
        };
    }
}
