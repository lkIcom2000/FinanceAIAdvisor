package de.financetool.audit.application;

import de.financetool.audit.AuditEntry;
import de.financetool.audit.AuditExport;
import de.financetool.audit.AuditWriter;
import de.financetool.audit.EvidenceItem;
import de.financetool.audit.EvidenceReader;
import de.financetool.audit.EvidenceStore;
import de.financetool.audit.Provenance;
import de.financetool.audit.adapter.AuditRecordRepository;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.SourcedNumber;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes and exports Audit Records and evidence items, always inside the caller's transaction (AD-7, AD-16). */
@Service
class AuditService implements AuditWriter, EvidenceStore, EvidenceReader, AuditExport {

    private final AuditRecordRepository records;
    private final AuditPayloadMapper mapper;

    AuditService(AuditRecordRepository records, AuditPayloadMapper mapper) {
        this.records = records;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void write(RequestContext context, AuditEntry entry) {
        records.insert(entry.kind().name(), context.runId(), context.personaId().value(), entry.turnId(),
                entry.actionId(), entry.fixtureVersion(), SCHEMA_VERSION, mapper.payload(context, entry.body()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void putIfAbsent(List<EvidenceItem> items) {
        for (EvidenceItem item : items) {
            records.insertEvidenceIfAbsent(item.provId(), item.personaId().value(),
                    item.origin().name().toLowerCase(Locale.ROOT), item.originId(), mapper.toJson(item.content()));
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<SourcedNumber> figures(PersonaScope scope, List<String> provIds) {
        Map<String, String> stored = records.findEvidence(provIds, scope);
        return provIds.stream().filter(stored::containsKey)
                .map(id -> mapper.fromJson(stored.get(id), Provenance.class))
                .map(p -> new SourcedNumber(p.provId(), p.value(), p.unit(), p.label()))
                .toList();
    }

    /** The provenance stored under {@code provId}, if it belongs to the scope's persona. */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<Provenance> provenance(PersonaScope scope, String provId) {
        return records.findEvidence(provId, scope).map(json -> mapper.fromJson(json, Provenance.class));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public void writeJsonl(String runId, OutputStream out) {
        records.forEach(runId, row -> {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("seq", row.seq());
            line.put("id", row.id());
            line.put("kind", row.kind());
            line.put("runId", row.runId());
            line.put("personaId", row.personaId());
            line.put("turnId", row.turnId());
            line.put("actionId", row.actionId());
            line.put("fixtureVersion", row.fixtureVersion());
            line.put("schemaVersion", row.schemaVersion());
            line.put("createdAt", row.createdAt().toInstant());
            line.put("payload", mapper.fromJson(row.payload()));
            try {
                out.write((mapper.toJson(line) + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
