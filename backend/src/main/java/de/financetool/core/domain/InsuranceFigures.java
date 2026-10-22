package de.financetool.core.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.modulith.NamedInterface;

/**
 * The keyed results behind "Your insurance", from one {@code CoreGateway} call.
 *
 * @param duplicates outputs of {@link DuplicateCoverage}; a duplicate flag carries the key of its output key
 * @param missing outputs of {@link MissingEssentialCover}; a missing-cover flag carries the key of its output key
 */
@NamedInterface("results")
public record InsuranceFigures(List<Contract> contracts, CoreResult duplicates, List<CoverageFlag> duplicateFlags,
        CoreResult missing, List<CoverageFlag> missingFlags) {

    /**
     * A contract with its keyed premium ({@link InsurancePremium#OUTPUT}).
     *
     * @param premium {@code null} if the persona does not pay a premium (e.g. co-insured)
     * @param nextDueDate the next due date after {@code asOf}, or {@code null} without a premium
     */
    @NamedInterface("results")
    public record Contract(InsuranceContract contract, CoreResult premium, LocalDate nextDueDate) {

        public Contract {
            Objects.requireNonNull(contract, "contract");
            if ((premium == null) != (nextDueDate == null)) {
                throw new IllegalArgumentException("a premium comes with its next due date");
            }
        }
    }

    public InsuranceFigures {
        contracts = List.copyOf(contracts);
        Objects.requireNonNull(duplicates, "duplicates");
        duplicateFlags = List.copyOf(duplicateFlags);
        Objects.requireNonNull(missing, "missing");
        missingFlags = List.copyOf(missingFlags);
    }
}
