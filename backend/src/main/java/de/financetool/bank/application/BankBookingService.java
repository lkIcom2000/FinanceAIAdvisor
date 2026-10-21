package de.financetool.bank.application;

import de.financetool.bank.AccountType;
import de.financetool.bank.BankPort;
import de.financetool.bank.TransactionCategory;
import de.financetool.bank.TransactionOrigin;
import de.financetool.bank.adapter.AccountEntity;
import de.financetool.bank.adapter.BankJpaRepository;
import de.financetool.bank.adapter.BankRowWriter;
import de.financetool.bank.adapter.BankRowWriter.TransactionRow;
import de.financetool.shared.domain.PersonaId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Books transfers between the persona's own accounts. Booking ids are derived from the persona and the number of
 * earlier {@code BANK_PORT} bookings, so a replayed session books the same ids (no unseeded randomness).
 */
@Service
class BankBookingService implements BankPort {

    static final int MAX_REFERENCE_LENGTH = 200;

    private final BankJpaRepository bank;
    private final BankRowWriter writer;

    BankBookingService(BankJpaRepository bank, BankRowWriter writer) {
        this.bank = bank;
        this.writer = writer;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Transfer transfer(PersonaId personaId, UUID fromAccountId, UUID toAccountId, BigDecimal amount,
            LocalDate bookingDate, String reference) {
        Objects.requireNonNull(bookingDate, "bookingDate");
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive: " + amount);
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Transfer amount has more than two decimals: " + amount);
        }
        if (reference == null || reference.isBlank() || reference.length() > MAX_REFERENCE_LENGTH) {
            throw new IllegalArgumentException("Transfer reference must have 1 to " + MAX_REFERENCE_LENGTH
                    + " characters");
        }
        if (Objects.equals(fromAccountId, toAccountId)) {
            throw new IllegalArgumentException("Transfer needs two different accounts");
        }
        AccountEntity from = ownCashAccount(personaId, fromAccountId);
        AccountEntity to = ownCashAccount(personaId, toAccountId);
        BigDecimal value = amount.setScale(2, RoundingMode.UNNECESSARY);
        long booked = bank.countTransactions(personaId.value(), TransactionOrigin.BANK_PORT);
        UUID debitId = bookingId(personaId, booked);
        UUID creditId = bookingId(personaId, booked + 1);
        writer.insertTransactions(List.of(
                new TransactionRow(debitId, personaId.value(), from.getId(), bookingDate, value.negate(),
                        TransactionCategory.TRANSFER.name(), to.getName(), reference, null, null,
                        TransactionOrigin.BANK_PORT),
                new TransactionRow(creditId, personaId.value(), to.getId(), bookingDate, value,
                        TransactionCategory.TRANSFER.name(), from.getName(), reference, null, null,
                        TransactionOrigin.BANK_PORT)));
        return new Transfer(debitId, creditId);
    }

    private AccountEntity ownCashAccount(PersonaId personaId, UUID accountId) {
        AccountEntity account = accountId == null ? null : bank.account(personaId.value(), accountId).orElse(null);
        if (account == null) {
            throw new IllegalArgumentException("Account " + accountId + " does not belong to " + personaId);
        }
        if (account.getType() == AccountType.DEPOT) {
            throw new IllegalArgumentException("Transfers to or from a depot are not supported: " + accountId);
        }
        return account;
    }

    private static UUID bookingId(PersonaId personaId, long sequence) {
        return UUID.nameUUIDFromBytes(("fine:bank-port:" + personaId.value() + ":" + sequence)
                .getBytes(StandardCharsets.UTF_8));
    }
}
