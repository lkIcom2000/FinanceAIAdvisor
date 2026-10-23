package de.financetool.advisory.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * With retrieval off the phrasing prompt holds no SOURCES, yet the model sometimes cites "[S1]" anyway (seen live).
 * Such markers point at nothing, so they are removed from the draft before the checks and the release; the first
 * draft in the Audit Record keeps them. A deviation from the Experiment Track, which releases them unchanged.
 */
public final class CitationMarkers {

    static final Pattern MARKER = Pattern.compile("\\s?\\[S\\d+\\]", Pattern.UNICODE_CHARACTER_CLASS);

    /** The text without its citation markers and how many were removed. */
    public record Stripped(String text, int removed) {
    }

    private CitationMarkers() {
    }

    public static Stripped strip(String text) {
        Matcher m = MARKER.matcher(text);
        int removed = 0;
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            removed++;
            m.appendReplacement(out, "");
        }
        m.appendTail(out);
        return new Stripped(out.toString(), removed);
    }
}
