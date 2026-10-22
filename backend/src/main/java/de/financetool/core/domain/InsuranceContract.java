package de.financetool.core.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import org.springframework.modulith.NamedInterface;

/**
 * An insurance or pension contract on which the persona is insured, as the insurance Core functions see it.
 *
 * @param id the contract's id, part of the figure keys
 * @param policyholder who holds the policy, e.g. "Parents of Jonas"
 * @param coInsured {@code true} if the persona is co-insured on someone else's policy (e.g. a family policy)
 * @param premium the premium per payment interval, or {@code null} if the persona does not pay it
 * @param intervalMonths the payment interval in months, or {@code null} without a premium
 * @param nextDueDate the stored next due date, or {@code null} without a premium
 */
@NamedInterface("results")
public record InsuranceContract(String id, Cover cover, String policyholder, boolean coInsured, BigDecimal premium,
        Integer intervalMonths, LocalDate nextDueDate) {

    /** The risk a contract covers, with its wording for customers. */
    @NamedInterface("results")
    public enum Cover {
        LIABILITY("liability", "Private liability insurance", true),
        CAR("car", "Car insurance", true),
        HOUSEHOLD_CONTENTS("household contents", "Household contents insurance", true),
        OCCUPATIONAL_DISABILITY("occupational disability", "Occupational disability insurance", true),
        LEGAL_PROTECTION("legal protection", "Legal protection insurance", true),
        RIESTER_PENSION("Riester pension", "Riester pension", false);

        private final String plainWords;
        private final String typeName;
        private final boolean insurance;

        Cover(String plainWords, String typeName, boolean insurance) {
            this.plainWords = plainWords;
            this.typeName = typeName;
            this.insurance = insurance;
        }

        /** The cover in running text, e.g. "liability" in "You may be paying twice for liability insurance". */
        public String plainWords() {
            return plainWords;
        }

        /** The contract type in plain words, e.g. "Private liability insurance". */
        public String typeName() {
            return typeName;
        }

        /** {@code false} for pension contracts, which insure no risk and never count as cover. */
        public boolean insurance() {
            return insurance;
        }
    }

    public InsuranceContract {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cover, "cover");
        Objects.requireNonNull(policyholder, "policyholder");
        if (premium != null && (intervalMonths == null || nextDueDate == null)) {
            throw new IllegalArgumentException("a premium needs a payment interval and a due date: " + id);
        }
        if (intervalMonths != null && intervalMonths < 1) {
            throw new IllegalArgumentException("payment interval must be at least one month: " + id);
        }
    }

    /** "monthly", "quarterly", "yearly" or "every n months"; {@code null} without a payment interval. */
    public String intervalWords() {
        if (intervalMonths == null) {
            return null;
        }
        return switch (intervalMonths) {
            case 1 -> "monthly";
            case 3 -> "quarterly";
            case 12 -> "yearly";
            default -> String.format(Locale.ROOT, "every %d months", intervalMonths);
        };
    }

    /** The contract as cited in a flag's inputs, e.g. "Private liability insurance, policyholder Jonas". */
    String citation() {
        return cover.typeName() + (coInsured ? ", co-insured on the policy of " + policyholder
                : ", policyholder " + policyholder);
    }
}
