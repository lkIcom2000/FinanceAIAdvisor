package de.financetool.bank.adapter;

import de.financetool.bank.TransactionOrigin;
import de.financetool.shared.fixtures.PersonaFixture;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Inserts into the five bank tables: fixture rows as JDBC batches, single {@code BankPort} bookings. */
@Repository
public class BankRowWriter {

    /** A transaction row as stored. */
    public record TransactionRow(UUID id, String personaId, UUID accountId, LocalDate bookingDate, BigDecimal amount,
            String category, String counterparty, String reference, String recurrenceId, UUID contractId,
            TransactionOrigin origin) {
    }

    private static final String INSERT_TRANSACTION = """
            INSERT INTO transactions (id, persona_id, account_id, booking_date, amount, category, counterparty,
                                      reference, recurrence_id, contract_id, origin)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private final JdbcTemplate jdbc;

    BankRowWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts every row of the fixture, parents first. */
    public void insertFixture(PersonaFixture fixture) {
        String personaId = fixture.personaId();
        jdbc.batchUpdate("""
                INSERT INTO accounts (id, persona_id, type, name, iban, opening_date, opening_balance)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", fixture.accounts(), 100, (ps, a) -> {
            ps.setObject(1, a.id());
            ps.setString(2, personaId);
            ps.setString(3, a.type());
            ps.setString(4, a.name());
            ps.setString(5, a.iban());
            ps.setDate(6, Date.valueOf(a.openingDate()));
            ps.setBigDecimal(7, a.openingBalance());
        });
        jdbc.batchUpdate("""
                INSERT INTO contracts (id, persona_id, coverage, provider, policy_number, policyholder, insured_role,
                                       premium, payment_interval, next_due_date, debit_account_id, start_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", fixture.contracts(), 100, (ps, c) -> {
            ps.setObject(1, c.id());
            ps.setString(2, personaId);
            ps.setString(3, c.coverage());
            ps.setString(4, c.provider());
            ps.setString(5, c.policyNumber());
            ps.setString(6, c.policyholder());
            ps.setString(7, c.insuredRole());
            ps.setBigDecimal(8, c.premium());
            ps.setString(9, c.paymentInterval());
            ps.setObject(10, c.nextDueDate() == null ? null : Date.valueOf(c.nextDueDate()), Types.DATE);
            ps.setObject(11, c.debitAccountId(), Types.OTHER);
            ps.setDate(12, Date.valueOf(c.startDate()));
        });
        List<TransactionRow> transactions = fixture.transactions().stream()
                .map(t -> new TransactionRow(t.id(), personaId, t.accountId(), t.bookingDate(), t.amount(),
                        t.category(), t.counterparty(), t.reference(), t.recurrenceId(), t.contractId(),
                        TransactionOrigin.FIXTURE))
                .toList();
        insertTransactions(transactions);
        jdbc.batchUpdate("""
                INSERT INTO holdings (id, persona_id, account_id, name, asset_class, units, unit_price,
                                      valuation_date, cost_basis)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", fixture.holdings(), 100, (ps, h) -> {
            ps.setObject(1, h.id());
            ps.setString(2, personaId);
            ps.setObject(3, h.accountId());
            ps.setString(4, h.name());
            ps.setString(5, h.assetClass());
            ps.setBigDecimal(6, h.units());
            ps.setBigDecimal(7, h.unitPrice());
            ps.setDate(8, Date.valueOf(h.valuationDate()));
            ps.setBigDecimal(9, h.costBasis());
        });
        jdbc.batchUpdate("""
                INSERT INTO savings_plans (id, persona_id, account_id, debit_account_id, name, asset_class, amount,
                                           day_of_month, start_date, recurrence_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", fixture.savingsPlans(), 100, (ps, s) -> {
            ps.setObject(1, s.id());
            ps.setString(2, personaId);
            ps.setObject(3, s.accountId());
            ps.setObject(4, s.debitAccountId());
            ps.setString(5, s.name());
            ps.setString(6, s.assetClass());
            ps.setBigDecimal(7, s.amount());
            ps.setShort(8, (short) s.dayOfMonth());
            ps.setDate(9, Date.valueOf(s.startDate()));
            ps.setString(10, s.recurrenceId());
        });
    }

    public void insertTransactions(List<TransactionRow> rows) {
        jdbc.batchUpdate(INSERT_TRANSACTION, rows, 500, (ps, t) -> {
            ps.setObject(1, t.id());
            ps.setString(2, t.personaId());
            ps.setObject(3, t.accountId());
            ps.setDate(4, Date.valueOf(t.bookingDate()));
            ps.setBigDecimal(5, t.amount());
            ps.setString(6, t.category());
            ps.setString(7, t.counterparty());
            ps.setString(8, t.reference());
            ps.setString(9, t.recurrenceId());
            ps.setObject(10, t.contractId(), Types.OTHER);
            ps.setString(11, t.origin().name());
        });
    }
}
