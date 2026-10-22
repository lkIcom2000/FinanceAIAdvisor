package de.financetool.core.domain;

import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Core function {@code duplicateCoverage}: two or more contracts that cover the same risk for the persona, as
 * policyholder or co-insured. Pension contracts insure no risk and are never compared. Pure.
 */
public final class DuplicateCoverage {

    public static final String FUNCTION = "duplicateCoverage";
    public static final String OUTPUT_COUNT = "duplicates";

    /** The flags (one per duplicated cover, citing every contract involved) and the computation to key. */
    public record Result(List<CoverageFlag> flags, CoreComputation computation) {

        public Result {
            flags = List.copyOf(flags);
            Objects.requireNonNull(computation, "computation");
        }
    }

    private DuplicateCoverage() {
    }

    /** The output key of the flag for {@code cover}: the number of contracts covering it. */
    public static String outputKey(InsuranceContract.Cover cover) {
        return "contracts." + cover.name();
    }

    /**
     * @param contracts the contracts on which the persona is policyholder or co-insured
     * @param personaId the persona the contracts cover, part of the key inputs
     */
    public static Result detect(List<InsuranceContract> contracts, String personaId) {
        Objects.requireNonNull(contracts, "contracts");
        Objects.requireNonNull(personaId, "personaId");
        Map<InsuranceContract.Cover, List<InsuranceContract>> byCover = new EnumMap<>(InsuranceContract.Cover.class);
        for (InsuranceContract contract : contracts) {
            if (contract.cover().insurance()) {
                byCover.computeIfAbsent(contract.cover(), c -> new ArrayList<>()).add(contract);
            }
        }

        List<CoverageFlag> flags = new ArrayList<>();
        List<Figure> outputs = new ArrayList<>();
        List<FigureInput> inputs = new ArrayList<>();
        boolean coInsuredInvolved = false;
        for (Map.Entry<InsuranceContract.Cover, List<InsuranceContract>> e : byCover.entrySet()) {
            List<InsuranceContract> involved = e.getValue();
            if (involved.size() < 2) {
                continue;
            }
            InsuranceContract.Cover cover = e.getKey();
            flags.add(new CoverageFlag(CoverageFlag.Kind.DUPLICATE_COVERAGE, cover, outputKey(cover),
                    involved.stream().map(InsuranceContract::id).toList()));
            outputs.add(new Figure(outputKey(cover), "Contracts covering " + cover.plainWords(),
                    BigDecimal.valueOf(involved.size()), Unit.COUNT));
            for (InsuranceContract contract : involved) {
                inputs.add(FigureInput.text("Covers " + cover.plainWords(), contract.citation()));
                coInsuredInvolved |= contract.coInsured();
            }
        }
        outputs.addFirst(new Figure(OUTPUT_COUNT, "Risks covered more than once", BigDecimal.valueOf(flags.size()),
                Unit.COUNT));
        if (inputs.isEmpty()) {
            inputs.add(FigureInput.text("Contracts", contracts.isEmpty() ? "You don't have any insurance contracts."
                    : "No risk is covered by more than one of your contracts."));
        }

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("persona", personaId);
        keyInputs.put("contracts", contracts.stream()
                .map(c -> (Object) (c.id() + ":" + c.cover() + ":" + (c.coInsured() ? "co-insured" : "policyholder")))
                .toList());

        List<String> assumptions = new ArrayList<>();
        assumptions.add("Fine compares the kind of cover only, not the conditions or limits of each contract.");
        assumptions.add("Pension contracts insure no risk and are not compared.");
        if (coInsuredInvolved) {
            assumptions.add("Whether the family policy still covers you depends on its conditions, such as having"
                    + " your own household.");
        }
        return new Result(flags, new CoreComputation(FUNCTION, "duplicate coverage check",
                "Based on the insurance contracts on which you are policyholder or co-insured.", keyInputs, inputs,
                outputs, assumptions, Map.of()));
    }
}
