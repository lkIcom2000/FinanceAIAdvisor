package de.financetool.advisory.adapter;

import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The NLU cache {@code nlu_cache} (AD-5): the normalised extraction per sha256 key over persona, model tag, model
 * digest, decoding, schema, NLU prompt hash and message. An extraction can hold stated profile facts, so the cache is
 * persona state: every read and write takes a {@link PersonaScope}, and persona reset deletes the persona's rows
 * (AD-23).
 */
@Repository
public class NluCacheRepository {

    /** A cached extraction: its JSON and whether the model's answer was parseable. */
    public record Entry(String extraction, boolean parseOk) {
    }

    private final JdbcClient jdbc;

    NluCacheRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Entry> find(PersonaScope scope, String key) {
        return jdbc.sql("SELECT extraction, parse_ok FROM nlu_cache WHERE cache_key = ? AND persona_id = ?")
                .params(key, scope.id())
                .query((rs, row) -> new Entry(rs.getString("extraction"), rs.getBoolean("parse_ok")))
                .optional();
    }

    /** Stores the entry unless the key is cached already (a concurrent turn with the same message). */
    public void put(PersonaScope scope, String key, Entry entry) {
        jdbc.sql("INSERT INTO nlu_cache (cache_key, persona_id, extraction, parse_ok) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (cache_key) DO NOTHING")
                .params(key, scope.id(), entry.extraction(), entry.parseOk())
                .update();
    }

    /** Persona reset: deletes the persona's cached extractions; returns the deleted rows. */
    public int deletePersonaRows(PersonaId personaId) {
        return jdbc.sql("DELETE FROM nlu_cache WHERE persona_id = ?").param(personaId.value()).update();
    }
}
