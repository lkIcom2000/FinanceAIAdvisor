package de.financetool.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.PostgresIntegrationTest;
import de.financetool.research.application.PersonaFixtureLoader;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.fixtures.PersonaFixture;
import de.financetool.shared.fixtures.PersonaFixtureCatalog;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** {@link BankReadPort} snapshots filter by {@code asOf}; {@link BankPort} books transfers between own accounts. */
class BankSnapshotIntegrationTest extends PostgresIntegrationTest {

    static final PersonaId LENA = new PersonaId("p-lena");
    static final LocalDate START = LocalDate.of(2026, 10, 15);

    @Autowired
    BankReadPort bank;

    @Autowired
    BankPort bankPort;

    @Autowired
    PersonaFixtureCatalog fixtures;

    @Autowired
    PersonaFixtureLoader loader;

    @Autowired
    TransactionTemplate tx;

    static final PersonaId JONAS = new PersonaId("p-jonas");

    @AfterEach
    void restoreLena() {
        tx.executeWithoutResult(status -> {
            loader.load(LENA);
            loader.load(JONAS);
        });
    }

    @Test
    void holdingsSavingsPlansAndCoInsuredContractsAreReadAndFilteredByAsOf() {
        BankSnapshot jonas = bank.snapshot(JONAS, START);
        UUID depot = jonas.accounts().stream().filter(a -> a.type() == AccountType.DEPOT).findFirst().orElseThrow()
                .id();

        assertThat(jonas.holdings()).singleElement().satisfies(h -> {
            assertThat(h.accountId()).isEqualTo(depot);
            assertThat(h.units()).isEqualByComparingTo("17.842300");
            assertThat(h.unitPrice().toPlainString()).isEqualTo("112.36");
            assertThat(h.costBasis().toPlainString()).isEqualTo("1900.00");
            assertThat(h.assetClass()).isEqualTo(AssetClass.EQUITY_ETF);
        });
        assertThat(jonas.savingsPlans()).singleElement().satisfies(p -> {
            assertThat(p.accountId()).isEqualTo(depot);
            assertThat(p.debitAccountId()).isEqualTo(current(jonas).id());
            assertThat(p.dayOfMonth()).isEqualTo(15);
            assertThat(p.amount().toPlainString()).isEqualTo("100.00");
        });
        assertThat(jonas.contracts()).filteredOn(c -> c.insuredRole() == InsuredRole.CO_INSURED).singleElement()
                .satisfies(family -> {
                    assertThat(family.coverage()).isEqualTo(Coverage.LIABILITY);
                    assertThat(family.premium()).isNull();
                    assertThat(family.paymentInterval()).isNull();
                    assertThat(family.nextDueDate()).isNull();
                    assertThat(family.debitAccountId()).isNull();
                });

        assertThat(bank.snapshot(JONAS, START.minusDays(1)).holdings()).as("before the valuation date").isEmpty();
        assertThat(bank.snapshot(JONAS, LocalDate.of(2025, 4, 14)).savingsPlans()).as("before the plan start")
                .isEmpty();
        assertThat(bank.snapshot(JONAS, LocalDate.of(2024, 1, 31)).contracts()).as("before the own policy started")
                .singleElement().satisfies(c -> assertThat(c.insuredRole()).isEqualTo(InsuredRole.CO_INSURED));
    }

