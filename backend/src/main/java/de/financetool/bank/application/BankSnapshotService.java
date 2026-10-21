package de.financetool.bank.application;

import de.financetool.bank.BankReadPort;
import de.financetool.bank.BankSnapshot;
import de.financetool.bank.adapter.AccountEntity;
import de.financetool.bank.adapter.BankJpaRepository;
import de.financetool.bank.adapter.TransactionEntity;
import de.financetool.shared.domain.PersonaId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Snapshot of the persona's bank data as of a date; balances are opening balance plus bookings up to that date. */
@Service
class BankSnapshotService implements BankReadPort {

    private final BankJpaRepository bank;

    BankSnapshotService(BankJpaRepository bank) {
        this.bank = bank;
    }

    @Override
    public BankSnapshot snapshot(PersonaId personaId, LocalDate asOf) {
        Objects.requireNonNull(personaId, "personaId");
        Objects.requireNonNull(asOf, "asOf");
        String id = personaId.value();
        List<TransactionEntity> transactions = bank.transactions(id, asOf);
        Map<UUID, BigDecimal> movements = new HashMap<>();
        for (TransactionEntity t : transactions) {
            movements.merge(t.getAccountId(), t.getAmount(), BigDecimal::add);
        }
        List<BankSnapshot.Account> accounts = bank.accounts(id, asOf).stream()
                .sorted(Comparator.comparing(AccountEntity::getType).thenComparing(AccountEntity::getName))
                .map(a -> new BankSnapshot.Account(a.getId(), a.getType(), a.getName(), a.getIban(),
                        a.getOpeningDate(), money(a.getOpeningBalance()),
                        money(a.getOpeningBalance().add(movements.getOrDefault(a.getId(), BigDecimal.ZERO)))))
                .toList();
        return new BankSnapshot(personaId, asOf, accounts,
                bank.contracts(id, asOf).stream()
                        .map(c -> new BankSnapshot.Contract(c.getId(), c.getCoverage(), c.getProvider(),
                                c.getPolicyNumber(), c.getPolicyholder(), c.getInsuredRole(),
                                c.getPremium() == null ? null : money(c.getPremium()), c.getPaymentInterval(),
                                c.getNextDueDate(), c.getDebitAccountId(), c.getStartDate()))
                        .toList(),
                transactions.stream()
                        .map(t -> new BankSnapshot.Transaction(t.getId(), t.getAccountId(), t.getBookingDate(),
                                money(t.getAmount()), t.getCategory(), t.getCounterparty(), t.getReference(),
                                t.getRecurrenceId(), t.getContractId(), t.getOrigin()))
                        .toList(),
                bank.holdings(id, asOf).stream()
                        .map(h -> new BankSnapshot.Holding(h.getId(), h.getAccountId(), h.getName(),
                                h.getAssetClass(), h.getUnits(), money(h.getUnitPrice()), h.getValuationDate(),
                                money(h.getCostBasis())))
                        .toList(),
                bank.savingsPlans(id, asOf).stream()
                        .map(s -> new BankSnapshot.SavingsPlan(s.getId(), s.getAccountId(), s.getDebitAccountId(),
                                s.getName(), s.getAssetClass(), money(s.getAmount()), s.getDayOfMonth(),
                                s.getStartDate(), s.getRecurrenceId()))
                        .toList());
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
