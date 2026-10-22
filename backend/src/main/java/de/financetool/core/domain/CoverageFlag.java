package de.financetool.core.domain;

import java.util.List;
import java.util.Objects;

/**
 * A flag on the persona's insurance: Duplicate Coverage or a missing essential cover.
 *
 * @param outputKey the output of the flag's Core result whose figure key the flag carries
 * @param contractIds every contract involved, in input order (empty for a missing cover)
 */
public record CoverageFlag(Kind kind, InsuranceContract.Cover cover, String outputKey, List<String> contractIds) {

    public enum Kind {
        DUPLICATE_COVERAGE, MISSING_COVER
    }

    public CoverageFlag {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(cover, "cover");
        Objects.requireNonNull(outputKey, "outputKey");
        contractIds = List.copyOf(contractIds);
    }
}
