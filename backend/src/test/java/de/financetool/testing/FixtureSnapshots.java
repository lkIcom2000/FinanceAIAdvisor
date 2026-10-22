package de.financetool.testing;

import de.financetool.bank.AccountType;
import de.financetool.bank.AssetClass;
import de.financetool.bank.BankSnapshot;
import de.financetool.bank.Coverage;
import de.financetool.bank.InsuredRole;
import de.financetool.bank.PaymentInterval;
import de.financetool.bank.TransactionCategory;
import de.financetool.bank.TransactionOrigin;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.fixtures.PersonaFixture;
import java.time.LocalDate;
import java.util.Comparator;

/**
 * A persona fixture as the bank snapshot at {@code asOf}, without a database (Epic 2 retro A5): the same rows the
 * seed writes and the {@code asOf} filters of {@code BankJpaRepository}, so Core tests run the gateway's own mapping.
 * {@code Account.balance} stays {@code null}; the Core computes balances itself.
 */
public final class FixtureSnapshots {

    private FixtureSnapshots() {
    }

    public static BankSnapshot of(PersonaFixture fixture, LocalDate asOf) {
        return new BankSnapshot(new PersonaId(fixture.personaId()), asOf,
                fixture.accounts().stream()
                        .filter(a -> !a.openingDate().isAfter(asOf))
                        .map(a -> new BankSnapshot.Account(a.id(), AccountType.valueOf(a.type()), a.name(), a.iban(),
                                a.openingDate(), a.openingBalance(), null))
                        .toList(),
                fixture.contracts().stream()
                        .filter(c -> c.startDate() != null && !c.startDate().isAfter(asOf))
                        .map(c -> new BankSnapshot.Contract(c.id(), Coverage.valueOf(c.coverage()), c.provider(),
                                c.policyNumber(), c.policyholder(), InsuredRole.valueOf(c.insuredRole()), c.premium(),
                                c.paymentInterval() == null ? null : PaymentInterval.valueOf(c.paymentInterval()),
                                c.nextDueDate(), c.debitAccountId(), c.startDate()))
                        .toList(),
                fixture.transactions().stream()
                        .filter(t -> !t.bookingDate().isAfter(asOf))
                        .sorted(Comparator.comparing(PersonaFixture.Transaction::bookingDate))
                        .map(t -> new BankSnapshot.Transaction(t.id(), t.accountId(), t.bookingDate(), t.amount(),
                                t.category() == null ? null : TransactionCategory.valueOf(t.category()),
                                t.counterparty(), t.reference(), t.recurrenceId(), t.contractId(),
                                TransactionOrigin.FIXTURE))
                        .toList(),
                fixture.holdings().stream()
                        .filter(h -> !h.valuationDate().isAfter(asOf))
                        .map(h -> new BankSnapshot.Holding(h.id(), h.accountId(), h.name(),
                                AssetClass.valueOf(h.assetClass()), h.units(), h.unitPrice(), h.valuationDate(),
                                h.costBasis()))
                        .toList(),
                fixture.savingsPlans().stream()
                        .filter(p -> !p.startDate().isAfter(asOf))
                        .map(p -> new BankSnapshot.SavingsPlan(p.id(), p.accountId(), p.debitAccountId(), p.name(),
                                AssetClass.valueOf(p.assetClass()), p.amount(), p.dayOfMonth(), p.startDate(),
                                p.recurrenceId()))
                        .toList());
    }
}
