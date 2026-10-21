package de.financetool.bank.adapter;

import de.financetool.bank.TransactionOrigin;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Reads and deletes of the five bank tables (JPA). Inserts go through {@link BankRowWriter}. Reads filter by
 * {@code asOf}; writes are called only from {@code bank.application} services implementing {@code BankPort} or
 * {@code PersonaResettable} (ArchUnit).
 */
@Repository
public class BankJpaRepository {

    @PersistenceContext
    private EntityManager em;

    public List<AccountEntity> accounts(String personaId, LocalDate asOf) {
        return em.createQuery("select a from AccountEntity a where a.personaId = :personaId "
                + "and a.openingDate <= :asOf order by a.id", AccountEntity.class)
                .setParameter("personaId", personaId).setParameter("asOf", asOf).getResultList();
    }

    public Optional<AccountEntity> account(String personaId, UUID accountId) {
        return em.createQuery("select a from AccountEntity a where a.personaId = :personaId and a.id = :id",
                AccountEntity.class)
                .setParameter("personaId", personaId).setParameter("id", accountId)
                .getResultStream().findFirst();
    }

    public List<ContractEntity> contracts(String personaId, LocalDate asOf) {
        return em.createQuery("select c from ContractEntity c where c.personaId = :personaId "
                + "and c.startDate <= :asOf order by c.id", ContractEntity.class)
                .setParameter("personaId", personaId).setParameter("asOf", asOf).getResultList();
    }

    public List<TransactionEntity> transactions(String personaId, LocalDate asOf) {
        return em.createQuery("select t from TransactionEntity t where t.personaId = :personaId "
                + "and t.bookingDate <= :asOf order by t.bookingDate, t.id", TransactionEntity.class)
                .setParameter("personaId", personaId).setParameter("asOf", asOf).getResultList();
    }

    public List<HoldingEntity> holdings(String personaId, LocalDate asOf) {
        return em.createQuery("select h from HoldingEntity h where h.personaId = :personaId "
                + "and h.valuationDate <= :asOf order by h.id", HoldingEntity.class)
                .setParameter("personaId", personaId).setParameter("asOf", asOf).getResultList();
    }

    public List<SavingsPlanEntity> savingsPlans(String personaId, LocalDate asOf) {
        return em.createQuery("select s from SavingsPlanEntity s where s.personaId = :personaId "
                + "and s.startDate <= :asOf order by s.id", SavingsPlanEntity.class)
                .setParameter("personaId", personaId).setParameter("asOf", asOf).getResultList();
    }

    public long countTransactions(String personaId, TransactionOrigin origin) {
        return em.createQuery("select count(t) from TransactionEntity t where t.personaId = :personaId "
                + "and t.origin = :origin", Long.class)
                .setParameter("personaId", personaId).setParameter("origin", origin).getSingleResult();
    }

    /** Deletes every bank row of the persona, children first; returns the number of deleted rows. */
    public int deletePersonaRows(String personaId) {
        em.flush();
        int deleted = 0;
        for (String entity : List.of("TransactionEntity", "SavingsPlanEntity", "HoldingEntity", "ContractEntity",
                "AccountEntity")) {
            deleted += em.createQuery("delete from " + entity + " e where e.personaId = :personaId")
                    .setParameter("personaId", personaId).executeUpdate();
        }
        em.clear();
        return deleted;
    }
}
