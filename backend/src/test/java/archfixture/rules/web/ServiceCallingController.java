package archfixture.rules.web;

import archfixture.rules.application.FixtureUseCase;
import archfixture.rules.application.TransactionalService;

/** Deliberately broken (AD-21): a controller that calls a module service instead of a use case. */
public class ServiceCallingController {

    private final TransactionalService service = new TransactionalService();
    private final FixtureUseCase useCase = new FixtureUseCase();

    public void handle() {
        service.startsItsOwnTransaction();
        useCase.run();
    }
}
