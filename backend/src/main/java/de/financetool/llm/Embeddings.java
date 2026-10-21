package de.financetool.llm;

import java.util.List;

/** Embedding vectors, one per input text, with the model that produced them. */
public record Embeddings(List<float[]> vectors, String modelTag, String digest) {
}
