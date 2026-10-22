package de.financetool.audit;

import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Module API: the provenance of one figure (FR-8), stored as the content of its evidence item and served by
 * {@code GET /api/provenance/{provId}}. Built by {@code core}; every text in it is customer-facing.
 *
 * @param function the Core function id (internal; never shown to customers)
 * @param asOf the simulated date the figure was computed for
 */
public record Provenance(String provId, String function, Source source, String label, BigDecimal value, Unit unit,
        LocalDate asOf, List<Input> inputs, List<String> assumptions, List<RuleParameter> ruleParameters,
        List<Check> checks) {

    public enum SourceKind {
        CORE_FUNCTION, RULE_PARAMETER, KB_CITATION
    }

    /** Where the figure comes from, as a plain sentence. */
    public record Source(SourceKind kind, String description) {

        public Source {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(description, "description");
        }
    }

    /**
     * One input: a number with unit or a text, with the input's own figure key where one applies.
     *
     * @param value {@code null} for a text input
     * @param unit {@code null} for a text input
     * @param text {@code null} for a number input
     * @param provId {@code null} if the input is not itself a stored figure
     */
    public record Input(String label, BigDecimal value, Unit unit, String text, String provId) {

        public Input {
            Objects.requireNonNull(label, "label");
        }
    }

    /**
     * A Rule Parameter the figure depends on, with its version and validity.
     *
     * @param validFrom first day the version applies, or {@code null} if unknown
     * @param validTo last day the version applies, or {@code null} if open-ended or unknown
     */
    public record RuleParameter(String id, String version, LocalDate validFrom, LocalDate validTo) {

        public RuleParameter {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(version, "version");
        }
    }

    /** One check result; {@code id} is shown only as a small caption. */
    public record Check(String id, boolean passed, String description) {

        public Check {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(description, "description");
        }
    }

    public Provenance {
        Objects.requireNonNull(provId, "provId");
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(asOf, "asOf");
        inputs = List.copyOf(inputs);
        assumptions = List.copyOf(assumptions);
        ruleParameters = List.copyOf(ruleParameters);
        checks = List.copyOf(checks);
    }
}
