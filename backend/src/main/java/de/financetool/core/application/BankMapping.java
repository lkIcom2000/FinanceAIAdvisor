package de.financetool.core.application;

import de.financetool.bank.AssetClass;
import de.financetool.bank.BankSnapshot;
import de.financetool.bank.InsuredRole;
import de.financetool.core.domain.AccountBalance;
import de.financetool.core.domain.CashFlowForecast;
import de.financetool.core.domain.InsuranceContract;
import de.financetool.core.domain.LedgerEntry;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The bank snapshot as the Core's pure functions take it; the gateway's only mapping (tests call it, too). */
final class BankMapping {

    private BankMapping() {
    }

    /** The bookings of {@code accountId}, or of every account if {@code null}. */
    static List<LedgerEntry> entries(BankSnapshot snapshot, UUID accountId) {
        return snapshot.transactions().stream()
                .filter(t -> accountId == null || t.accountId().equals(accountId))
                .map(BankMapping::entry)
                .toList();
    }

    static List<AccountBalance.Booking> bookings(BankSnapshot snapshot, UUID accountId) {
        return snapshot.transactions().stream()
                .filter(t -> t.accountId().equals(accountId))
                .map(t -> new AccountBalance.Booking(t.bookingDate(), t.amount()))
                .toList();
    }

    /** The complete premiums debited from {@code accountId}. */
    static List<CashFlowForecast.Premium> premiums(BankSnapshot snapshot, UUID accountId) {
        return snapshot.contracts().stream()
                .filter(c -> c.premium() != null && c.paymentInterval() != null && c.nextDueDate() != null
                        && accountId.equals(c.debitAccountId()))
                .map(c -> new CashFlowForecast.Premium(premiumDescription(c), c.premium(),
                        c.paymentInterval().months(), c.nextDueDate()))
                .toList();
    }

    /** The contracts started by the snapshot's {@code asOf}. */
    static List<InsuranceContract> contracts(BankSnapshot snapshot) {
        return snapshot.contracts().stream()
                .filter(c -> c.startDate() == null || !c.startDate().isAfter(snapshot.asOf()))
                .map(BankMapping::insuranceContract)
                .toList();
    }

    /** A contract without a complete premium (e.g. co-insured on a family policy) has no premium figure. */
    private static InsuranceContract insuranceContract(BankSnapshot.Contract c) {
        boolean paid = c.premium() != null && c.paymentInterval() != null && c.nextDueDate() != null;
        return new InsuranceContract(c.id().toString(), InsuranceContract.Cover.valueOf(c.coverage().name()),
                c.policyholder(), c.insuredRole() == InsuredRole.CO_INSURED, paid ? c.premium() : null,
                paid ? c.paymentInterval().months() : null, paid ? c.nextDueDate() : null);
    }

    private static LedgerEntry entry(BankSnapshot.Transaction t) {
        return new LedgerEntry(t.bookingDate(), t.amount(), t.category() == null ? null : t.category().name(),
                t.reference() != null ? t.reference() : t.counterparty(), t.recurrenceId(), t.contractId() != null);
    }

    static String assetClass(AssetClass assetClass) {
        return switch (assetClass) {
            case EQUITY_ETF -> "Equity ETF";
            case BOND_ETF -> "Bond ETF";
            case MONEY_MARKET_FUND -> "Money market fund";
        };
    }

    private static String premiumDescription(BankSnapshot.Contract contract) {
        String coverage = switch (contract.coverage()) {
            case LIABILITY -> "Private liability insurance";
            case CAR -> "Car insurance";
            case HOUSEHOLD_CONTENTS -> "Household contents insurance";
            case OCCUPATIONAL_DISABILITY -> "Occupational disability insurance";
            case LEGAL_PROTECTION -> "Legal protection insurance";
            case RIESTER_PENSION -> "Riester pension";
        };
        return coverage + ", " + contract.paymentInterval().name().toLowerCase(Locale.ROOT) + " premium";
    }
}
