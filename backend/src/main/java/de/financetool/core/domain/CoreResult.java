package de.financetool.core.domain;

import de.financetool.shared.domain.SourcedNumber;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.modulith.NamedInterface;

/**
 * The result of a Core call through the {@code CoreGateway}: the computation plus one keyed {@link SourcedNumber} per
 * output, in output order.
 */
@NamedInterface("results")
public record CoreResult(String function, List<FigureInput> inputs, List<Figure> outputs, List<String> assumptions,
        Map<String, String> ruleParamVersions, List<SourcedNumber> numbers) {

    public CoreResult {
        Objects.requireNonNull(function, "function");
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        assumptions = List.copyOf(assumptions);
        ruleParamVersions = Map.copyOf(ruleParamVersions);
        numbers = List.copyOf(numbers);
        if (numbers.size() != outputs.size()) {
            throw new IllegalArgumentException("one keyed number per output");
        }
    }

    /** The keyed number of the output {@code outputKey}. */
    public SourcedNumber number(String outputKey) {
        for (int i = 0; i < outputs.size(); i++) {
            if (outputs.get(i).outputKey().equals(outputKey)) {
                return numbers.get(i);
            }
        }
        throw new NoSuchElementException("no output " + outputKey + " in " + function);
    }
}
