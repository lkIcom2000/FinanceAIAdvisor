package de.financetool.advisory.adapter;

import de.financetool.advisory.domain.ClassInstruction;
import de.financetool.advisory.domain.MessageClass;
import de.financetool.shared.AppHome;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The versioned prompt files {@code config/prompts/*.txt} and {@code *.json} (AD-14), read once at startup. Text and
 * hash use LF line endings, so a checkout with CRLF gives the same hash.
 */
@Component
public class PromptFiles {

    /**
     * A prompt's text and the sha256 (hex) of its UTF-8 bytes with LF line endings.
     *
     * @param fileName the file name in {@code config/prompts/}, the key of the Audit Record's prompt hashes
     */
    public record Prompt(String fileName, String text, String hash) {
    }

    /** One NLU few-shot example: a message and the extraction the model should answer (a JSON value). */
    public record FewShot(String message, Object extraction) {
    }

    /** The compliance rules every compliance level gives to the phrasing role. */
    public static final String COMPLIANCE_RULES = "compliance-rules.txt";
    /** The NLU role's system prompt ({@code SYSTEM} of {@code experiments/finlab/nlu.py}). */
    public static final String NLU_SYSTEM = "nlu-system.txt";
    /** The NLU role's few-shot examples: a JSON list of [message, extraction] pairs ({@code FEW_SHOT}). */
    public static final String NLU_FEW_SHOT = "nlu-few-shot.json";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Map<String, Prompt> prompts = new TreeMap<>();
    private final List<FewShot> fewShots;

    PromptFiles(AppHome appHome) {
        Path directory = appHome.config().resolve("prompts");
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(PromptFiles::isPromptFile).toList()) {
                String fileName = file.getFileName().toString();
                String text = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
                prompts.put(fileName, new Prompt(fileName, text, sha256(text)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the prompt files in " + directory, e);
        }
        // Fail fast: every file a turn needs exists, and the few-shot file parses.
        named(NLU_SYSTEM);
        for (MessageClass messageClass : MessageClass.values()) {
            classInstruction(messageClass);
        }
        this.fewShots = parseFewShots(named(NLU_FEW_SHOT).text());
    }

    /** The few-shot file as [message, extraction] pairs; anything else names the file and fails. */
    public static List<FewShot> parseFewShots(String text) {
        String file = "config/prompts/" + NLU_FEW_SHOT;
        Object root;
        try {
            root = JSON.readValue(text, Object.class);
        } catch (JacksonException e) {
            throw new IllegalStateException(file + " is not valid JSON", e);
        }
        if (!(root instanceof List<?> pairs)) {
            throw new IllegalStateException(file + " must be a JSON list of [message, extraction] pairs");
        }
        List<FewShot> out = new ArrayList<>();
        for (Object pair : pairs) {
            if (!(pair instanceof List<?> p) || p.size() != 2 || !(p.get(0) instanceof String message)
                    || !(p.get(1) instanceof Map<?, ?>)) {
                throw new IllegalStateException(file + ": every example must be a [message, extraction] pair");
            }
            out.add(new FewShot(message, p.get(1)));
        }
        return List.copyOf(out);
    }

    private static boolean isPromptFile(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".txt") || name.endsWith(".json");
    }

    /** The role prompt {@code role-<rolePrompt>.txt}. */
    public Prompt rolePrompt(String rolePrompt) {
        return named("role-" + rolePrompt + ".txt");
    }

    /** The compliance rules {@code compliance-rules.txt}. */
    public Prompt complianceRules() {
        return named(COMPLIANCE_RULES);
    }

    public Prompt nluSystem() {
        return named(NLU_SYSTEM);
    }

    public Prompt nluFewShot() {
        return named(NLU_FEW_SHOT);
    }

    /** The few-shot examples, parsed once at startup. */
    public List<FewShot> nluFewShots() {
        return fewShots;
    }

    /** The class instruction {@code class-<CLASS>.txt}. */
    public Prompt classInstruction(MessageClass messageClass) {
        return named(ClassInstruction.fileName(messageClass) + ".txt");
    }

    private Prompt named(String fileName) {
        Prompt prompt = prompts.get(fileName);
        if (prompt == null) {
            throw new IllegalStateException("No prompt file config/prompts/" + fileName);
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