    @Test
    void invalidTransfersBookNothing() {
        BankSnapshot before = bank.snapshot(JONAS, START);
        UUID current = current(before).id();
        UUID overnight = overnight(before).id();
        UUID depot = before.accounts().stream().filter(a -> a.type() == AccountType.DEPOT).findFirst().orElseThrow()
                .id();
        record Attempt(String what, UUID from, UUID to, String amount, LocalDate date, String reference) {
        }
        List<Attempt> attempts = List.of(
                new Attempt("same account", current, current, "10.00", START, "Same"),
                new Attempt("depot", current, depot, "10.00", START, "Depot"),
                new Attempt("scale > 2", overnight, current, "10.001", START, "Scale"),
                new Attempt("blank reference", overnight, current, "10.00", START, "  "),
                new Attempt("reference over 200", overnight, current, "10.00", START, "x".repeat(201)),
                new Attempt("before opening", overnight, current, "10.00", LocalDate.of(2025, 6, 30), "Early"));
        for (Attempt a : attempts) {
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> bankPort.transfer(JONAS, a.from(), a.to(),
                    new BigDecimal(a.amount()), a.date(), a.reference())))
                    .as(a.what()).isInstanceOf(IllegalArgumentException.class);
        }

        assertThat(bank.snapshot(JONAS, START)).as("nothing booked").isEqualTo(before);
    }

    @Test
    void theSnapshotOnTheStartDateHoldsTheWholeFixture() {
        PersonaFixture fixture = fixtures.fixture(LENA);
        BankSnapshot snapshot = bank.snapshot(LENA, START);

        assertThat(snapshot.transactions()).hasSize(fixture.transactions().size())
                .allSatisfy(t -> assertThat(t.origin()).isEqualTo(TransactionOrigin.FIXTURE))
                .allSatisfy(t -> assertThat(t.amount().scale()).isEqualTo(2));
        assertThat(snapshot.contracts()).hasSize(fixture.contracts().size());
        assertThat(snapshot.accounts()).extracting(BankSnapshot.Account::type)
                .containsExactly(AccountType.CURRENT, AccountType.OVERNIGHT);
        assertThat(current(snapshot).balance()).isEqualByComparingTo("1974.00");
        assertThat(overnight(snapshot).balance()).isEqualByComparingTo(expectedBalance(fixture, overnight(snapshot).id(),
                START));
        assertThat(snapshot.contracts()).filteredOn(c -> c.coverage() == Coverage.CAR).singleElement()
                .satisfies(car -> {
                    assertThat(car.premium()).isEqualByComparingTo("620.00");
                    assertThat(car.paymentInterval()).isEqualTo(PaymentInterval.ANNUAL);
                    assertThat(car.nextDueDate()).isEqualTo(LocalDate.of(2026, 11, 1));
                });
    }

    @Test
    void bookingsAfterAsOfAreExcludedAndBalancesFollowAsOf() {
        PersonaFixture fixture = fixtures.fixture(LENA);
        LocalDate asOf = LocalDate.of(2026, 10, 2);
        BankSnapshot snapshot = bank.snapshot(LENA, asOf);

        assertThat(snapshot.asOf()).isEqualTo(asOf);
        assertThat(snapshot.transactions()).isNotEmpty()
                .allSatisfy(t -> assertThat(t.bookingDate()).isBeforeOrEqualTo(asOf))
                .noneMatch(t -> t.category() == TransactionCategory.SALARY
                        && t.bookingDate().equals(LocalDate.of(2026, 10, 3)));
        assertThat(current(snapshot).balance())
                .isEqualByComparingTo(expectedBalance(fixture, current(snapshot).id(), asOf));

        BankSnapshot beforeHistory = bank.snapshot(LENA, LocalDate.of(2025, 6, 30));
        assertThat(beforeHistory.accounts()).isEmpty();
        assertThat(beforeHistory.transactions()).isEmpty();
    }

    @Test
    void aTransferBooksTwoLegsOnItsDate() {
        BankSnapshot before = bank.snapshot(LENA, START);
        UUID current = current(before).id();
        UUID overnight = overnight(before).id();

        BankPort.Transfer transfer = tx.execute(status -> bankPort.transfer(LENA, overnight, current,
                new BigDecimal("200.00"), START, "Cover the forecast shortfall"));

        BankSnapshot after = bank.snapshot(LENA, START);
        assertThat(current(after).balance()).isEqualByComparingTo(current(before).balance().add(new BigDecimal("200")));
        assertThat(overnight(after).balance())
                .isEqualByComparingTo(overnight(before).balance().subtract(new BigDecimal("200")));
        assertThat(after.transactions()).filteredOn(t -> t.origin() == TransactionOrigin.BANK_PORT)
                .extracting(BankSnapshot.Transaction::id, BankSnapshot.Transaction::category,
                        t -> t.amount().toPlainString())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(transfer.debitTransactionId(), TransactionCategory.TRANSFER,
                                "-200.00"),
                        org.assertj.core.groups.Tuple.tuple(transfer.creditTransactionId(),
                                TransactionCategory.TRANSFER, "200.00"));
        assertThat(bank.snapshot(LENA, START.minusDays(1)).transactions())
                .noneMatch(t -> t.origin() == TransactionOrigin.BANK_PORT);
    }

    @Test
    void anotherPersonasAccountOrANonPositiveAmountBooksNothing() {
        BankSnapshot lena = bank.snapshot(LENA, START);
        UUID marcosAccount = current(bank.snapshot(new PersonaId("p-marco"), START)).id();
        UUID current = current(lena).id();
        UUID overnight = overnight(lena).id();

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> bankPort.transfer(LENA, marcosAccount, current,
                new BigDecimal("50.00"), START, "Not mine")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(marcosAccount.toString());
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> bankPort.transfer(LENA, overnight, marcosAccount,
                new BigDecimal("50.00"), START, "Not mine")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> bankPort.transfer(LENA, overnight, current,
                BigDecimal.ZERO, START, "Nothing")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> bankPort.transfer(LENA, overnight, current,
                new BigDecimal("-10.00"), START, "Negative")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(bank.snapshot(LENA, START)).isEqualTo(lena);
        assertThat(bank.snapshot(new PersonaId("p-marco"), START).transactions())
                .noneMatch(t -> t.origin() == TransactionOrigin.BANK_PORT);
    }

    @Test
    void bookingsNeedTheCallersTransaction() {
        BankSnapshot lena = bank.snapshot(LENA, START);
        assertThatThrownBy(() -> bankPort.transfer(LENA, overnight(lena).id(), current(lena).id(),
                new BigDecimal("10.00"), START, "No transaction"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private static BigDecimal expectedBalance(PersonaFixture fixture, UUID accountId, LocalDate asOf) {
        PersonaFixture.Account account = fixture.accounts().stream().filter(a -> a.id().equals(accountId))
                .findFirst().orElseThrow();
        return account.openingBalance().add(fixture.transactions().stream()
                .filter(t -> t.accountId().equals(accountId) && !t.bookingDate().isAfter(asOf))
                .map(PersonaFixture.Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BankSnapshot.Account current(BankSnapshot snapshot) {
        return snapshot.accounts().stream().filter(a -> a.type() == AccountType.CURRENT).findFirst().orElseThrow();
    }

    private static BankSnapshot.Account overnight(BankSnapshot snapshot) {
        return snapshot.accounts().stream().filter(a -> a.type() == AccountType.OVERNIGHT).findFirst().orElseThrow();
    }
}
