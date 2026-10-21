package de.financetool.bank;

/** Where a booking comes from: the persona fixture, or a later {@link BankPort} booking. */
public enum TransactionOrigin {
    FIXTURE, BANK_PORT
}
