package de.financetool.shared.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Computes the {@link ConfigFingerprint} over the AD-14 file set: the active agent configuration, prompts,
 * {@code autonomy.yaml}, {@code profile-minimums.yaml}, {@code models/lock.json}, {@code config/documents.yaml},
 * {@code config/letters/} and {@code data/rules/}. Bytes are LF-normalised; files are hashed in sorted path order,
 * each as path, NUL, content, NUL, so that renames change the fingerprint too. Files not present are skipped.
 */
public final class ConfigFingerprints {

    private ConfigFingerprints() {
    }

    /** The fingerprinted files and directories, relative to {@code APP_HOME}. */
    static List<String> fileSet(String agentConfigId) {
        return List.of(
                "config/agent/" + agentConfigId + ".yaml",
                "config/prompts",
                "config/autonomy.yaml",
                "config/profile-minimums.yaml",
                "models/lock.json",
                "config/documents.yaml",
                "config/letters",
                "data/rules");
    }

    public static ConfigFingerprint compute(Path appHome, String agentConfigId) {
        SortedMap<String, byte[]> contents = new TreeMap<>();
        for (String entry : fileSet(agentConfigId)) {
            Path path = appHome.resolve(entry);
            for (Path file : filesBelow(path)) {
                contents.put(appHome.relativize(file).toString().replace('\\', '/'), normalisedBytes(file));
            }
        }
        MessageDigest total = sha256();
        SortedMap<String, String> fileHashes = new TreeMap<>();
        contents.forEach((relative, bytes) -> {
            fileHashes.put(relative, HexFormat.of().formatHex(sha256().digest(bytes)));
            total.update(relative.getBytes(StandardCharsets.UTF_8));
            total.update((byte) 0);
            total.update(bytes);
            total.update((byte) 0);
        });
        return new ConfigFingerprint(HexFormat.of().formatHex(total.digest()), fileHashes);
    }

    private static List<Path> filesBelow(Path path) {
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }
        if (!Files.isDirectory(path)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(path)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> !file.getFileName().toString().equals(".gitkeep"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Raw bytes with CRLF replaced by LF, so Windows checkouts hash like the image. */
    static byte[] normalisedBytes(Path file) {
        try {
            byte[] raw = Files.readAllBytes(file);
            byte[] out = new byte[raw.length];
            int n = 0;
            for (int i = 0; i < raw.length; i++) {
                if (raw[i] == '\r' && i + 1 < raw.length && raw[i + 1] == '\n') {
                    continue;
                }
                out[n++] = raw[i];
            }
            return java.util.Arrays.copyOf(out, n);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
