package de.financetool.shared.config;

import java.util.Collections;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * SHA-256 fingerprint over every behaviour-relevant configuration file (AD-14), plus the per-file hashes keyed by
 * path relative to {@code APP_HOME}. Recorded in every Audit Record.
 */
public record ConfigFingerprint(String value, SortedMap<String, String> fileHashes) {

    public ConfigFingerprint {
        Objects.requireNonNull(value, "value");
        fileHashes = Collections.unmodifiableSortedMap(new TreeMap<>(fileHashes));
    }
}
