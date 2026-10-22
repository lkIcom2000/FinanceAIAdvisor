package de.financetool.audit.application;

import de.financetool.shared.RequestContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The only serialiser of audit payloads and export lines (AD-13). Its own mapper, independent of Spring's and
 * Hibernate's, so the format is fixed: properties and map keys sorted, dates as ISO strings, nulls written.
 */
@Component
public class AuditPayloadMapper {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            // Records too: otherwise their components keep declaration order.
            .disable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            // Decimals read back keep their scale (12.50 stays 12.50 in the export).
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    /** The record payload: what the use case ran with, plus the kind-specific body. */
    public String payload(RequestContext context, Object body) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("id", context.agentConfig().id());
        config.put("fingerprint", context.configFingerprint().value());
        config.put("files", context.configFingerprint().fileHashes());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("config", config);
        payload.put("kbSnapshot", context.kbSnapshot());
        payload.put("seeds", context.seeds());
        payload.put("asOf", context.asOf());
        payload.put("body", body);
        return toJson(payload);
    }

    public String toJson(Object value) {
        return mapper.writeValueAsString(value);
    }

    /** Reads a stored JSON object back, for re-serialisation in the fixed format. */
    public Map<String, Object> fromJson(String json) {
        return mapper.readValue(json, MAP);
    }

    /** Reads stored JSON back into a type, e.g. an evidence item's {@code Provenance}. */
    public <T> T fromJson(String json, Class<T> type) {
        return mapper.readValue(json, type);
    }
}
