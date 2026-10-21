package archfixture.rules.application;

import de.financetool.bank.adapter.BankJpaRepository;

/** Violates the bank write rule: deletes bank rows without going through BankPort or persona reset. */
public class BankWritingService {

    public void wipe(BankJpaRepository bank) {
        bank.deletePersonaRows("p-lena");
    }
}
