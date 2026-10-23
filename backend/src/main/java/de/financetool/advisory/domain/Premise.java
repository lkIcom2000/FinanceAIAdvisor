package de.financetool.advisory.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A fact a question takes for granted, as the NLU proposes it ({@code asserted_premises} of
 * {@code experiments/finlab/nlu.py}) or as the classifier adds it (a described law, a guarantee claim).
 *
 * @param kind one of the NLU's premise kinds, or {@code described_law} for a law described instead of named
 * @param description the description of a described law, {@code null} otherwise
 */
public record Premise(String claim, String kind, String namedEntity, String description) {

    public static final String DESCRIBED_LAW = "described_law";
    public static final String GUARANTEE = "guarantee_or_compensation";

    public Premise {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(namedEntity, "namedEntity");
    }

    public Premise(String claim, String kind, String namedEntity) {
        this(claim, kind, namedEntity, null);
    }

    /** The premise with the experiment's keys, for the Audit Record. */
    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("claim", claim);
        out.put("kind", kind);
        out.put("named_entity", namedEntity);
        if (description != null) {
            out.put("description", description);
        }
        return out;
    }
}
