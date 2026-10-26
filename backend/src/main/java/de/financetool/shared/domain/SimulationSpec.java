package de.financetool.shared.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * What a calculation runs with (AD-2, AD-24): the Core function and its parameters, each with where it came from.
 * A chat turn proposes it first and runs it only once the persona confirmed or changed it.
 *
 * @param function the tool's id, e.g. {@code withdrawalDuration} (never shown to customers)
 */
public record SimulationSpec(String function, List<Parameter> parameters) {

    /** Where a parameter's value came from, shown as its source tag. */
    public enum Source {
        /** "from your question" */
        QUESTION,
        /** "from your account data": a Core figure */
        ACCOUNT_DATA,
        /** changed by the persona on the proposal card */
        CHANGED
    }

    /**
     * One parameter.
     *
     * @param key the parameter's name within the function, e.g. {@code portfolio}
     * @param label the parameter in plain words
     * @param provId the Core figure an {@code ACCOUNT_DATA} value is; {@code null} for other sources
     */
    public record Parameter(String key, String label, BigDecimal value, Unit unit, Source source, String provId) {

        public Parameter {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(unit, "unit");
            Objects.requireNonNull(source, "source");
            if ((source == Source.ACCOUNT_DATA) != (provId != null)) {
                throw new IllegalArgumentException("exactly the account-data values carry a provId: " + key);
            }
        }

        /** This parameter with a value the persona entered. */
        public Parameter changedTo(BigDecimal newValue) {
            return newValue.compareTo(value) == 0 ? this
                    : new Parameter(key, label, newValue, unit, Source.CHANGED, null);
        }
    }

    public SimulationSpec {
        Objects.requireNonNull(function, "function");
        parameters = List.copyOf(parameters);
        if (parameters.stream().map(Parameter::key).distinct().count() != parameters.size()) {
            throw new IllegalArgumentException("parameter keys must be unique in " + function);
        }
    }

    /** The parameter {@code key}. */
    public Parameter parameter(String key) {
        return parameters.stream().filter(p -> p.key().equals(key)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("no parameter " + key + " in " + function));
    }
}
