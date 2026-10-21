package archfixture.rules.application;

import org.springframework.transaction.annotation.Transactional;

/** Allowed (AD-21): a use case starts the transaction. */
public class FixtureUseCase {

    @Transactional
    public void run() {
    }
}
