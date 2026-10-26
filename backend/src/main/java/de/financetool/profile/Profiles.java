package de.financetool.profile;

import de.financetool.shared.PersonaScope;
import java.time.Instant;
import java.util.Map;

/**
 * Module API: the persona's stored Suitability and Needs Profiles (AD-12). {@link #confirm} is the confirm control,
 * the only path that writes profile values; facts stated in a chat message are never passed to it (AD-23).
 */
public interface Profiles {

    /** The confirmed fields of both profiles, field → value; empty if none are stored; in the caller's transaction. */
    Map<String, Object> stored(PersonaScope scope);

    /**
     * Stores the confirmed answers, replacing earlier values of the same fields; runs in the caller's transaction.
     * Values of fields the assessment card asks for are stored in {@link AssessmentFields#normalize} form.
     *
     * @throws IllegalArgumentException for a field not in {@link ProfileFields}, an empty value or a value that does
     *     not fit its card field (the message is a plain sentence); nothing is stored
     */
    void confirm(PersonaScope scope, Map<String, Object> answers, Instant confirmedAt);
}
