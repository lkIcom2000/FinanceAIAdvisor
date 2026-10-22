package de.financetool.core.application;

import de.financetool.audit.EvidenceItem;
import de.financetool.audit.EvidenceOrigin;
import de.financetool.audit.EvidenceStore;
import de.financetool.audit.Provenance;
import de.financetool.bank.AccountType;
import de.financetool.bank.BankReadPort;
import de.financetool.bank.BankSnapshot;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.AccountBalance;
import de.financetool.core.domain.CoreComputation;
import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.Figure;
import de.financetool.core.domain.FigureKey;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.SourcedNumber;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reads the snapshot, runs the pure function, keys its outputs and stores their provenance (AD-2, AD-21). */
@Service
class CoreGatewayService implements CoreGateway {

    private final BankReadPort bank;
    private final EvidenceStore evidence;

    CoreGatewayService(BankReadPort bank, EvidenceStore evidence) {
        this.bank = bank;
        this.evidence = evidence;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CoreResult currentAccountBalance(RequestContext context, EvidenceOrigin origin) {
        BankSnapshot snapshot = bank.snapshot(context.personaId(), context.asOf());
        BankSnapshot.Account account = snapshot.accounts().stream()
                .filter(a -> a.type() == AccountType.CURRENT)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No current account for " + context.personaId()));
        List<AccountBalance.Booking> bookings = snapshot.transactions().stream()
                .filter(t -> t.accountId().equals(account.id()))
                .map(t -> new AccountBalance.Booking(t.bookingDate(), t.amount()))
                .toList();
        return record(context, origin,
                AccountBalance.of(account.openingDate(), account.openingBalance(), bookings, context.asOf()));
    }

    /** Keys every output, stores one evidence item per output and returns the keyed result. */
    private CoreResult record(RequestContext context, EvidenceOrigin origin, CoreComputation computation) {
        Objects.requireNonNull(origin, "origin");
        String fingerprint = context.configFingerprint().value();
        Provenance.Source source = new Provenance.Source(Provenance.SourceKind.CORE_FUNCTION,
                "Calculated by Fine's calculation engine (" + computation.plainName()
                        + "), not by the language model. " + computation.basis());
        List<Provenance.Input> inputs = computation.inputs().stream()
                .map(i -> new Provenance.Input(i.label(), i.value(), i.unit(), i.text(), i.provId()))
                .toList();
        List<Provenance.RuleParameter> ruleParameters = computation.ruleParamVersions().entrySet().stream()
                .map(e -> new Provenance.RuleParameter(e.getKey(), e.getValue(), null, null))
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();

        List<SourcedNumber> numbers = new ArrayList<>();
        List<EvidenceItem> items = new ArrayList<>();
        for (Figure output : computation.outputs()) {
            String provId = FigureKey.of(context.personaId(), computation.function(), computation.keyInputs(),
                    context.asOf(), fingerprint, output.outputKey());
            numbers.add(new SourcedNumber(provId, output.value(), output.unit(), output.label()));
            Provenance provenance = new Provenance(provId, computation.function(), source, output.label(),
                    output.value(), output.unit(), context.asOf(), inputs, computation.assumptions(),
                    ruleParameters, List.of());
            items.add(new EvidenceItem(provId, context.personaId(), origin, null, provenance));
        }
        evidence.putIfAbsent(items);
        return new CoreResult(computation.function(), computation.inputs(), computation.outputs(),
                computation.assumptions(), computation.ruleParamVersions(), numbers);
    }
}
