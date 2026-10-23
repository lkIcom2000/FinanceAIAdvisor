package de.financetool.llm;

/**
 * The model bound to a role: its tag and the pinned digest from {@code models/lock.json} (empty if not pinned).
 */
public record LlmModel(String tag, String digest) {
}
