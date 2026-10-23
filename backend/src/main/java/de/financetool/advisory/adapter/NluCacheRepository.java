package de.financetool.advisory.adapter;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The NLU cache {@code nlu_cache} (AD-5): the normalised extraction per sha256 key over model tag, model digest,
 * NLU prompt hash and message. Not persona state: it is never reset.
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

    public Optional<Entry> find(String key) {
        return jdbc.sql("SELECT extraction, parse_ok FROM nlu_cache WHERE cache_key = ?")
                .param(key)
                .query((rs, row) -> new Entry(rs.getString("extraction"), rs.getBoolean("parse_ok")))
                .optional();
    }

    /** Stores the entry unless the key is cached already (a concurrent turn with the same message). */
    public void put(String key, Entry entry) {
        jdbc.sql("INSERT INTO nlu_cache (cache_key, extraction, parse_ok) VALUES (?, ?, ?) "
                        + "ON CONFLICT (cache_key) DO NOTHING")
                .params(key, entry.extraction(), entry.parseOk())
                .update();
    }
}
