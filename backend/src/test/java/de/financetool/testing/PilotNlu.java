package de.financetool.testing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * The NLU extractions recorded in the E1 pilot run ({@code experiments/results/Pilot_E1/e1-llama3/items.jsonl}, model
 * {@code llama3.1:8b-instruct-q4_K_M}) for the 65 Q&A items: stub answers of the NLU role (Stories 3.3–3.8).
 */
public final class PilotNlu {

    public static final String RESOURCE = "/nlu/e1-llama3-pilot.jsonl";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<Item> ITEMS = load(RESOURCE);

    /**
     * @param nlu the recorded extraction as the model's JSON answer
     */
    public record Item(String itemId, String question, String nlu) {
    }

    private PilotNlu() {
    }

    public static List<Item> items() {
        return ITEMS;
    }

    /** The recorded answer for this exact question, or {@code null}. */
    public static String answerFor(String question) {
        return ITEMS.stream().filter(i -> i.question().equals(question)).map(Item::nlu).findFirst().orElse(null);
    }

    /** The JSON lines of a test resource, without blank lines and {@code #} comments. */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> jsonLines(String resource) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (InputStream in = PilotNlu.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + resource);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    rows.add(JSON.readValue(line, Map.class));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }

    private static List<Item> load(String resource) {
        return jsonLines(resource).stream().map(row -> new Item(String.valueOf(row.get("item_id")),
                (String) row.get("question"), JSON.writeValueAsString(row.get("nlu")))).toList();
    }
}
