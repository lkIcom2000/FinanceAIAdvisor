package archfixture.rules.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/** Deliberately broken (AD-21): a module service that starts transactions, is class-level transactional and async. */
@Transactional(propagation = Propagation.MANDATORY)
public class TransactionalService {

    @Transactional
    public void startsItsOwnTransaction() {
    }

    @Async
    public void runsElsewhere() {
    }

    @TransactionalEventListener
    public void afterCommit(Object event) {
    }
}
