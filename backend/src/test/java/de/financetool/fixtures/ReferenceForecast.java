package de.financetool.fixtures;

import de.financetool.shared.fixtures.PersonaFixture;
import de.financetool.shared.fixtures.PersonaFixture.Account;
import de.financetool.shared.fixtures.PersonaFixture.Contract;
import de.financetool.shared.fixtures.PersonaFixture.Transaction;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Test-side reference of the forecast model decided in Story 2.1 (Design Notes), which Story 2.3's
 * {@code CashFlowForecast} median must reproduce. Start: the current-account balance at the end of {@code asOf}.
 * For each later day: recurring flows due that day (day clamped to the month length), minus V / days in month (V =
 * mean variable spending of the last 3 complete months), then contract premiums due that day. Rounded HALF_UP to
 * cents only at the end. Variable spending: current-account bookings without {@code recurrenceId} and
 * {@code contractId}, except transfers between own accounts.
 */
final class ReferenceForecast {

    /** A monthly flow as last booked before {@code asOf}. */
    record Recurrence(String id, int day, BigDecimal amount, String category) {
    }

    private final PersonaFixture fixture;
    private final LocalDate asOf;
    private final Account current;

    ReferenceForecast(PersonaFixture fixture, LocalDate asOf) {
        this.fixture = fixture;
        this.asOf = asOf;
        List<Account> currents = fixture.accounts().stream().filter(a -> a.type().equals("CURRENT")).toList();
        if (currents.size() != 1) {
            throw new IllegalStateException(fixture.personaId() + " needs exactly one current account");
        }
        this.current = currents.getFirst();
    }

    ReferenceForecast(PersonaFixture fixture) {
        this(fixture, fixture.simulatedDate());
    }

    Account current() {
        return current;
    }

    /** Balance of {@code accountId} at the end of {@code asOf}: opening balance plus bookings up to then. */
    BigDecimal balance(UUID accountId) {
        Account account = fixture.accounts().stream().filter(a -> a.id().equals(accountId)).findFirst().orElseThrow();
        return account.openingBalance().add(fixture.transactions().stream()
                .filter(t -> t.accountId().equals(accountId) && !t.bookingDate().isAfter(asOf))
                .map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** The current account's recurring flows, each with its latest amount and day of month. */
    List<Recurrence> recurrences() {
        Map<String, Recurrence> latest = new LinkedHashMap<>();
        currentAccountBookings().stream().filter(t -> t.recurrenceId() != null)
                .forEach(t -> latest.put(t.recurrenceId(), new Recurrence(t.recurrenceId(),
                        t.bookingDate().getDayOfMonth(), t.amount(), t.category())));
        return List.copyOf(latest.values());
    }

    /** Variable spending in {@code month} (positive). */
    BigDecimal variableSpending(YearMonth month) {
        return currentAccountBookings().stream()
                .filter(ReferenceForecast::isVariable)
                .filter(t -> YearMonth.from(t.bookingDate()).equals(month))
                .map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add).negate();
    }

    /** V: mean variable spending per month of the last three complete months before {@code asOf}. */
    BigDecimal variableMonthly() {
        YearMonth lastComplete = asOf.equals(YearMonth.from(asOf).atEndOfMonth())
                ? YearMonth.from(asOf) : YearMonth.from(asOf).minusMonths(1);
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < 3; i++) {
            sum = sum.add(variableSpending(lastComplete.minusMonths(i)));
        }
        return sum.divide(BigDecimal.valueOf(3), MathContext.DECIMAL128);
    }

    /** Forecast current-account balance at the end of {@code target}, rounded to cents. */
    BigDecimal balanceOn(LocalDate target, BigDecimal premiumOverride, boolean withVariableSpending) {
        BigDecimal balance = balance(current.id());
        List<Recurrence> recurrences = recurrences();
        BigDecimal variable = withVariableSpending ? variableMonthly() : BigDecimal.ZERO;
        for (LocalDate day = asOf.plusDays(1); !day.isAfter(target); day = day.plusDays(1)) {
            int length = YearMonth.from(day).lengthOfMonth();
            for (Recurrence r : recurrences) {
                if (day.getDayOfMonth() == Math.min(r.day(), length)) {
                    balance = balance.add(r.amount());
                }
            }
            balance = balance.subtract(variable.divide(BigDecimal.valueOf(length), MathContext.DECIMAL128));
            for (Contract c : premiumContracts()) {
                if (isDue(c, day)) {
                    BigDecimal premium = premiumOverride != null && day.equals(target) ? premiumOverride : c.premium();
                    balance = balance.subtract(premium);
                }
            }
        }
        return balance.setScale(2, RoundingMode.HALF_UP);
    }

    /** Contracts whose premium is debited from the current account. */
    List<Contract> premiumContracts() {
        return fixture.contracts().stream()
                .filter(c -> c.premium() != null && current.id().equals(c.debitAccountId())).toList();
    }

    static int intervalMonths(Contract contract) {
        return switch (contract.paymentInterval()) {
            case "MONTHLY" -> 1;
            case "QUARTERLY" -> 3;
            case "ANNUAL" -> 12;
            default -> throw new IllegalArgumentException(contract.paymentInterval());
        };
    }

    static boolean isVariable(Transaction t) {
        return t.recurrenceId() == null && t.contractId() == null && !t.category().equals("TRANSFER");
    }

    private boolean isDue(Contract contract, LocalDate day) {
        int months = intervalMonths(contract);
        for (int k = 0; ; k++) {
            LocalDate due = contract.nextDueDate().plusMonths((long) k * months);
            if (due.isAfter(day)) {
                return false;
            }
            if (due.equals(day)) {
                return true;
            }
        }
    }

    private List<Transaction> currentAccountBookings() {
        return fixture.transactions().stream()
                .filter(t -> t.accountId().equals(current.id()) && !t.bookingDate().isAfter(asOf)).toList();
    }
}
