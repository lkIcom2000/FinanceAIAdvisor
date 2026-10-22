package de.financetool.core.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a pure Core function returns, before the gateway keys and stores it.
 *
 * @param function the function id, part of the figure key (never shown to customers)
 * @param plainName the function in plain words, e.g. "current account balance"
 * @param basis the data the function used, as a plain sentence
 * @param keyInputs the canonical inputs hashed into the figure key ({@link CanonicalForm})
 * @param inputs the inputs as shown to the customer
 * @param ruleParamVersions Rule Parameter id to version
 */
public record CoreComputation(String function, String plainName, String basis, Map<String, Object> keyInputs,
        List<FigureInput> inputs, List<Figure> outputs, List<String> assumptions,
        Map<String, String> ruleParamVersions) {

    public CoreComputation {
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(plainName, "plainName");
        Objects.requireNonNull(basis, "basis");
        keyInputs = Map.copyOf(keyInputs);
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        assumptions = List.copyOf(assumptions);
        ruleParamVersions = Map.copyOf(ruleParamVersions);
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("a Core function has at least one output");
        }
        if (outputs.stream().map(Figure::outputKey).distinct().count() != outputs.size()) {
            // Equal output keys would give equal figure keys, and the store would keep only the first.
            throw new IllegalArgumentException("output keys must be unique in " + function);
        }
    }
}
