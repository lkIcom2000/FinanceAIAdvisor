package de.financetool.llm;

import java.util.Objects;

/** One chat message. */
public record LlmMessage(Author author, String content) {

    public enum Author {
        SYSTEM, USER, ASSISTANT
    }

    public LlmMessage {
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(content, "content");
    }

    public static LlmMessage system(String content) {
        return new LlmMessage(Author.SYSTEM, content);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage(Author.USER, content);
    }
}
