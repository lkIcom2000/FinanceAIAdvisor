package de.financetool.core.domain;

import de.financetool.shared.domain.PersonaId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/**
 * The figure key ({@code provId}, AD-2): the first 16 lower-case hex characters of SHA-1 over the UTF-8 text of
 * persona id, function, canonical inputs, {@code asOf}, configuration fingerprint and output key, joined by newlines.
 * Identical calls give identical keys; any changed component gives a different one, so the components of a released
 * figure are frozen: stored references would no longer resolve (Epic 2 retro A6, pinned by {@code FigureKeyFreezeTest}).
 */
public final class FigureKey {

    private FigureKey() {
    }

    public static String of(PersonaId personaId, String function, Map<String, ?> inputs, LocalDate asOf,
            String configFingerprint, String outputKey) {
        Objects.requireNonNull(personaId, "personaId");
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(configFingerprint, "configFingerprint");
        Objects.requireNonNull(outputKey, "outputKey");
        String text = String.join("\n", personaId.value(), function, CanonicalForm.of(inputs), asOf.toString(),
                configFingerprint, outputKey);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by every Java platform", e);
        }
    }
}
