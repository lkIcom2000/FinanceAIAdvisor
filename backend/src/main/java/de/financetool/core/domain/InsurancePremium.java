package de.financetool.core.domain;

import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Core function {@code contractPremium}: a contract's premium and its next due date after {@code asOf}. Pure. */
public final class InsurancePremium {

    public static final String FUNCTION = "contractPremium";
    public static final String OUTPUT = "premium";

    /** The computation to key and the next due date (the stored date rolled forward past {@code asOf}). */
    public record Result(CoreComputation computation, LocalDate nextDueDate) {

        public Result {
            Objects.requireNonNull(computation, "computation");
            Objects.requireNonNull(nextDueDate, "nextDueDate");
        }
    }

    private InsurancePremium() {
    }

    /** The first due date after {@code asOf}: the stored date rolled forward by whole payment intervals. */
    public static LocalDate nextDueAfter(LocalDate stored, int intervalMonths, LocalDate asOf) {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(asOf, "asOf");
        if (intervalMonths < 1) {
            throw new IllegalArgumentException("payment interval must be at least one month");
        }
        int k = 0;
        while (!stored.plusMonths((long) k * intervalMonths).isAfter(asOf)) {
            k++;
        }
        return stored.plusMonths((long) k * intervalMonths);
    }

    /** @throws IllegalArgumentException for a contract without a premium (it has no premium figure) */
    public static Result of(InsuranceContract contract, LocalDate asOf) {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(asOf, "asOf");
        if (contract.premium() == null) {
            throw new IllegalArgumentException("contract " + contract.id() + " has no premium");
        }
        BigDecimal premium = contract.premium().setScale(2, RoundingMode.HALF_UP);
        LocalDate next = nextDueAfter(contract.nextDueDate(), contract.intervalMonths(), asOf);

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("contract", contract.id());
        keyInputs.put("cover", contract.cover());
        keyInputs.put("premium", premium);
        keyInputs.put("intervalMonths", contract.intervalMonths());
        keyInputs.put("storedDueDate", contract.nextDueDate());
        keyInputs.put("nextDueDate", next);

        String type = contract.cover().typeName();
        String interval = contract.intervalWords();
        return new Result(new CoreComputation(FUNCTION, "contract premium",
                "Based on your " + type + " contract data.", keyInputs,
                List.of(FigureInput.text("Contract", type),
                        FigureInput.text("Payment interval", Character.toUpperCase(interval.charAt(0))
                                + interval.substring(1)),
                        FigureInput.text("Due date on file", Wording.date(contract.nextDueDate())),
                        FigureInput.text("Next due date", Wording.date(next))),
                List.of(new Figure(OUTPUT, "Premium for " + type, premium, Unit.EUR)),
                List.of("The premium stays the same until the insurer tells you otherwise.",
                        "The next due date is the due date on file, moved on by whole payment intervals until it is"
                                + " after " + Wording.date(asOf) + "."),
                Map.of()), next);
    }
}
