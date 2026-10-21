package de.financetool.llm.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Thin client for Ollama's native API: {@code /api/chat}, {@code /api/embed}, {@code /api/tags} (AD-5). */
class OllamaClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(Message message, @JsonProperty("prompt_eval_count") Integer promptEvalCount,
            @JsonProperty("done_reason") String doneReason) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Message(String content) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbedResponse(List<float[]> embeddings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TagsResponse(List<Tag> models) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Tag(String name, String model, String digest) {
        }
    }

    private final RestClient rest;

    OllamaClient(RestClient rest) {
        this.rest = rest;
    }

    ChatResponse chat(Map<String, Object> body) {
        return rest.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(ChatResponse.class);
    }

    EmbedResponse embed(String model, List<String> texts) {
        // truncate=false: an over-long text must fail loudly instead of being cut silently.
        return rest.post().uri("/api/embed").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", model, "input", texts, "truncate", false))
                .retrieve().body(EmbedResponse.class);
    }

    TagsResponse tags() {
        return rest.get().uri("/api/tags").retrieve().body(TagsResponse.class);
    }
}
