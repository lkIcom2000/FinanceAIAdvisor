package de.financetool.llm.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code models/lock.json} (AD-18): per model tag the {@code /api/tags} digest and the GGUF sha256 of its weights.
 */
record ModelLock(List<Entry> models) {

    record Entry(String tag, String digest, String ggufSha256) {
    }

    static final String FILE = "lock.json";

    ModelLock {
        models = List.copyOf(models);
    }

    /** The lock below {@code models/}, or empty if the file does not exist yet. */
    static Optional<ModelLock> read(Path modelsDirectory) {
        Path file = modelsDirectory.resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(JsonMapper.builder().build().readValue(file.toFile(), ModelLock.class));
    }

    Optional<Entry> find(String tag) {
        return Optional.ofNullable(byTag().get(tag));
    }

    private Map<String, Entry> byTag() {
        return models.stream().collect(Collectors.toMap(Entry::tag, Function.identity()));
    }
}
