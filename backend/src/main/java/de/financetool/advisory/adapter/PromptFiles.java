package de.financetool.advisory.adapter;

import de.financetool.shared.AppHome;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * The versioned prompt files {@code config/prompts/*.txt} (AD-14), read once at startup. Text and hash use LF line
 * endings, so a checkout with CRLF gives the same hash.
 */
@Component
public class PromptFiles {

    /** A prompt's text and the sha256 (hex) of its UTF-8 bytes with LF line endings. */
    public record Prompt(String name, String text, String hash) {
    }

    private final Map<String, Prompt> prompts = new TreeMap<>();

    PromptFiles(AppHome appHome) {
        Path directory = appHome.config().resolve("prompts");
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".txt")).toList()) {
                String fileName = file.getFileName().toString();
                String name = fileName.substring(0, fileName.length() - ".txt".length());
                String text = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
                prompts.put(name, new Prompt(name, text, sha256(text)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the prompt files in " + directory, e);
        }
    }

    /** The role prompt {@code role-<rolePrompt>.txt}. */
    public Prompt rolePrompt(String rolePrompt) {
        Prompt prompt = prompts.get("role-" + rolePrompt);
        if (prompt == null) {
            throw new IllegalStateException("No prompt file config/prompts/role-" + rolePrompt + ".txt");
        }
        return prompt;
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
