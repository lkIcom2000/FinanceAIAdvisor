package de.financetool.core.domain;

import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Core function {@code missingEssentialCover}: the essential covers (private liability, occupational disability) that
 * no contract of the persona covers. Riester and other pension contracts do not count. Pure.
 */
public final class MissingEssentialCover {

    public static final String FUNCTION = "missingEssentialCover";
    public static final List<InsuranceContract.Cover> ESSENTIAL = List.of(InsuranceContract.Cover.LIABILITY,
            InsuranceContract.Cover.OCCUPATIONAL_DISABILITY);

    /** The flags (one per missing essential cover) and the computation to key. */
    public record Result(List<CoverageFlag> flags, CoreComputation computation) {

        public Result {
            flags = List.copyOf(flags);
            Objects.requireNonNull(computation, "computation");
        }
    }

    private MissingEssentialCover() {
    }

    /** The output key for {@code cover}: the number of contracts covering it (0 for a flag). */
    public static String outputKey(InsuranceContract.Cover cover) {
        return "covered." + cover.name();
    }

    public static Result detect(List<InsuranceContract> contracts) {
        Objects.requireNonNull(contracts, "contracts");
        List<CoverageFlag> flags = new ArrayList<>();
        List<Figure> outputs = new ArrayList<>();
        List<FigureInput> inputs = new ArrayList<>();
        for (InsuranceContract.Cover cover : ESSENTIAL) {
            List<InsuranceContract> covering = contracts.stream().filter(c -> c.cover() == cover).toList();
            outputs.add(new Figure(outputKey(cover), "Contracts covering " + cover.plainWords(),
                    BigDecimal.valueOf(covering.size()), Unit.COUNT));
            if (covering.isEmpty()) {
                flags.add(new CoverageFlag(CoverageFlag.Kind.MISSING_COVER, cover, outputKey(cover), List.of()));
                inputs.add(FigureInput.text(cover.typeName(), "No contract found"));
            } else {
                covering.forEach(c -> inputs.add(FigureInput.text(cover.typeName(), c.citation())));
            }
        }

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("contracts", contracts.stream().map(c -> (Object) (c.id() + ":" + c.cover())).toList());

        return new Result(flags, new CoreComputation(FUNCTION, "essential cover check",
                "Based on the insurance contracts on which you are policyholder or co-insured.", keyInputs, inputs,
                outputs,
                List.of("Private liability and occupational disability insurance count as essential cover.",
                        "Riester and other pension contracts do not count as cover.",
                        "Fine knows only the contracts in your banking data; cover you hold elsewhere is not"
                                + " included."),
                Map.of()));
    }
}
