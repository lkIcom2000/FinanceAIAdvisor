package de.financetool.bank.application;

import de.financetool.bank.adapter.BankJpaRepository;
import de.financetool.bank.adapter.BankRowWriter;
import de.financetool.shared.PersonaResettable;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.fixtures.PersonaFixtureCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restores the persona's bank rows to its fixture: deletes every row of the persona in the five bank tables, then
 * inserts the fixture rows. Used by startup seeding and persona reset alike (one path).
 */
@Service
class BankFixtureResetter implements PersonaResettable {

    private final BankJpaRepository bank;
    private final BankRowWriter writer;
    private final PersonaFixtureCatalog fixtures;

    BankFixtureResetter(BankJpaRepository bank, BankRowWriter writer, PersonaFixtureCatalog fixtures) {
        this.bank = bank;
        this.writer = writer;
        this.fixtures = fixtures;
    }

    @Override
    public String module() {
        return "bank";
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reset(PersonaId personaId) {
        bank.deletePersonaRows(personaId.value());
        writer.insertFixture(fixtures.fixture(personaId));
    }
}
